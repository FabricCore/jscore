package ws.siri.jscore.runtime.js;

import org.graalvm.polyglot.Value;

public class JsUtils {
    /**
     * Check if JS value is undefined
     */
    public static boolean isUndefined(Value value) {
        return value.isNull() && value.getMetaObject().getMetaSimpleName().equals("undefined");
    }

}
