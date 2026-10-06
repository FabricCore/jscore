package ws.siri.jscore.runtime;

import org.graalvm.polyglot.Context;

public interface LangDef {
    /**
     * Graal language ID
     */
    String id();

    /**
     * Anticipated file extensions, must be nonempty, and the first item is the
     * default ext
     */
    String[] exts();

    /**
     * Wrap a module with a language specific module object
     */
    LangSpecificModule wrapModule(Module module);

    /**
     * Complete preparation for the context (this runs BEFORE prelude)
     */
    void prepare(Context ctx, LangSpecificModule module);
}
