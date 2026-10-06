package ws.siri.jscore.runtime;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.graalvm.polyglot.proxy.ProxyObject;

public class Prelude {
    private Module sourceModule;
    private BiConsumer<ProxyObject, LangSpecificModule> preludeFunction;

    public Prelude(Module sourceModule, BiConsumer<ProxyObject, LangSpecificModule> preludeFunction) {
        this.sourceModule = sourceModule;
        this.preludeFunction = preludeFunction;
    }

    public void apply(ProxyObject globalScope, Module module) {
        preludeFunction.accept(globalScope, sourceModule.getLangDef().wrapModule(module));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Prelude))
            return false;

        Prelude other = (Prelude) obj;
        return this.preludeFunction.equals(other.preludeFunction)
                && this.sourceModule.equals(other.sourceModule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.preludeFunction, this.sourceModule);
    }

    public Module getSourceModule() {
        return sourceModule;
    }
}
