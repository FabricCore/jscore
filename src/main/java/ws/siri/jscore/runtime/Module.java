package ws.siri.jscore.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FilenameUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import ws.siri.jscore.JSCore;
import ws.siri.jscore.Utils;
import ws.siri.jscore.Utils.CounterLock;
import ws.siri.jscore.runtime.ClassMarkers.LangDef;
import ws.siri.jscore.runtime.ModuleCache.Prelude;

/**
 * the module object should never be directly accessible, currently it is a
 * private property of
 * - modulecache
 * - repl
 */
// TODO: note the possibility of cross thread import cycles, described below:
// suppose both events happens at the same time
// - A imports B before B is initialised (A loads not because of B)
// - B imports A before A is initialised (B loads not because of A)
// Then A and B are both waiting for each other to initialise
// This is ignored for now but in future it should be addressed
public class Module {
    private final List<String> path;
    /**
     * language used for this module
     */
    private final LangDef langDef;
    /**
     * preludes used when creating the module
     */
    private final List<Prelude> preludes;
    /**
     * string used when creating the module
     */
    private final String content;

    public static enum ModulePhase {
        /**
         * default state when created
         */
        INERT,
        /**
         * an initialisation has been request
         */
        INITIALISING,
        /**
         * still initialising, but an unload has been requested, the when init has
         * completed the next state is unloading
         */
        UNLOAD_WAITING_INIT,
        /**
         * the module isn't doing anything special, state goes to unloading whenever
         * unloading is requseted
         */
        ACTIVE,
        /**
         * the module is being unloaded, in this state, module.import is not allowed
         */
        UNLOADING;

        public boolean isInitialised() {
            return this == ACTIVE || this == UNLOADING;
        }

        public boolean unloadRequested() {
            return this == UNLOADING || this == UNLOAD_WAITING_INIT;
        }
    }

    /**
     * current state of the module
     */
    private ModulePhase __phase = ModulePhase.INERT;
    /**
     * evaluation error on initialisation
     */
    private volatile Optional<RuntimeException> initError = Optional.empty();

    private Context ctx;
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
    private final Set<List<String>> dependencies = ConcurrentHashMap.newKeySet();
    /**
     * set of modules that imports this module
     */
    private final Set<List<String>> dependents = ConcurrentHashMap.newKeySet();

    /**
     * blocks until the module has no more dependents (including the ones that are
     * unloading, does not include explicitly loaded)
     */
    private final CounterLock dependentsWaiter = new CounterLock(0, false);
    /**
     * blocks until the module is fully unloaded
     */
    private final CountDownLatch unloadWaiter = new CountDownLatch(1);
    /**
     * blocks until init is complete
     */
    private final CountDownLatch initWaiter = new CountDownLatch(1);

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
        this.content = content;

