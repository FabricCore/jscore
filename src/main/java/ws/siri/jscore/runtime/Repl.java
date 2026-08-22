package ws.siri.jscore.runtime;

import java.io.IOException;
import java.lang.StackWalker.Option;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.graalvm.polyglot.Value;

import ws.siri.jscore.JSCoreConfig;
import ws.siri.jscore.runtime.ClassMarkers.LangDef;

public class Repl {
    private Module internal;
    private boolean isFresh = true;
    private static Optional<Repl> focusedClient = Optional.empty();
    private static Optional<Repl> focusedServer = Optional.empty();

    public static String genReplName(String fileExt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        return String.format("%s-%s.%s", LocalDateTime.now().format(formatter),
                UUID.randomUUID().toString().subSequence(0, 8), fileExt);
    }

    public Repl(Module internal) {
        this.internal = internal;
    }

    private static Optional<LangDef> defaultReplLanguage() {
        Map<String, LangDef> langDefs = Runtime.getInstance().getAllLangDefs();
        return JSCoreConfig.getInstance().getReplLanguagePriority().stream()
                .filter(langId -> langDefs.containsKey(langId)).map(langId -> langDefs.get(langId)).findFirst();
    }

    public Value evaluate(String expression) throws IOException {
        // TODO: method for saving line to file
        isFresh = false;
        return internal.eval(expression);
    }

    private static Optional<Repl> getFocusedOf(Optional<Repl> cache) {
        if (cache.isPresent())
            return cache;

        Optional<LangDef> lang = defaultReplLanguage();
        if (lang.isEmpty())
            return Optional.empty();

        return Optional.of(ModuleCache.getInstance().spawnRepl(lang.get().id(), new String[0]));
    }

    public static synchronized Optional<Repl> getFocusedClient() {
        focusedClient = getFocusedOf(focusedClient);
        return focusedClient;
    }

    public static synchronized Optional<Repl> getFocusedServer() {
        focusedServer = getFocusedOf(focusedServer);
        return focusedServer;
    }

    public String getName() {
        return this.internal.getName();
    }

    public boolean isFresh() {
        return this.isFresh;
    }
}
