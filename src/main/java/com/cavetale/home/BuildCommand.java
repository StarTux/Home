package com.cavetale.home;

import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class BuildCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length != 0) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        final UUID playerId = player.getUniqueId();
        if (!player.hasMetadata(plugin.META_IGNORE) && !player.isOp() && plugin.hasAClaim(playerId)) {
            player.sendMessage(Component.text("You already have a claim!", NamedTextColor.RED));
            PluginPlayerEvent.Name.USE_WILD_WITH_CLAIM.call(plugin, player);
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
        commandHelp(player, "/build", new String[]{}, "Find a place to build.");
    }
}
