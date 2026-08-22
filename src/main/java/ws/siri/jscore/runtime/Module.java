package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FilenameUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import ws.siri.jscore.runtime.ClassMarkers.LangDef;
import ws.siri.jscore.runtime.ModuleCache.Prelude;

/**
 * the module object should never be directly accessible, currently it is a
 * private property of
 * - modulecache
 * - repl
 */
public class Module {
    private List<String> path;
    private LangDef langDef;

    private Context ctx = Context.newBuilder().allowAllAccess(true).engine(Runtime.getInstance().getEngine()).build();
    private Optional<Value> exports = Optional.empty();
    private Optional<Runnable> onunload = Optional.empty();

    public Module(List<String> path, List<Prelude> preludes, String content) {
        if (path.isEmpty())
            throw new UnsupportedOperationException("path cannot be empty");

        String fileExt = FilenameUtils.getExtension(path.getLast());
        Optional<LangDef> langDef = Runtime.getInstance().getLangId(fileExt);

        if (langDef.isEmpty())
            throw new UnsupportedOperationException(
                    String.format("file extension .%s does not have a language associated with it", fileExt));

        this.langDef = langDef.get();
        this.path = path;

        // apply preludes
        Map<String, Object> globalScope = new HashMap<>();
        ProxyObject globalScopeProxy = ProxyObject.fromMap(globalScope);
        preludes.forEach(prelude -> prelude.apply(globalScopeProxy, this));
        globalScope.forEach((key, value) -> ctx.getBindings(this.langDef.id()).putMember(key, value));
        ctx.getBindings(this.langDef.id()).putMember("module", this.langDef.wrapModule(this));

        this.eval(content);
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

    public Value eval(String content) {
        try {
            Source src = Source.newBuilder(this.langDef.id(), content, String.join("/", path)).build(); // this could cause IO
                                                                                             // exceptions
            return ctx.eval(src);
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO: make this less shitty
        }
    }

    public List<String> getPath() {
        return path;
    }

    /**
     * import a file using a relative path, and apply preludes to the file
     * if the file is already loaded, the prelude list is ignored and a cached
     * version of it will return instead
     */
    public Optional<Value> importRelative(String path, String[] preludeNames) throws IOException {
        Path newPath = Path.of("/" + String.join("/", this.path)).getParent().resolve(path).normalize();
        List<String> newPathChunks = StreamSupport.stream(newPath.spliterator(), false).map(Path::toString).toList();
        return ModuleCache.getInstance().get(newPathChunks, preludeNames);
    }

    public String getName() {
        return String.join("/", this.path);
    }
}
