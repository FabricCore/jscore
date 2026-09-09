package ws.siri.jscore.client.ui.metrics;

import org.slf4j.event.Level;

import ws.siri.jscore.ui.metrics.JscLogger;

public class JscClientLogger {
    public static void registerLogger() {
        JscLogger.registerClientLogger((tags, level, message) -> {
        });
    }

    public static void log(String[] tags, Level level, String content) {
    }
}
