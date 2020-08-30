package com.cavetale.home;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class SetHomeCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length > 1) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        UUID playerId = player.getUniqueId();
        if (!plugin.isHomeWorld(player.getWorld())) {
            throw new Wrong("You cannot set homes in this world");
        }
        Claim claim = plugin.getClaimAt(player.getLocation().getBlock());
        if (claim != null && !claim.canBuild(playerId)) {
            throw new Wrong("You cannot set homes in this claim");
        }
        String playerWorld = player.getWorld().getName();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();
        String homeName = args.length == 0 ? null : args[0];
        WorldSettings settings = plugin.getWorldSettings().get(playerWorld);
        for (Home home : plugin.getHomes()) {
            if (home.isOwner(playerId) && home.isInWorld(playerWorld)
                && !home.isNamed(homeName)
                && Math.abs(playerX - (int) home.getX()) < settings.homeMargin
                && Math.abs(playerZ - (int) home.getZ()) < settings.homeMargin) {
                if (home.getName() == null) {
                    throw new Wrong("Your primary home is nearby");
                } else {
                    throw new Wrong("You have a home named \""
                                               + home.getName() + "\" nearby");
                }
            }
        }
        Home home = plugin.findHome(playerId, homeName);
        if (home == null) {
            home = new Home(playerId, player.getLocation(), homeName);
            plugin.getHomes().add(home);
            home.pack();
            plugin.getDb().insertAsync(home, null);
        } else {
            home.setLocation(player.getLocation());
            home.pack();
            plugin.getDb().saveAsync(home, null);
        }
        if (homeName == null) {
            player.sendMessage(ChatColor.GREEN + "Primary home set");
        } else {
            player.sendMessage(ChatColor.GREEN + "Home \"" + homeName + "\" set");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/sethome", new String[]{}, "Set your primary home.");
        commandHelp(player, "/sethome", new String[]{"<name>"}, "Set a named home.");
    }
}
