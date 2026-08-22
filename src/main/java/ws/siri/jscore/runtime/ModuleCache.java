package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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

    private ConcurrentHashMap<List<String>, Module> cache = new ConcurrentHashMap<>();

    public static ModuleCache getInstance() {
        return instance;
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
     * returns an existing module if it is already in cache, ignoring all other
     * inputs
     */
    private synchronized Module createModule(List<String> path, List<Prelude> filePreludes, String content,
            Optional<List<String>> requestedBy) {
        Module module;
        if (cache.containsKey(path)) {
            // rechecks as there may be a time difference between it has last been checked
            // in get
            // this block of code must also be replicated in get
            module = cache.get(path);
            if (!module.preludeMatches(filePreludes))
                throw new IllegalArgumentException("prelude list does not match previous calls");
        } else {
            module = new Module(path, filePreludes, content, requestedBy);
            cache.put(path, module);
        }

        // even if the module content is faulty causing an initialisation fail
        // it is still considered as a dependency
        if (requestedBy.isPresent())
            module.addDependent(requestedBy.get());

        return module;
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
     */
    private synchronized Optional<Module> removeModule(List<String> path, Optional<List<String>> requestedBy) {
        if (!cache.containsKey(path))
            return Optional.empty();

        Module module = cache.get(path);
        if (requestedBy.isPresent())
            module.removeDependent(requestedBy.get());
        else
            module.unsetExplicitlyLoaded();

        if (module.shouldUnload()) {
            cache.remove(path);
            return Optional.of(module);
        } else
            return Optional.empty();
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
     *
     * note: circular import is UNDEFINED BEHAVIOUR
     * until I find an O(1) way to detect it, it will fail silently and be badly
     * behaved!
     */
    public Optional<Value> get(List<String> path, String[] preludeNames, Optional<Module> requestedBy)
            throws IOException {
        List<Prelude> filePreludes = getPreludes(preludeNames);

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

            return module.getExports();
        }

        Path filePath = getModulePath(path);
        String content = Files.readString(filePath);
        Module module = createModule(path, filePreludes, content, requestedBy.map(Module::getPath));

        requestedBy.ifPresent(m -> m.addDependency(path));

        return module.getExports();
    }

    public void unimportModule(List<String> path, Optional<Module> requestedBy) {
        Optional<Module> removedModule = removeModule(path, requestedBy.map(Module::getPath));
        requestedBy.ifPresent(module -> module.removeDependency(path));
        removedModule.ifPresent(Module::unloadInternal);
    }

    public Repl spawnRepl(String fileExt, String[] preludeNames) {
        List<Prelude> filePreludes = getPreludes(preludeNames);
        List<String> replPath = List.of("sys", "repls", Repl.genReplName(fileExt));
        Module module = createModule(replPath, filePreludes, "", Optional.empty()); // explicitly loaded!
        return new Repl(module);
    }
}
