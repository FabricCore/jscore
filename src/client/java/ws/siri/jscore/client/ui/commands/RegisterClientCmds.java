package ws.siri.jscore.client.ui.commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import ws.siri.jscore.runtime.Repl;
import ws.siri.jscore.ui.commands.CmdSource;
import ws.siri.jscore.ui.commands.CmdTree;

public class RegisterClientCmds {
    public static final CmdSource<FabricClientCommandSource> SOURCE = new CmdSource<>() {
        @Override
        public Repl getFocusedRepl() {
            return Repl.getFocusedClient();
        }

        @Override
        public void sendSuccess(FabricClientCommandSource src, Component msg, boolean broadcast) {
            src.sendFeedback(msg);
        }

        @Override
        public void sendFailure(FabricClientCommandSource src, Component msg) {
            src.sendError(msg);
        }

        @Override
        public Player getPlayer(FabricClientCommandSource src) {
            return src.getPlayer();
        };
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher
                        .register(CmdTree.build("jsc", SOURCE, FabricClientCommandSource::attended)));
    }
}
