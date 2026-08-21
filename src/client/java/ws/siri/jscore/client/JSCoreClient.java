package ws.siri.jscore.client;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import ws.siri.jscore.JSCore;
import ws.siri.jscore.client.ui.commands.RegisterClientCmds;
import ws.siri.jscore.runtime.universal.ModuleCache;
import ws.siri.jscore.runtime.universal.Runtime;

public class JSCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RegisterClientCmds.register();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            Runtime.initialise();
            try {
                ModuleCache.getInstance().get(List.of("client", "index.js"), new String[0]);
            } catch (Exception e) {
                JSCore.LOGGER.error(e.toString()); // TODO: be less ridiculous
            }
        });
    }
}
