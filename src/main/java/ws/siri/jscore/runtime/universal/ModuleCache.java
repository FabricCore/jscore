package ws.siri.jscore.runtime.universal;

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
import ws.siri.jscore.runtime.universal.ClassMarkers.LangDef;
import ws.siri.jscore.runtime.universal.ClassMarkers.LangSpecificModule;

public class ModuleCache {
    private static ModuleCache instance = new ModuleCache();

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

    /**
     * preludes are applied in order, duplicates will not be removed
     * - if module is not cached and not on disk, throws an error
     * - if module.exports is undefined, returns empty()
     * - if module.exports is set, returns of()
     */
    public Optional<Value> get(List<String> path, String[] preludeNames) throws IOException {
        if (cache.containsKey(path))
            return cache.get(path).getExports();

        Path filePath = FabricLoader.getInstance().getConfigDir().resolve(JSCore.MOD_ID, path.toArray(new String[0]));
        String content = Files.readString(filePath);
        List<Prelude> filePreludes = getPreludes(preludeNames);

        Module module = new Module(path, filePreludes, content);
        cache.put(path, module);
        return module.getExports();
    }

    public boolean unload(List<String> path) {
        if (!cache.containsKey(path))
            return false;

        Module module = cache.remove(path);
        if (module.getOnUnload().isPresent())
            module.getOnUnload().get().run();

        return true;
    }

    public Repl spawnRepl(String fileExt, String[] preludeNames) {
        List<Prelude> filePreludes = getPreludes(preludeNames);
        List<String> replPath = List.of("__sys", "repls", Repl.genReplName(fileExt));
        Module module = new Module(replPath, filePreludes, "");
        cache.put(replPath, module);
        return new Repl(module);
    }
}
