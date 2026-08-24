package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import net.fabricmc.loader.api.FabricLoader;
import ws.siri.jscore.JSCore;
import ws.siri.jscore.runtime.ClassMarkers.LangDef;
import ws.siri.jscore.runtime.ClassMarkers.LangSpecificModule;

public class ModuleCache {
    private static ModuleCache instance = new ModuleCache();

    // TODO: also reference counts this
    public class Prelude {
        private LangDef langDef;
        private BiConsumer<ProxyObject, LangSpecificModule> preludeFunction;

        public void apply(ProxyObject globalScope, Module module) {
            preludeFunction.accept(globalScope, langDef.wrapModule(module));
        }
    }

    private static class UnloadablesRes {
        /**
         * root is none when unloadables is empty
         */
        public Optional<Module> root;
        /**
         * includes root
         */
        public Set<List<String>> unloadables;

        public UnloadablesRes(Optional<Module> root, Set<List<String>> unloadables) {
            this.unloadables = unloadables;
            this.root = root;
        }

        public static UnloadablesRes empty() {
            return new UnloadablesRes(Optional.empty(), Set.of());
        }
    }

    private static class CreateModuleRes {
        private enum ResType {
            CREATED,
            WAIT_FOR_UNLOAD
        }

        public ResType resType;
        public Module created;
        public Runnable waitForUnload;

        private CreateModuleRes() {
        }

        public static CreateModuleRes created(Module created) {
            CreateModuleRes out = new CreateModuleRes();
            out.resType = ResType.CREATED;
            out.created = created;
            return out;
        }

        public static CreateModuleRes waitForUnload(Runnable waitForUnload) {
            CreateModuleRes out = new CreateModuleRes();
            out.resType = ResType.WAIT_FOR_UNLOAD;
            out.waitForUnload = waitForUnload;
            return out;
        }
    }

    /**
     * (module: Module, scope object: Record<string, any>): void
     */
    private Map<String, Prelude> preludes = new ConcurrentHashMap<>();

    private ModuleCache() {
    }

    /**
     * DO NOT REFERENCE THIS, you MUST access it through useCache
     *
     * cache: only one thread should be able to access it at a time
     *
     * you MUST use cache through the useCache function
     */
    private Map<List<String>, Module> __cache = new HashMap<>();

    public static ModuleCache getInstance() {
        return instance;
    }

    /**
     * you MUST NOT access cache, or modify the tree outside of useCache in any way,
     * including dependencies
     *
     * with the exception of when cascading unloading a subgraph that is already
     * disconnected from the main graph
     * you can modify the dependencies/dependents of those modules
     * this is safe because they are already marked as unloading and their dep
     * values will not be read until the unloading has
     * completed and they are removed from cache
     */
    private synchronized <T> T useCache(Function<Map<List<String>, Module>, T> consumer) {
        return consumer.apply(__cache);
    }

    private List<Prelude> getPreludes(String[] preludeNames) {
        return Arrays.stream(preludeNames)
                .map(prelude -> {
                    if (preludes.containsKey(prelude)) {
                        return preludes.get(prelude);
                    } else
                        throw new UnsupportedOperationException(String.format("could not find prelude %s", prelude));
                }).toList();
    }

    private Path getModulePath(List<String> path) {
        return FabricLoader.getInstance().getConfigDir().resolve(JSCore.MOD_ID, path.toArray(String[]::new));
    }

    /**
     * check if file for a particular module exists on disk
     */
    public boolean fileExistsFor(List<String> path) {
        return Files.exists(getModulePath(path));
    }

    private static UnloadablesRes unloadableModules(Map<List<String>, Module> cache, Module unloadRoot) {
        Set<List<String>> unloadables = new HashSet<>();
        /**
         * all modules in frontier have shouldUnload = true,
         * but we haven't look at their dependencies yet
         */
        Set<Module> frontier = new HashSet<>();

        if (!unloadRoot.shouldUnload())
            return UnloadablesRes.empty();

        frontier.add(unloadRoot);

        while (frontier.size() != 0) {
            Set<Module> newFrontier = new HashSet<>();

            frontier.forEach(unloadable -> {
                unloadables.add(unloadable.getPath());
                unloadable.getDependencies().forEach(depPath -> {
                    Module dep = cache.get(depPath);
                    if (dep.getDependents().stream().allMatch(dependent -> unloadables.contains(dependent))
                            && !dep.isExplicitlyLoaded())
                        newFrontier.add(dep);
                });
            });

            frontier = newFrontier;
        }

        return new UnloadablesRes(Optional.of(unloadRoot), unloadables);
    }

    /**
     * centralised place for creating new (blank modules)
     *
     * requestedBy: if its empty means its explicitly loaded, if is some then that's
     * the module
     *
     * # module not currently unloading
     * returns an existing module if it is already in cache, but if preludes doesn't
     * match the preludes used to create the cached version, it will throw an error
     *
     * # module currently unloading
     */
    private CreateModuleRes createModule(List<String> path, List<Prelude> filePreludes, String content,
            Optional<Module> requestedBy) {
        return useCache(cache -> {
            Module mod;
            if (cache.containsKey(path)) {
                // rechecks as there may be a time difference between it has last been checked
                // in get
                // this block of code must also be replicated in get
                mod = cache.get(path);

                // retry if module is currently being unloaded
                Optional<Runnable> waitForUnloadTask = mod.waitForUnloadTask();
                if (waitForUnloadTask.isPresent())
                    return CreateModuleRes.waitForUnload(waitForUnloadTask.get());

                if (!mod.preludeMatches(filePreludes))
                    throw new IllegalArgumentException("prelude list does not match previous calls");
            } else {
                mod = new Module(path, filePreludes, content, requestedBy.map(Module::getPath));
                cache.put(path, mod);
            }

            // even if the module content is faulty causing an initialisation fail
            // it is still considered as a dependency
            if (requestedBy.isPresent())
                mod.addDependent(requestedBy.get().getPath());

            requestedBy.ifPresent(m -> m.addDependency(path));

            return CreateModuleRes.created(mod);
        });
    }

