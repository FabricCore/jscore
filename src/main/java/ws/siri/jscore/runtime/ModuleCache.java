package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * you MUST NOT access cache, or modify the tree outside of useCache in any way, including dependencies
     *
     * with the exception for calling unload on a module already remove from the cache, that is safe
     * because it modifying its dependencies is not visible in any way, and cascading unloads calls useCache again which is safe
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

    /**
     * centralised place for creating new (blank modules)
     *
     * requestedBy: if its empty means its explicitly loaded, if is some then that's
     * the module
     *
     * returns an existing module if it is already in cache, but if preludes doesn't match
     * the preludes used to create the cached version, it will throw an error
     */
    private Module createModule(List<String> path, List<Prelude> filePreludes, String content,
            Optional<Module> requestedBy) {
        return useCache(cache -> {
            Module mod;
            if (cache.containsKey(path)) {
                // rechecks as there may be a time difference between it has last been checked
                // in get
                // this block of code must also be replicated in get
                mod = cache.get(path);
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

            return mod;
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
     * returns the module if removal is successful
     * noop and returns empty if the module is not loaded in the first place
     *
     * this should ONLY be used by unimportModule, as this also removes the dependency
     */
    private Optional<Module> removeModule(List<String> path, Optional<Module> requestedBy) {
        return useCache(cache -> {
            if (!cache.containsKey(path))
                return Optional.empty();

            Module module = cache.get(path);
            if (requestedBy.isPresent())
                module.removeDependent(requestedBy.get().getPath());
            else
                module.unsetExplicitlyLoaded();

            requestedBy.ifPresent(m -> m.removeDependency(path));

            if (module.shouldUnload()) {
                cache.remove(path);
                return Optional.of(module);
            } else
                return Optional.empty();
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
        Module module = createModule(path, filePreludes, content, requestedBy);

        return module.waitForExports();
    }

    /**
     * remove dependency of one module
     *
     * the values from the unimported module is undefined behaviour after an unimport
     */
    public void unimportModule(List<String> path, Optional<Module> requestedBy) {
        Optional<Module> removedModule = removeModule(path, requestedBy);
        removedModule.ifPresent(Module::unloadInternal);
    }

    public Repl spawnRepl(String fileExt, String[] preludeNames) {
        List<Prelude> filePreludes = getPreludes(preludeNames);
        List<String> replPath = List.of("sys", "repls", Repl.genReplName(fileExt));
        Module module = createModule(replPath, filePreludes, "", Optional.empty()); // explicitly loaded!
        return new Repl(module);
    }
}
