package ws.siri.jscore.ui.commands;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import ws.siri.jscore.runtime.Repl;

public interface CmdSource<S> {
    /**
     * Get current focused Repl for the client or server
     */
    Repl getFocusedRepl();

    /**
     * Function to call in order to send a success/normal message
     *
     * Note broadcast does nothing on a client command
     */
    void sendSuccess(S src, Component msg, boolean broadcast);

    /**
     * Function to call in order to send a failure message
     */
    void sendFailure(S src, Component msg);

    /**
     * Get player who ran the command
     */
    Player getPlayer(S src);
}
