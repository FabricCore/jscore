package ws.siri.jscore.runtime.universal;

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
         * Anticipated file extensions
         */
        String[] exts();

        /**
         * Wrap a module with a language specific module object
         */
        LangSpecificModule wrapModule(Module module);
    }
}
