package ws.siri.jscore.runtime.js;

import ws.siri.jscore.runtime.universal.ClassMarkers.LangDef;
import ws.siri.jscore.runtime.universal.ClassMarkers.LangSpecificModule;
import ws.siri.jscore.runtime.universal.Module;
import ws.siri.jscore.runtime.universal.Runtime;

public class JsLangDef implements LangDef {
    static {
        Runtime.registerSupportedLanguage(new JsLangDef());
    }

    @Override
    public String id() {
        return "js";
    }

    @Override
    public String[] exts() {
        return new String[] { "js" };
    }

    @Override
    public LangSpecificModule wrapModule(Module module) {
        return new JsModule(module);
    }
}
