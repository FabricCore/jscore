package ws.siri.jscore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import ws.siri.jscore.runtime.Runtime;
import ws.siri.jscore.runtime.ClassMarkers.LangDef;

public class JSCoreConfig {
    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Optional<JSCoreConfig> instance = Optional.empty();

    /**
     * which language should be prioritised to when creating the first repl
     *
     * this list should contain all the registered languages
     *
     * list of language IDs
     */
    private List<String> replLanguagePriorities = new ArrayList<>();
    private String clientEntryPoint = "client/index.js";
    private String serverEntryPoint = "server/index.js";

    private JSCoreConfig() {
    }

    private static JSCoreConfig load() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("jscore/config.json");
            if (!Files.exists(configPath))
                return new JSCoreConfig();

            String content = Files.readString(configPath);
            return GSON.fromJson(content, JSCoreConfig.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void save() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("jscore/master.config.json");
            if (!Files.exists(configPath.getParent()))
                Files.createDirectories(configPath.getParent());

            String content = GSON.toJson(this);
            Files.writeString(configPath, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void ensureInitialised() {
        if (instance.isPresent())
            return;

        JSCoreConfig config = load();

        // add missing languages to replLanguagePriorities
        Set<String> replPriorityIncludedLangs = new HashSet<>(config.replLanguagePriorities);
        // all supported languages
        Set<String> missingLanguages = new HashSet<>(
                Runtime.getInstance().getAllLangDefs().values().stream().map(LangDef::id).toList());
        // now it is the missing languages
        missingLanguages.removeAll(replPriorityIncludedLangs);
        missingLanguages.forEach(langId -> {
            config.replLanguagePriorities.add(langId);
        });

        instance = Optional.of(config);
        config.save();
    }

    public static JSCoreConfig getInstance() {
        if (instance.isEmpty())
            throw new RuntimeException("calling getInstance before config is initialised");

        return instance.get();
    }

    // getters / setters
    public List<String> getReplLanguagePriority() {
        return replLanguagePriorities;
    }

    public void setReplLanguagePriority(List<String> priorities) {
        replLanguagePriorities = priorities;
        save();
    }

    public String getClientEntryPoint() {
        return clientEntryPoint;
    }

    public void setClientEntryPoint(String path) {
        clientEntryPoint = path;
        save();
    }

    public String getServerEntryPoint() {
        return serverEntryPoint;
    }

    public void setServerEntryPoint(String path) {
        serverEntryPoint = path;
        save();
    }
}
