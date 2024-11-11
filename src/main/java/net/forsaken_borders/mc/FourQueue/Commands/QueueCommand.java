package net.forsaken_borders.mc.FourQueue.Commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.forsaken_borders.mc.FourQueue.FourQueue;
import net.forsaken_borders.mc.FourQueue.QueueHolder;
import net.kyori.adventure.text.Component;

import java.util.Optional;
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

        // Grab the queue
        QueueHolder queue = _plugin.getQueue();

        // Check to see if the player is in the main server
        Optional<ServerConnection> optionalServerConnection = player.getCurrentServer();
        UUID uuid = player.getUniqueId();
        if (optionalServerConnection.isPresent() && optionalServerConnection.get().getServer() == _plugin.getMainServer()) {
            queue.removePlayer(uuid);
            player.sendMessage(Component.text("You're already on the main server, you don't need to rejoin the queue!"));
            return;
        }

        // Check to see if the player is in the queue
        int playerPosition = _plugin.getQueue().getPlayerPosition(uuid);

        // Add the player to the queue if they're not in it
        if (playerPosition == -1) {
            playerPosition = _plugin.getQueue().addPlayer(uuid);
        }

        // Let the player know where their spot is in chat instead of the action bar since they explicitly requested it.
        player.sendMessage(Component.text(FourQueue.PositionQueue.formatted(playerPosition, _plugin.getQueue().getTotalPlayerCount())));
    }
}
