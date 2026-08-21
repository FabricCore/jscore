package ws.siri.jscore.runtime.js;

import ws.siri.jscore.runtime.universal.Module;
import ws.siri.jscore.runtime.universal.ClassMarkers.LangSpecificModule;
import ws.siri.jscore.Utils;
import ws.siri.jscore.runtime.universal.Errors;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * The module object accessible in that file.
 */
public class JsModule implements LangSpecificModule {
    private Module internal;

    public JsModule(Module internal) {
        this.internal = internal;
    }

    @FunctionalInterface
    public interface ThroableBiFunction<T, U, R, E extends Exception> {
        R apply(T t, U u) throws E;
    }

    @Override
    public Object getMember(String key) {
        switch (key) {
            case "exports":
                return Utils.dangerouslyCastOptional(internal.getExports()).orElse(Undefined.instance);
            case "onunload":
                return Utils.dangerouslyCastOptional(internal.getOnUnload()).orElse(Undefined.instance);
            case "import":
                // note: Value is String[]
                return (ThroableBiFunction<String, List<String>, Object, IOException>) (path, preludeNames) -> {
                    Optional<Value> res = internal.importRelative(path, preludeNames.toArray(String[]::new));
                    return JsUtils.unwrapOrUndefined(res);
                };
            default:
                return Undefined.instance;
        }
    }

    @Override
    public Object getMemberKeys() {
        return new String[] { "exports", "onload", "import" };
    }

    @Override
    public boolean hasMember(String key) {
        switch (key) {
            case "exports":
            case "onload":
            case "import":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void putMember(String key, Value value) {
        switch (key) {
            case "exports":
                if (JsUtils.isUndefined(value))
                    this.internal.setExports(Optional.empty());
                else
                    this.internal.setExports(Optional.of(value));
                break;
            case "onunload":
                if (JsUtils.isUndefined(value))
                    this.internal.setOnUnload(Optional.empty());
                else if (!value.canExecute())
                    throw new Errors.TypeMismatchException("function", value.getMetaObject().getMetaSimpleName());
                else
                    this.internal.setOnUnload(Optional.of(value.as(Runnable.class)));
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean removeMember(String key) {
        switch (key) {
            case "exports":
                internal.setExports(Optional.empty());
                return true;
            case "onunload":
                internal.setOnUnload(Optional.empty());
                return true;
            default:
                return false;
        }
    }
}
