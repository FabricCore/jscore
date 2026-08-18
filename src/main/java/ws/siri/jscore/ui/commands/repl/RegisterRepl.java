package ws.siri.jscore.ui.commands.repl;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

public class RegisterRepl {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("repl")
                    .then(Commands.argument("expression", StringArgumentType.greedyString())
                            .executes(CmdEvaluate::evaluate)));
        });
    }
}
