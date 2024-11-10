package net.forsaken_borders.mc.FourQueue;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.forsaken_borders.mc.FourQueue.Commands.QueueCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Plugin(id = "four-queue", name = "4Queue", version = BuildConstants.VERSION, description = "A Velocity Queue plugin", url = "https://github.com/OoLunar/4Queue", authors = {"OoLunar"})
public final class FourQueue {
    public static final String PositionQueue = "You're currently in position %d of %d";

    private final ScheduledExecutorService _playerMoverService;
    private final FourQueueConfig _config;
    private final ProxyServer _server;
    private final QueueHolder _queue;
    private final Logger _logger;

    private RegisteredServer _mainServer;
    private RegisteredServer _limboServer;

    public FourQueue(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataPath) throws IOException {
        _playerMoverService = new ScheduledThreadPoolExecutor(1);
        _config = FourQueueConfig.Load(dataPath.resolve("config.json").toFile(), logger);
        _server = proxyServer;
        _logger = logger;
        _queue = new QueueHolder();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // See if we can find the main server
        var mainServerOptional = _server.getServer(_config.MainHostname());
        if (mainServerOptional.isEmpty()) {
            _logger.error("The main server was not registered with Velocity: {}", _config.MainHostname());
            return;
        }

        // See if we can find the limbo
        var limboServerOptional = _server.getServer(_config.LimboHostname());
        if (limboServerOptional.isEmpty()) {
            _logger.error("The limbo server was not registered with Velocity: {}", _config.MainHostname());
            return;
        }

        // Set the server fields
        _mainServer = mainServerOptional.get();
        _limboServer = limboServerOptional.get();

        // Unregister the /server command to prevent abuse
        CommandManager commandManager = _server.getCommandManager();
        commandManager.unregister("server");
        _logger.warn("Removed the /server command to prevent abuse. TODO: Add a permissions plugin that disallows that command by default.");

        // Register the /queue command
        commandManager.register(commandManager
                .metaBuilder("queue")
                .aliases("q")
                .plugin(this)
                .build(), new QueueCommand(_queue));
        _logger.info("4Queue has been initialized.");

        // Start moving players
        _playerMoverService.scheduleWithFixedDelay(this::MovePlayersAsync, 0, _config.MovePlayerDelay(), TimeUnit.MILLISECONDS);
    }

    @Subscribe
    @SuppressWarnings("UnstableApiUsage")
    public void onJoin(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        int playerPosition = _queue.addPlayer(player.getUniqueId());
        player.sendMessage(Component.text(PositionQueue.formatted(playerPosition, _queue.getTotalPlayerCount())));
    }

    @Subscribe
    public void onLeave(DisconnectEvent event) {
        _queue.removePlayer(event.getPlayer().getUniqueId());
    }

    private CompletableFuture<Void> MovePlayersAsync() {
        // Attempt to get the next player.
        // If the player is null, then nobody is in limbo.
        Player player = getNextPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Try to see if the main server is online
        try {
            _mainServer.ping().get();
        } catch (Exception error) {
            _logger.warn("Unable to reach the main server: {}", _config.MainHostname(), error);
            return CompletableFuture.completedFuture(null);
        }

        // Try moving the player to the main server
        try {
            player.createConnectionRequest(_mainServer).connect().get();
            _queue.removePlayer(player.getUniqueId());
        } catch (Exception error) {
            _logger.warn("Unable to reach the main server: {}", _config.MainHostname(), error);
            return CompletableFuture.completedFuture(null);
        }

        // If it worked, then keep trying to send more players
        return CompletableFuture.runAsync(this::sendPlayerAsync);
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

    private CompletableFuture<Void> sendPlayerAsync() {
        // Attempt to get the next player.
        // If the player is null, then nobody is in limbo.
        Player player = getNextPlayer();
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            player.createConnectionRequest(_limboServer).connect().get();
            _queue.removePlayer(player.getUniqueId());
            return CompletableFuture.completedFuture(_playerMoverService.schedule(() -> {
            }, _config.MovePlayerDelay(), TimeUnit.MILLISECONDS)).thenRunAsync(this::sendPlayerAsync);
        } catch (Exception error) {
            _logger.info("Unable to send players to the main server: {}", _config.MainHostname(), error);
            return CompletableFuture.completedFuture(null);
        }
    }
}
