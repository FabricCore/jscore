package ws.siri.jscore.runtime.universal;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import ws.siri.jscore.runtime.universal.ModuleCache.Prelude;

/**
 * the module object should never be directly accessible, currently it is a
 * private property of
 * - modulecache
 * - repl
 */
public class Module {
    private List<String> path;
    private String langId;

    private Context ctx = Context.newBuilder().allowAllAccess(true).engine(Runtime.getInstance().getEngine()).build();
    private Optional<Value> exports = Optional.empty();
    private Optional<Runnable> onunload = Optional.empty();

    public Module(List<String> path, List<Prelude> preludes, String content) {
        if (path.isEmpty())
            throw new UnsupportedOperationException("path cannot be empty");

        String fileExt = FilenameUtils.getExtension(path.getLast());
        Optional<String> langId = Runtime.getInstance().getLangId(fileExt);

        if (langId.isEmpty())
            throw new UnsupportedOperationException(
                    String.format("file extension .%s does not have a language associated with it", fileExt));

        this.langId = langId.get();
        this.path = path;

        // apply preludes
        Map<String, Object> globalScope = new HashMap<>();
        ProxyObject globalScopeProxy = ProxyObject.fromMap(globalScope);
        preludes.forEach(prelude -> prelude.apply(globalScopeProxy, this));

        globalScope.forEach((key, value) -> ctx.getBindings(this.langId).putMember(key, value));
    }

    public Optional<Value> getExports() {
        return exports;
    }

    public void setExports(Optional<Value> exports) {
        this.exports = exports;
    }

    public Optional<Runnable> getOnUnload() {
        return onunload;
    }

    public void setOnUnload(Optional<Runnable> onunload) {
        this.onunload = onunload;
    }

    public Value eval(String content) throws IOException {
        Source src = Source.newBuilder(langId, content, String.join("/", path)).build();
        return ctx.eval(src);
    }

    public List<String> getPath() {
        return path;
    }
}
