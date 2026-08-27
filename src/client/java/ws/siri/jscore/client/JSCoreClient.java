package ws.siri.jscore.client;

import java.util.Arrays;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import ws.siri.jscore.JSCore;
import ws.siri.jscore.JSCoreConfig;
import ws.siri.jscore.client.ui.commands.RegisterClientCmds;
import ws.siri.jscore.runtime.Runtime;

public class JSCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RegisterClientCmds.register();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            Runtime.initialise();
            JSCoreConfig.ensureInitialised();
            List<String> clientEntryPoint = Arrays.asList(JSCoreConfig.getInstance().getClientEntryPoint().split("/"));

            JSCore.loadEntryPointIfExists(clientEntryPoint);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register((client) -> {
            JSCoreConfig.ensureInitialised();
            List<String> clientEntryPoint = Arrays.asList(JSCoreConfig.getInstance().getClientEntryPoint().split("/"));
            JSCore.unloadEntryPointIfExists(clientEntryPoint);
        });
    }
}
