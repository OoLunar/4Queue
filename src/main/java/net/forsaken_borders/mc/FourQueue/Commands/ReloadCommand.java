package net.forsaken_borders.mc.FourQueue.Commands;

import com.velocitypowered.api.command.SimpleCommand;
import net.forsaken_borders.mc.FourQueue.FourQueue;
import net.kyori.adventure.text.Component;

import java.io.IOException;

public class ReloadCommand implements SimpleCommand {
    private final FourQueue _plugin;

    public ReloadCommand(FourQueue plugin) {
        this._plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        try {
            _plugin.reloadConfig();
            invocation.source().sendMessage(Component.text("Config has been reloaded!"));
        } catch (IOException error) {
            invocation.source().sendMessage(Component.text("Failed to reload the config! %s".formatted(error.getMessage())));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("four-queue.reload") || invocation.source().hasPermission("text.perm.op");
    }
}
