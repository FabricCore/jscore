package ws.siri.jscore.runtime.js;

import org.graalvm.polyglot.Value;

import ws.siri.jscore.runtime.universal.Runtime;

public class JsUtils {
    static {
        Runtime.registerSupportedLanguage(new JsLangDef());
    }

    /**
     * Check if JS value is undefined
     */
    public static boolean isUndefined(Value value) {
        return value.isNull() && value.getMetaObject().getMetaSimpleName().equals("undefined");
    }

}
