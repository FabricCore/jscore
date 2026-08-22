package ws.siri.jscore.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.graalvm.polyglot.Engine;

import ws.siri.jscore.runtime.ClassMarkers.LangDef;

public class Runtime {
    /**
     * null : before getInstance() is first ran
     */
    private static Runtime instance = null;
    private static Set<LangDef> supportedLanguages = new HashSet<>();

    private Engine engine;
    /**
     * <file ext, lang ID>
     */
    private Map<String, LangDef> langExts;
    /**
     * <lang ID, lang def>
     */
    private Map<String, LangDef> langDefs;

    public static void registerSupportedLanguage(LangDef langDef) {
        if (instance != null)
            throw new UnsupportedOperationException("can only be called before getInstance() is first used");

        supportedLanguages.add(langDef);
    }

    private Runtime() {
        engine = Engine.create(supportedLanguages.stream().map(def -> def.id()).toList().toArray(String[]::new));

        langExts = new HashMap<>();
        langDefs = new HashMap<>();

        for (LangDef def : supportedLanguages) {
            langDefs.put(def.id(), def);
            for (String ext : def.exts())
                langExts.put(ext, def);
        }
    }

    public static synchronized void initialise() {
        if (instance != null)
            return;
        instance = new Runtime();
    }

    public static Runtime getInstance() {
        if (instance == null)
            throw new UnsupportedOperationException("runtime is not yet initialised");
        return instance;
    }

    public Engine getEngine() {
        return engine;
    }

    public Optional<LangDef> getLangId(String fileExt) {
        if (langExts.containsKey(fileExt))
            return Optional.of(langExts.get(fileExt));
        else
            return Optional.empty();
    }

    /**
     * <lang ID, lang def>
     */
    public Map<String, LangDef> getAllLangDefs() {
        return langDefs;
    }
}
