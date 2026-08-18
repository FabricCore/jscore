package ws.siri.jscore.runtime.universal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.graalvm.polyglot.Engine;

import ws.siri.jscore.runtime.universal.ClassMarkers.LangDef;

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
    private HashMap<String, String> langExts;

    public static void registerSupportedLanguage(LangDef langDef) {
        if (instance != null)
            throw new UnsupportedOperationException("can only be called before getInstance() is first used");

        supportedLanguages.add(langDef);
    }

    private Runtime() {
        engine = Engine.create(supportedLanguages.stream().map(def -> def.id()).toList().toArray(new String[0]));

        langExts = new HashMap<>();

        for (LangDef def : supportedLanguages)
            for (String ext : def.exts())
                langExts.put(ext, def.id());
    }

    public static Runtime getInstance() {
        if (instance == null)
            instance = new Runtime();
        return instance;
    }

    public Engine getEngine() {
        return engine;
    }

    public Optional<String> getLangId(String fileExt) {
        if (langExts.containsKey(fileExt))
            return Optional.of(langExts.get(fileExt));
        else
            return Optional.empty();
    }
}
