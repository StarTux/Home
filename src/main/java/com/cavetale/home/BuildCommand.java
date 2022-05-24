package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.event.player.PluginPlayerEvent;

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
        if (!plugin.doesIgnoreClaims(player.getUniqueId())) {
            if (plugin.hasAClaim(player.getUniqueId())) {
                if (player.isPlayer()) {
                    PluginPlayerEvent.Name.USE_WILD_WITH_CLAIM.call(plugin, player.getPlayer());
                }
                throw new CommandWarn("You already have a claim!");
            }
        }
        plugin.findPlaceToBuild(player);
        return true;
    }
}
