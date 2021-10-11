package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent;
import org.bukkit.entity.Player;

public final class BuildCommand extends AbstractCommand<HomePlugin> {
    protected BuildCommand(final HomePlugin plugin) {
        super(plugin, "wild");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Find a place to build")
            .denyTabCompletion()
            .playerCaller(this::wild);
    }

    protected boolean wild(Player player, String[] args) {
        if (args.length != 0) return false;
        if (!plugin.doesIgnoreClaims(player)) {
            if (plugin.hasAClaim(player.getUniqueId())) {
                PluginPlayerEvent.Name.USE_WILD_WITH_CLAIM.call(plugin, player);
                throw new CommandWarn("You already have a claim!");
            }
        }
        plugin.findPlaceToBuild(player);
        return true;
    }
}
