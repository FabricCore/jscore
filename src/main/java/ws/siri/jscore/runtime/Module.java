package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    /**
     * language used for this module
     */
    private LangDef langDef;

    // these values are relevant when initialisation has not yet completed,
    // they are a copy of the parameters passed on instantiation so initialisation
    // don't block the instantiation queue which is not parallelised
    /**
     * whether the initial evaluation has completed
     */
    private boolean isInitalised = false;
    private List<Prelude> preludes;
    /**
     * this field is cleared after initialised
     */
    private String content;
    /**
     * evaluation error on initialisation
     */
    private Optional<RuntimeException> initError = Optional.empty();

    private Context ctx = Context.newBuilder().allowAllAccess(true).engine(Runtime.getInstance().getEngine()).build();
    private Optional<Value> exports = Optional.empty();
    private Optional<Runnable> onunload = Optional.empty();

    /**
     * whether the loading was explicit: all modules must have an explicit dependent
     * (transitive)
     * otherwise it is unloaded
     *
     * examples of explicitly loaded modules are
     * - entry points to the server/client scripts
     * - repls
     */
    private boolean explicitlyLoaded = false;
    /**
     * set of modules this module imports
     */
    private Set<List<String>> dependencies = ConcurrentHashMap.newKeySet();
    /**
     * set of modules that imports this module
     */
    private Set<List<String>> dependents = ConcurrentHashMap.newKeySet();

    /**
     * importedFrom: which module imports this module causing it to load? if empty,
     * the module is explicitly loaded
     */
    Module(List<String> path, List<Prelude> preludes, String content, Optional<List<String>> importedFrom) {
        if (path.isEmpty())
            throw new UnsupportedOperationException("path cannot be empty");

        String fileExt = FilenameUtils.getExtension(path.getLast());
        Optional<LangDef> langDef = Runtime.getInstance().getLangId(fileExt);

        if (langDef.isEmpty())
            throw new UnsupportedOperationException(
                    String.format("file extension .%s does not have a language associated with it", fileExt));

        this.langDef = langDef.get();
        this.path = path;
        this.preludes = preludes;

        if (importedFrom.isPresent())
            this.dependents.add(importedFrom.get());
        else
            this.explicitlyLoaded = true;
    }

    synchronized void ensureInitialised() {
        if (isInitalised) {
            if (initError.isPresent())
                throw initError.get();

            return;
        }

        try {
            // apply preludes
            Map<String, Object> globalScope = new HashMap<>();
            ProxyObject globalScopeProxy = ProxyObject.fromMap(globalScope);
            preludes.forEach(prelude -> prelude.apply(globalScopeProxy, this));
            globalScope.forEach((key, value) -> ctx.getBindings(this.langDef.id()).putMember(key, value));
            ctx.getBindings(this.langDef.id()).putMember("module", this.langDef.wrapModule(this));
            this.eval(content);
        } catch (RuntimeException e) {
            initError = Optional.of(e);
            throw e;
        } finally {
            isInitalised = true;
            content = null;
        }

    }

    public Optional<Value> getExports() {
        ensureInitialised();
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

    /**
     * DANGER: this should only be used by ModuleCache
     *
     * unloads module and triggers unload cascade
     *
     * at this stage, the module should've been already removed from ModuleCache
     */
    void unloadInternal() {
        try {
            // wait for initialisation to complete so onunload is correct
            ensureInitialised();
        } catch (RuntimeException e) {
            // DONT CARE!
        } finally {
            try {
                onunload.ifPresent(Runnable::run);
            } finally {
                ctx.close();
                // the cascade must happen regardless of whether onunload failed
                Set<List<String>> oldDependencies = dependencies;
                dependencies = ConcurrentHashMap.newKeySet();
                // dont iterate and modify dependencies at the same time
                // as unimportModule modifies the dependencies
                oldDependencies.forEach(depPath -> ModuleCache.getInstance().unimportModule(depPath, Optional.of(this)));
            }
        }
    }

    synchronized Value eval(String content) {
        ensureInitialised();
        try {
            Source src = Source.newBuilder(this.langDef.id(), content, String.join("/", path)).build(); // this could
                                                                                                        // cause IO
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
        return ModuleCache.getInstance().get(newPathChunks, preludeNames, Optional.of(this));
    }

    public String getName() {
        return String.join("/", this.path);
    }

    boolean shouldUnload() {
        return !explicitlyLoaded && dependents.isEmpty();
    }

    void unsetExplicitlyLoaded() {
        explicitlyLoaded = false;
    }

    void addDependent(List<String> path) {
        dependents.add(path);
    }

    void removeDependent(List<String> path) {
        dependents.remove(path);
    }

    void addDependency(List<String> path) {
        dependencies.add(path);
    }

    void removeDependency(List<String> path) {
        dependencies.remove(path);
    }

    boolean preludeMatches(List<Prelude> other) {
        // Prelude has no .equal, but this is correct
        // Prelude with a specific name cannot be changed when a module using it is
        // active
        // TODO: ref count Prelude as well
        return preludes.equals(other);
    }
}
