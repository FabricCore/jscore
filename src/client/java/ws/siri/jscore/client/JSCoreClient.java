package ws.siri.jscore.client;

import java.util.Arrays;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import ws.siri.jscore.JSCore;
import ws.siri.jscore.JSCoreConfig;
import ws.siri.jscore.client.ui.commands.RegisterClientCmds;
import ws.siri.jscore.runtime.ModuleCache;
import ws.siri.jscore.runtime.Runtime;

public class JSCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RegisterClientCmds.register();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            Runtime.initialise();
            JSCoreConfig.initialise();
            List<String> clientEntryPoint = Arrays.asList(JSCoreConfig.getInstance().getClientEntryPoint().split("/"));

            if (!ModuleCache.getInstance().fileExistsFor(clientEntryPoint))
                return;

            try {
                ModuleCache.getInstance()
                        .get(clientEntryPoint, new String[0]);
            } catch (Exception e) {
                JSCore.LOGGER.error(e.toString()); // TODO: be less ridiculous
            }
        });
    }
}