    /**
     * centralised place for removing modules
     *
     * if requestedBy is empty, then set explicitlyLoaded to false, but it may still
     * not unload if there are other dependencies
     *
     * requestedBy: if its empty means its explicitly loaded, if is some then that's
     * the module
     *
     * returns empty is the module at that path doesn't need to be removed
     * Optional.of if it needs to be removed
     *
     * after this is called, all the modules that needs to be removed have been
     * correctly marked with unloading
     *
     * this should ONLY be used by unimportModule, as this also removes the
     * dependency
     */
    private Optional<Module> removeModule(List<String> path, Optional<Module> requestedBy) {
        return useCache(cache -> {
            if (!cache.containsKey(path))
                return Optional.empty();

            Module module = cache.get(path);

            if (module.needToWaitForUnload())
                return Optional.empty();

            if (requestedBy.isPresent())
                module.removeDependent(requestedBy.get().getPath());
            else
                module.unsetExplicitlyLoaded();

            requestedBy.ifPresent(m -> m.removeDependency(path));

            // calculate and mark the classes, do not modify the cache DAG
            // returns an empty set if module should not be removed
            UnloadablesRes toUnload = unloadableModules(cache, module);
            toUnload.unloadables.forEach(modulePath -> cache.get(modulePath).startUnloading());
            return toUnload.root;
        });
    }

    /**
     * preludes are applied in order, duplicates will not be removed
     * - if module is not cached and not on disk, throws an error
     * - if module.exports is undefined, returns empty()
     * - if module.exports is set, returns of()
     *
     * requestedBy: if its empty means its explicitly loaded, if is some then that's
     * the module
     *
     * if requsetedBy is empty, the path MUST be a path that is not in cache!
     */
    public Optional<Value> get(List<String> path, String[] preludeNames, Optional<Module> requestedBy)
            throws IOException {
        List<Prelude> filePreludes = getPreludes(preludeNames);

        Optional<Module> cacheHit = useCache(cache -> {
            if (cache.containsKey(path)) {
                // this block of code must also be replicated in createModule
                Module module = cache.get(path);

                if (module.needToWaitForUnload())
                    return Optional.empty();

                if (!module.preludeMatches(filePreludes))
                    throw new IllegalArgumentException("prelude list does not match previous calls");

                if (requestedBy.isPresent()) {
                    requestedBy.get().addDependency(path);
                    module.addDependent(requestedBy.get().getPath());
                } else
                    throw new IllegalArgumentException("pinning an already loaded file is not (yet) a thing");

                return Optional.of(module);
            } else
                return Optional.empty();
        });

        if (cacheHit.isPresent())
            return cacheHit.get().waitForExports();

        Path filePath = getModulePath(path);
        String content = Files.readString(filePath);

        Optional<Module> resolvedModule = Optional.empty();

        // if need to wait for unload, try again until success
        while (resolvedModule.isEmpty()) {
            CreateModuleRes res = createModule(path, filePreludes, content, requestedBy);
            switch (res.resType) {
                case CREATED:
                    resolvedModule = Optional.of(res.created);
                    break;
                case WAIT_FOR_UNLOAD:
                    res.waitForUnload.run();
                    break;
            }
        }

        return resolvedModule.get().waitForExports();
    }

    /**
     * remove dependency of one module
     *
     * the values from the unimported module is undefined behaviour after an
     * unimport
     */
    public void unimportModule(List<String> path, Optional<Module> requestedBy) {
        Optional<Module> unloadableRoot = removeModule(path, requestedBy);

        if (unloadableRoot.isEmpty())
            return;

        // every module in frontier is shouldUnload = true
        Set<Module> frontier = new HashSet<>();
        frontier.add(unloadableRoot.get());

        // walk the graph :D
        // NOTE: this is the only instance where the graph can be modified outside of
        // useCache
        // this is because the graph is disconnecte from the rest of the graph, and
        // their dependencies/dependents values will not accessed
        // by any other code while they are all waiting for unload to complete and
        // unblock
        while (frontier.size() != 0) {
            Set<Module> newFrontier = new HashSet<>();

            frontier.forEach(unloadable -> {
                useCache(cache -> {
                    unloadable.getDependencies().forEach(depPath -> {
                        Module dep = cache.get(depPath);
                        dep.removeDependent(unloadable.getPath());
                        if (dep.shouldUnload())
                            newFrontier.add(dep);
                    });
                    return null;
                });

                unloadable.doUnloadCleanup();

                useCache(cache -> {
                    cache.remove(unloadable.getPath());
                    unloadable.completeUnloading();
                    return null;
                });
            });

            frontier = newFrontier;
        }
    }

    public Repl spawnRepl(String fileExt, String[] preludeNames) {
        List<Prelude> filePreludes = getPreludes(preludeNames);
        List<String> replPath = List.of("sys", "repls", Repl.genReplName(fileExt));

        CreateModuleRes createRes = createModule(replPath, filePreludes, "", Optional.empty()); // explicitly
        if (createRes.resType != CreateModuleRes.ResType.CREATED)
            throw new RuntimeException(
                    "how lucky must you be to hit this branch?! its an astronomically small chance!");

        return new Repl(createRes.created);
    }
}
