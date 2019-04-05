package com.cavetale.home;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class BuildCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) {
        if (args.length != 0) return false;
        final UUID playerId = player.getUniqueId();
        if (!player.hasMetadata(plugin.META_IGNORE)
            && !player.isOp()
            && !plugin.findClaimsInWorld(playerId, plugin.getPrimaryHomeWorld()).isEmpty()) {
            player.sendMessage(ChatColor.RED + "You already have a claim!");
            return true;
        }
        plugin.findPlaceToBuild(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void commandHelp(Player player) {
        player.sendMessage(C_CMD + "/build" + C_DASH + C_DESC + "Find a place to build.");
    }
}
