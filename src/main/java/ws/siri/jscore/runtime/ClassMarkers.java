package ws.siri.jscore.runtime;

import org.graalvm.polyglot.proxy.ProxyObject;

public class ClassMarkers {
    public interface LangSpecificModule extends ProxyObject {
    }

    public interface LangDef {
        /**
         * Graal language ID
         */
        String id();

        /**
         * Anticipated file extensions, must be nonempty, and the first item is the default ext
         */
        String[] exts();

        /**
         * Wrap a module with a language specific module object
         */
        LangSpecificModule wrapModule(Module module);
    }
}
