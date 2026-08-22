package ws.siri.jscore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import ws.siri.jscore.runtime.ModuleCache;
import ws.siri.jscore.runtime.Runtime;
import ws.siri.jscore.ui.commands.RegisterServerCmds;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSCore implements ModInitializer {
    public static final String MOD_ID = "jscore";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RegisterServerCmds.register();

        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            Runtime.initialise();
            JSCoreConfig.initialise();

            List<String> serverEntryPoint = Arrays.asList(JSCoreConfig.getInstance().getServerEntryPoint().split("/"));

            if (!ModuleCache.getInstance().fileExistsFor(serverEntryPoint))
                return;

            try {
                ModuleCache.getInstance()
                        .get(serverEntryPoint, new String[0]);
            } catch (Exception e) {
                JSCore.LOGGER.error(e.toString()); // TODO: be less ridiculous
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
