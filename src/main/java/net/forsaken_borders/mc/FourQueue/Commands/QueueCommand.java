package net.forsaken_borders.mc.FourQueue.Commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.forsaken_borders.mc.FourQueue.FourQueue;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class QueueCommand implements SimpleCommand {
    private final FourQueue _plugin;

    public QueueCommand(FourQueue plugin) {
        this._plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        // Check to see if a player executed the command
        if (!(invocation.source() instanceof Player player)) {
            // Return how many players are in the queue
            invocation.source().sendMessage(Component.text("There's a total of %d players waiting to join the server.".formatted(_plugin.getQueue().getTotalPlayerCount())));
            return;
        }

        // Check to see if the player is in the queue
        UUID uuid = player.getUniqueId();
        int playerPosition = _plugin.getQueue().getPlayerPosition(uuid);

        // Add the player to the queue if they're not in it
        if (playerPosition == -1) {
            playerPosition = _plugin.getQueue().addPlayer(uuid);
        }

        player.sendMessage(Component.text(FourQueue.PositionQueue.formatted(playerPosition, _plugin.getQueue().getTotalPlayerCount())));
    }
}
