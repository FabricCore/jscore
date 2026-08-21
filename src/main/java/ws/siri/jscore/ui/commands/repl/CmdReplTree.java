package ws.siri.jscore.ui.commands.repl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import ws.siri.jscore.ui.commands.CmdSource;

public class CmdReplTree {
    public static <S> LiteralArgumentBuilder<S> build(CmdSource<S> source) {
        return LiteralArgumentBuilder.<S>literal("repl")
                .then(CmdEvaluate.tree(source));
    }
}
