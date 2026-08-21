package ws.siri.jscore.ui.commands;

import java.util.function.Predicate;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.server.permissions.PermissionSetSupplier;
import ws.siri.jscore.ui.commands.repl.CmdReplTree;

public class CmdTree {
    public static <S extends PermissionSetSupplier> LiteralArgumentBuilder<S> build(String name, CmdSource<S> source,
            Predicate<S> requires) {
        return LiteralArgumentBuilder.<S>literal(name)
                .requires(requires)
                .then(CmdReplTree.build(source));
    }
}
