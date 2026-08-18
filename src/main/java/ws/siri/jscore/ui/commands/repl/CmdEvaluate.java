package ws.siri.jscore.ui.commands.repl;

import java.io.IOException;

import org.graalvm.polyglot.Value;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import ws.siri.jscore.runtime.universal.Repl;

/**
 * /jscore eval &lt;script&gt;
 * 
 * Evaluate script content at scope "repo"
 *
 * TODO: shitty code quality
 */
public class CmdEvaluate {
    public static int evaluate(CommandContext<CommandSourceStack> context) {
        String expression = context.getArgument("expression", String.class);
        Repl repl = Repl.getFocused();

        try {
            Value res = repl.evaluate(expression);
            System.out.println(res);
            context.getSource()
                    .sendSuccess(() -> Component.literal(String.format("> %s\n%s", expression, res.toString())), false);
        } catch (IOException e) {
            System.out.println(e);
        }

        return 1;
    }
}
