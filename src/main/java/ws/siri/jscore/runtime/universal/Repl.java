package ws.siri.jscore.runtime.universal;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.graalvm.polyglot.Value;

public class Repl {
    private Module internal;
    private static Optional<Repl> focused = Optional.empty();

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

    public static Repl getFocused() {
        if (focused.isPresent())
            return focused.get();

        // TODO: select file ext
        // TODO: select prelude
        focused = Optional.of(ModuleCache.getInstance().spawnRepl("js", new String[0]));
        return focused.get();
    }
}