        if (importedFrom.isPresent()) {
            this.dependents.add(importedFrom.get());
            this.dependentsWaiter.countUp();
        } else
            this.explicitlyLoaded = true;
    }

    /**
     * lock to make sure only one copy of usePhase is active at a time
     */
    private Lock phaseLock = new ReentrantLock();

    <T> T usePhase(Function<ModulePhase, T> f) {
        try {
            phaseLock.lock();
            return f.apply(__phase);
        } finally {
            phaseLock.unlock();
        }
    }

    public List<String> getPath() {
        return path;
    }

    /**
     * this must be called outside of useCache as running initialise can take a
     * while, we dont want to block useCache
     */
    void initialise() {
        usePhase((phase) -> {
            switch (phase) {
                case UNLOADING:
                case ACTIVE:
                case INITIALISING:
                    throw new UnsupportedOperationException("multiple calls of initialise for the same module");
                case INERT:
                    __phase = ModulePhase.INITIALISING;
                    break;
                case UNLOAD_WAITING_INIT:
                    break;
            }
            return null;
        });

        try {
            ctx = Context.newBuilder().allowAllAccess(true).engine(Runtime.getInstance().getEngine()).build();

            // apply preludes
            Map<String, Object> globalScope = new HashMap<>();
            ProxyObject globalScopeProxy = ProxyObject.fromMap(globalScope);
            preludes.forEach(prelude -> prelude.apply(globalScopeProxy, this));
            globalScope.forEach((key, value) -> ctx.getBindings(this.langDef.id()).putMember(key, value));
            ctx.getBindings(this.langDef.id()).putMember("module", this.langDef.wrapModule(this));
            this.evalWithoutWaiting(content);
        } catch (RuntimeException e) {
            initError = Optional.of(e);
        }

        try {
            usePhase(phase -> {
                switch (phase) {
                    case INITIALISING:
                        __phase = ModulePhase.ACTIVE;
                        break;
                    case UNLOAD_WAITING_INIT:
                        __phase = ModulePhase.UNLOADING;
                        break;
                    default:
                        throw new RuntimeException(
                                String.format("not possible to have state %s when done initialising",
                                        phase.toString()));
                }

                return null;
            });
        } finally {
            initWaiter.countDown();
        }
    }

    /**
     * used only by module.import from another module
     */
    Optional<Value> waitForExports() {
        usePhase(phase -> {
            switch (phase) {
                case UNLOADING:
                case UNLOAD_WAITING_INIT:
                    throw new UnsupportedOperationException("cannot wait for exports while unloading");
                default:
                    break;
            }

            return null;
        });

        Utils.waitFor(initWaiter);
        if (initError.isPresent())
            throw initError.get();

        return exports;
    }

    /**
     * DANGER: this should only be used by ModuleCache
     *
     * unloads module and triggers unload cascade
     *
     * at this stage, the module should've been already removed from ModuleCache
     *
     * this must be called outside of useCache as this can take a while,
     * we dont want to block useCache
     *
     * note: a failed unload will not throw an exception
     *
     * note 2: this does not unlock the unload lock, you'll have to do it separately
     */
    void doUnloadCleanup() {
        Utils.waitFor(initWaiter);

        usePhase(phase -> {
            __phase = ModulePhase.UNLOADING;
            return null;
        });

        try {
            onunload.ifPresent(Runnable::run);
        } catch (RuntimeException e) {
            // TODO: use the custom logger
            JSCore.LOGGER.error(e.getMessage(), e);
        }

        try {
            ctx.close(true); // force interrupts
        } catch (RuntimeException e) {
            // TODO: use the custom logger
            JSCore.LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * this must be called outside of useCache as this can take a while,
     * we dont want to block useCache
     */
    Value eval(String content) {
        Utils.waitFor(initWaiter);
        return evalWithoutWaiting(content);
    }

    /**
     * eval without waiting logic, only to be used in eval and init
     */
    private synchronized Value evalWithoutWaiting(String content) {
        try {
            Source src = Source.newBuilder(this.langDef.id(), content, String.join("/", path)).build(); // this could
                                                                                                        // cause IO
            // exceptions
            return ctx.eval(src);
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO: make this less shitty
        }
    }

    /**
     * this should ONLY be used by lang specific modules
     *
     * import a file using a relative path, and apply preludes to the file
     *
     * if the file is already loaded, throws an error if prelude list mismatches
     */
    public Optional<Value> importRelative(String path, String[] preludeNames) throws IOException {
        Path newPath = Path.of("/" + String.join("/", this.path)).getParent().resolve(path).normalize();
        List<String> newPathChunks = StreamSupport.stream(newPath.spliterator(), false).map(Path::toString).toList();
        return ModuleCache.getInstance().get(newPathChunks, preludeNames, Optional.of(this));
    }

    // stuff used by module cache to manage the cache DAG
    boolean shouldUnload() {
        return !explicitlyLoaded && dependents.isEmpty();
    }

    public String getName() {
        return String.join("/", this.path);
    }

    void unsetExplicitlyLoaded() {
        explicitlyLoaded = false;
    }

    void addDependent(List<String> path) {
        if (dependents.add(path))
            dependentsWaiter.countUp();
    }

    void startDependentUnloading(List<String> path) {
        dependents.remove(path);
    }

    void removeDependentOnly(List<String> path) {
        dependents.remove(path);
        dependentsWaiter.countDown();
    }

    void endDependentUnloading() {
        dependentsWaiter.countDown();
    }

    void addDependency(List<String> path) {
        dependencies.add(path);
    }

    void removeDependency(List<String> path) {
        dependencies.remove(path);
    }

    Optional<RuntimeException> getInitError() {
        return initError;
    }

    /**
     * this is only to be used by ModuleCache to resolve the unload tree
     *
     * the set is immutable
     */
    Set<List<String>> getDependents() {
        return Collections.unmodifiableSet(dependents);
    }

    boolean isDependencyOf(Optional<List<String>> path) {
        if (path.isPresent())
            return dependents.contains(path.get());
        else
            return isExplicitlyLoaded();
    }

    /**
     * this is only to be used by ModuleCache to resolve the unload tree
     *
     * the set is immutable
     */
    Set<List<String>> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    /**
     * this is only to be used by ModuleCache to resolve the unload tree
     */
    boolean isExplicitlyLoaded() {
        return explicitlyLoaded;
    }

    /**
     * mark this module as starting unloading
     */
    void startUnloading() {
        usePhase(phase -> {
            switch (phase) {
                case UNLOADING:
                case UNLOAD_WAITING_INIT:
                    throw new UnsupportedOperationException(
                            "startUnloading called multiple times, which isn't possible");
                case ACTIVE:
                    __phase = ModulePhase.UNLOADING;
                    break;
                case INERT:
                case INITIALISING:
                    __phase = ModulePhase.UNLOAD_WAITING_INIT;
                    break;
            }

            return null;
        });
    }

    void doneUnloading() {
        if (usePhase(p -> p) != ModulePhase.UNLOADING)
            throw new UnsupportedOperationException("doneUnloading when the module is not being unloaded");
        unloadWaiter.countDown();
    }

    boolean preludeMatches(List<Prelude> other) {
        // Prelude has no .equal, but this is correct
        // Prelude with a specific name cannot be changed when a module using it is
        // active
        // TODO: ref count Prelude as well
        return preludes.equals(other);
    }

    /**
     * DANGER! this should ONLY be accessed by lang specific module
     */
    public Optional<Value> getExportsInternal() {
        return exports;
    }

    /**
     * DANGER! this should ONLY be accessed by lang specific module
     */
    public void setExportsInternal(Optional<Value> exports) {
        this.exports = exports;
    }

    /**
     * DANGER! this should ONLY be accessed by lang specific module
     */
    public Optional<Runnable> getOnUnloadInternal() {
        return onunload;
    }

    /**
     * DANGER! this should ONLY be accessed by lang specific module
     */
    public void setOnUnloadInternal(Optional<Runnable> onunload) {
        this.onunload = onunload;
    }

    void waitForInit() {
        Utils.waitFor(initWaiter);
    }

    void waitForUnload() {
        Utils.waitFor(unloadWaiter);
    }

    void waitForAllDependentsToUnload() {
        Utils.waitFor(dependentsWaiter);
    }
}
