package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.RemotePlayer;

/**
 * This is really the "/wild" command.
 */
public final class BuildCommand extends AbstractCommand<HomePlugin> {
    protected BuildCommand(final HomePlugin plugin) {
        super(plugin, "wild");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Find a place to build")
            .denyTabCompletion()
            .remotePlayerCaller(this::wild);
    }

    protected boolean wild(RemotePlayer player, String[] args) {
        if (args.length != 0) return false;
        plugin.findPlaceToBuild(player);
        return true;
    }
}
