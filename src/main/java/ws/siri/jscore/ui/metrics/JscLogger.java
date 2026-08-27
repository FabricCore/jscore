package ws.siri.jscore.ui.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.function.TriConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import ws.siri.jscore.JSCore;

public class JscLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSCore.MOD_ID);

    private static final Level M_LEVEL = Level.INFO;
    private static Optional<TriConsumer<String[], Level, String>> clientLogger = Optional.empty();

    public static void registerClientLogger(TriConsumer<String[], Level, String> handler) {
        if (clientLogger.isPresent())
            throw new UnsupportedOperationException("client logger has already been registered");

        clientLogger = Optional.of(handler);
    }

    private String[] tags;

    public JscLogger() {
        this.tags = new String[0];
    }

    public JscLogger(String[] tags) {
        this.tags = tags;
    }

    public JscLogger tag(String s) {
        ArrayList<String> tags = new ArrayList<>(Arrays.asList(this.tags));
        tags.add(s);
        return new JscLogger(tags.toArray(String[]::new));
    }

    public void log(Level level, String content) {
        // level < M_LEVEL
        if (level.compareTo(M_LEVEL) > 0)
            return;

        clientLogger.ifPresent((handler) -> handler.accept(this.tags, level, content));
        LOGGER.atLevel(level).log(String.format("[%s] %s", String.join("][", tags), content));
    }
}
