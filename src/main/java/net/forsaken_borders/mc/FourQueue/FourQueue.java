package net.forsaken_borders.mc.FourQueue;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.forsaken_borders.mc.FourQueue.Commands.QueueCommand;
import net.forsaken_borders.mc.FourQueue.Commands.ReloadCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Plugin(id = "four-queue", name = "4Queue", version = BuildConstants.VERSION, description = "A Velocity Queue plugin", url = "https://github.com/OoLunar/4Queue", authors = {"OoLunar"})
public final class FourQueue {
    public static final String PositionQueue = "You're currently in position %d of %d";

    private final ScheduledExecutorService _playerMoverService;
    private final ProxyServer _server;
    private final QueueHolder _queue;
    private final Logger _logger;
    private final Path _dataPath;

    private FourQueueConfig _config;
    private RegisteredServer _mainServer;
    private RegisteredServer _limboServer;

    @Inject
    public FourQueue(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataPath) throws IOException {
        // TODO: Use Velocity's built in scheduler
        _playerMoverService = new ScheduledThreadPoolExecutor(1);
        _server = proxyServer;
        _dataPath = dataPath;
        _logger = logger;

        reloadConfig();
        _queue = new QueueHolder();
    }

    public void reloadConfig() throws IOException {
        _config = FourQueueConfig.Load(_dataPath, _logger);
    }

    public QueueHolder getQueue() {
        return _queue;
    }

    public RegisteredServer getMainServer() {
        return _mainServer;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // See if we can find the main server
        String mainHostname = _config.getMainHostname();
        if (mainHostname == null || mainHostname.isBlank()) {
            _logger.error("The main server hostname is not set in the config");
            return;
        }

        var mainServerOptional = _server.getServer(mainHostname);
        if (mainServerOptional.isEmpty()) {
            _logger.error("The main server was not registered with Velocity: {}", _config.getMainHostname());
            return;
        }

        // See if we can find the limbo server
        String limboHostname = _config.getLimboHostname();
        if (limboHostname == null || limboHostname.isBlank()) {
            _logger.error("The limbo server hostname is not set in the config");
            return;
        }

        var limboServerOptional = _server.getServer(limboHostname);
        if (limboServerOptional.isEmpty()) {
            _logger.error("The limbo server was not registered with Velocity: {}", _config.getMainHostname());
            return;
        }

        // Set the server fields
        _mainServer = mainServerOptional.get();
        _limboServer = limboServerOptional.get();

        // Register the /queue command
        CommandManager commandManager = _server.getCommandManager();
        commandManager.register(commandManager
                .metaBuilder("4queue:queue")
                .aliases("queue", "q")
                .plugin(this)
                .build(), new QueueCommand(this));

        // Register the /4queue reload command
        commandManager.register(commandManager
                .metaBuilder("4queue:reload")
                .plugin(this)
                .build(), new ReloadCommand(this)
        );

        // Unregister the /server command
        commandManager.unregister("server");

        // Start moving players
        _playerMoverService.schedule(this::MovePlayersAsync, _config.getMovePlayerDelay(), TimeUnit.MILLISECONDS);
        _logger.info("4Queue has been initialized.");
    }

    @Subscribe
    @SuppressWarnings("UnstableApiUsage")
    public void onJoin(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.getPreviousServer() != _limboServer || _queue.getPlayerPosition(uuid) == -1) {
            int playerPosition = _queue.addPlayer(uuid);
            player.sendActionBar(Component.text(PositionQueue.formatted(playerPosition, _queue.getTotalPlayerCount())));
            _logger.debug("Player {} ({}) is in position {} of {}", player.getUsername(), uuid, playerPosition, _queue.getTotalPlayerCount());
        }
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        _logger.debug("Player {} ({}) is leaving...", player.getUsername(), uuid);
        _queue.removePlayer(uuid);
    }

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        _logger.debug("Player {} ({}) was kicked from the server", player.getUsername(), uuid);
        _queue.removePlayer(uuid);
    }

    private void MovePlayersAsync() {
        Player player = getNextPlayer();
        if (player == null) {
            // No players in queue, wait X ms for the next attempt
            _logger.debug("No players in queue, waiting {}ms for the next attempt", _config.getMovePlayerDelay());
            _playerMoverService.schedule(this::MovePlayersAsync, _config.getMovePlayerDelay(), TimeUnit.MILLISECONDS);
            return;
        }

        // Try pinging the main server asynchronously
        _mainServer.ping().thenAccept(ping -> {
            // Try to move the player to the main server
            player.createConnectionRequest(_mainServer).connect()
                    .thenAccept((result) -> {
                        if (result.isSuccessful()) {
                            // Schedule for the next player to be moved
                            _queue.removePlayer(player.getUniqueId());
                            _playerMoverService.schedule(this::MovePlayersAsync, _config.getQuickMovePlayerDelay(), TimeUnit.MILLISECONDS);
                        } else {
                            // If the player can't be moved, try again with the normal delay
                            _playerMoverService.schedule(this::MovePlayersAsync, _config.getMovePlayerDelay(), TimeUnit.MILLISECONDS);
                        }

                        // Foreach player in the queue, send them a message with their position
                        int i = 1;
                        for (Iterator<UUID> it = _queue.getAllPlayers(); it.hasNext(); ) {
                            UUID uuid = it.next();
                            Optional<Player> optionalPlayer = _server.getPlayer(uuid);
                            if (optionalPlayer.isEmpty()) {
                                _queue.removePlayer(uuid);
                                continue;
                            }

                            // Test if the player is in the same server as the main server
                            Player queuePlayer = optionalPlayer.get();
                            Optional<ServerConnection> optionalServerConnection = queuePlayer.getCurrentServer();
                            if (optionalServerConnection.isPresent() && optionalServerConnection.get().getServer() == _mainServer) {
                                _queue.removePlayer(queuePlayer.getUniqueId());
                                continue;
                            }

                            queuePlayer.sendActionBar(Component.text(PositionQueue.formatted(i, _queue.getTotalPlayerCount())));
                            i++;
                        }
                    })
                    .exceptionally((error) -> {
                        // If the player can't be moved, try again with the normal delay
                        _playerMoverService.schedule(this::MovePlayersAsync, _config.getMovePlayerDelay(), TimeUnit.MILLISECONDS);
                        return null;
                    });
        }).exceptionally(error -> {
            // If the main server is unreachable, try again with the normal delay
            _logger.warn("Main server unreachable: {}", error.getMessage());
            _playerMoverService.schedule(this::MovePlayersAsync, _config.getMovePlayerDelay(), TimeUnit.MILLISECONDS);
            return null;
        });
    }

    private Player getNextPlayer() {
        while (true) {
            UUID nextPlayer = _queue.peekNextPlayer();
            if (nextPlayer == null) {
                // If nobody is in the queue
                return null;
            }

            // Test to see if the player is still logged in
            Optional<Player> optionalPlayer = _server.getPlayer(nextPlayer);
            if (optionalPlayer.isEmpty()) {
                // Remove the player from the queue
                _queue.removePlayer(nextPlayer);
                continue;
            }

            // We found a player who's still logged in
            return optionalPlayer.get();
        }
    }
}
