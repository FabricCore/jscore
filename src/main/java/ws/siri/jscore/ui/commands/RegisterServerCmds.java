package ws.siri.jscore.ui.commands;

import java.util.Optional;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import ws.siri.jscore.runtime.Repl;

public class RegisterServerCmds {
    public static final CmdSource<CommandSourceStack> SOURCE = new CmdSource<>() {
        @Override
        public Optional<Repl> getFocusedRepl() {
            return Repl.getFocusedServer();
        }

        @Override
        public void sendSuccess(CommandSourceStack src, Component msg, boolean broadcast) {
            src.sendSuccess(() -> msg, broadcast);
        }

        @Override
        public void sendFailure(CommandSourceStack src, Component msg) {
            src.sendFailure(msg);
        }

        @Override
        public Player getPlayer(CommandSourceStack src) {
            return src.getPlayer();
        };
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher
                        .register(CmdTree.build("jscsrv", SOURCE, Commands.hasPermission(Commands.LEVEL_OWNERS))));
    }
}
