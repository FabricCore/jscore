package ws.siri.jscore.runtime;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.graalvm.polyglot.Value;

public class Repl {
    private Module internal;
    private static Optional<Repl> focusedClient = Optional.empty();
    private static Optional<Repl> focusedServer = Optional.empty();

    public static String genReplName(String fileExt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss.SSSSSS");
        return String.format("%s-%s.%s", LocalDateTime.now().format(formatter),
                UUID.randomUUID().toString().subSequence(0, 8), fileExt);
    }

    public Repl(Module internal) {
        this.internal = internal;
    }

    public Value evaluate(String expression) throws IOException {
        // TODO: what3words for line
        // TODO: save line to file
        return internal.eval(expression);
    }

    public static synchronized Repl getFocusedClient() {
        if (focusedClient.isPresent())
            return focusedClient.get();

        // TODO: select file ext
        // TODO: select prelude
        focusedClient = Optional.of(ModuleCache.getInstance().spawnRepl("js", new String[0]));
        return focusedClient.get();
    }

    public static synchronized Repl getFocusedServer() {
        if (focusedServer.isPresent())
            return focusedServer.get();

        // TODO: select file ext
        // TODO: select prelude
        focusedServer = Optional.of(ModuleCache.getInstance().spawnRepl("js", new String[0]));
        return focusedServer.get();
    }

    public String getName() {
        return this.internal.getName();
    }
}
