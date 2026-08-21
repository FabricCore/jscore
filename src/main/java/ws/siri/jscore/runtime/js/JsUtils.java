package ws.siri.jscore.runtime.js;

import java.util.Optional;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.js.runtime.objects.Undefined;

public class JsUtils {
    /**
     * Check if JS value is undefined
     */
    public static boolean isUndefined(Value value) {
        return value.isNull() && value.getMetaObject().getMetaSimpleName().equals("undefined");
    }

    public static<T> Object unwrapOrUndefined(Optional<T> o) {
        return o.isPresent() ? o.get() : Undefined.instance;
    }
}
