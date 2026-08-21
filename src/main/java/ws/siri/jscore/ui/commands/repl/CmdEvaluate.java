package ws.siri.jscore.ui.commands.repl;

import java.io.IOException;
import java.util.UUID;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import ws.siri.jscore.JSCore;
import ws.siri.jscore.runtime.universal.Repl;
import ws.siri.jscore.ui.commands.CmdSource;

/**
 * /jscore eval &lt;script&gt;
 * 
 * Evaluate script content at scope "repo"
 */
public class CmdEvaluate {
    public static <S> LiteralArgumentBuilder<S> tree(CmdSource<S> source) {
        return LiteralArgumentBuilder.<S>literal("eval")
                .then(RequiredArgumentBuilder.<S, String>argument("expression", StringArgumentType.greedyString())
                        .executes(context -> evaluate(source, context)));
    }

    public static <S> int evaluate(CmdSource<S> source, CommandContext<S> context) {
        S src = context.getSource();
        String expression = context.getArgument("expression", String.class);
        Player player = source.getPlayer(src);
        Repl repl = source.getFocusedRepl();
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        JSCore.LOGGER
                .info(String.format("[%s] Started %s > %s : %s", uuid, repl.getName(), player.getName(), expression));

        source.sendSuccess(context.getSource(),
                Component.literal(String.format("> %s", expression)).withStyle(ChatFormatting.GREEN), false);

        // TODO: all these logging goes to the ui/logger
        try {
            Value res = repl.evaluate(expression);
            JSCore.LOGGER.info(String.format("[%s] Resolved : %s", uuid, expression));
            source.sendSuccess(context.getSource(),
                    Component.literal(String.format("%s", res)).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException e) {
            JSCore.LOGGER.error(String.format("[%s] Error (IOException) : %s", uuid, e));
            source.sendFailure(context.getSource(), Component
                    .literal(String.format("An IOException occured: %s\nSee logs for details.", e)));
        } catch (PolyglotException e) {
            Throwable unwrapped = e;

            if (e.isHostException())
                unwrapped = e.asHostException();

            JSCore.LOGGER.error(String.format("[%s] Error (PolyglotException) : %s", uuid, unwrapped));
            source.sendFailure(context.getSource(), Component
                    .literal(String.format("A PolyglotException occured: %s\nSee logs for details.",
                            unwrapped.getMessage())));
        } catch (Exception e) {
            JSCore.LOGGER.error(String.format("[%s] Error (unspecified Exception) : %s", uuid, e));
            source.sendFailure(context.getSource(), Component
                    .literal(String.format("An unspecified Exception occured: %s\nSee logs for details.",
                            e.getMessage())));
        }

        return 0;
    }
}
