package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class HomeCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length == 1 && args[0].equals("help")) return false;
        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            // Try to find a set home
            Home home = plugin.findHome(player.getUniqueId(), null);
            if (home != null) {
                Location location = home.createLocation();
                if (location == null) {
                    throw new Wrong("Primary home could not be found.");
                }
                Claim claim = plugin.getClaimAt(location);
                if (claim != null && !claim.hasTrust(player, location, Action.INTERACT)) {
                    throw new Wrong("This home is in a claim where you're not permitted.");
                }
                plugin.warpTo(player, location, () -> {
                        player.sendMessage(ChatColor.GREEN + "Welcome home :)");
                        player.sendTitle("", ChatColor.GREEN + "Welcome home :)", 10, 20, 10);
                    });
                return true;
            }
            // No home was found, so if the player has no claim in the
            // home world, find a place to build.  We do this here so
            // that an existing bed spawn does not prevent someone
            // from using /home as expected.  Either making a claim or
            // setting a home will have caused this function to exit
            // already.
            if (!plugin.hasAClaim(playerId)) {
                plugin.findPlaceToBuild(player);
                return true;
            }
            // Try the primary claim in the home world.
            List<Claim> playerClaims = plugin
                .findClaimsInWorld(playerId, plugin.getPrimaryHomeWorld());
            // or any claim
            if (playerClaims.isEmpty()) playerClaims = plugin.findClaims(playerId);
            if (!playerClaims.isEmpty()) {
                Claim claim = playerClaims.get(0);
                World bworld = plugin.getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                Location location = bworld.getHighestBlockAt((area.ax + area.bx) / 2,
                                                             (area.ay + area.by) / 2)
                    .getLocation().add(0.5, 0.0, 0.5);
                plugin.warpTo(player, location, () -> {
                        player.sendMessage(ChatColor.GREEN + "Welcome to your claim. :)");
                    });
                plugin.highlightClaim(claim, player);
                return true;
            }
            // Give up and default to a random build location, again.
            plugin.findPlaceToBuild(player);
            return true;
        }
        if (args.length == 1) {
            Home home;
            String arg = args[0];
            if (arg.contains(":")) {
                String[] toks = arg.split(":", 2);
                UUID targetId = GenericEvents.cachedPlayerUuid(toks[0]);
                if (targetId == null) {
                    throw new Wrong("Player not found: " + toks[0]);
                }
                home = plugin.findHome(targetId, toks[1].isEmpty() ? null : toks[1]);
                if (home == null) {
                    throw new Wrong("Home not found.");
                }
                if (!player.hasMetadata(plugin.META_IGNORE)
                    && !home.isInvited(player.getUniqueId())) {
                    throw new Wrong("Home not found.");
                }
            } else {
                home = plugin.findHome(player.getUniqueId(), arg);
            }
            if (home == null) {
                throw new Wrong("Home not found: " + arg);
            }
            Location location = home.createLocation();
            if (location == null) {
                throw new Wrong("Home \"%s\" could not be found.");
            }
            Claim claim = plugin.getClaimAt(location);
            if (claim != null && !claim.hasTrust(player, location, Action.INTERACT)) {
                throw new Wrong("This home is in a claim where you're not permitted.");
            }
            plugin.warpTo(player, location, () -> {
                    player.sendMessage(ChatColor.GREEN + "Welcome home.");
                    player.sendTitle("", ChatColor.GREEN + "Welcome home.", 10, 20, 10);
                });
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        List<String> result = new ArrayList<>();
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            for (Home home : plugin.getHomes()) {
                if (home.isOwner(playerId)) {
                    if (home.getName() != null && home.getName().startsWith(arg)) {
                        result.add(home.getName());
                    }
                } else {
                    if (player.hasMetadata(plugin.META_IGNORE) || home.isInvited(playerId)) {
                        String name;
                        if (home.getName() == null) {
                            name = home.getOwnerName() + ":";
                        } else {
                            name = home.getOwnerName() + ":" + home.getName();
                        }
                        if (name.startsWith(arg)) result.add(name);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/home", new String[]{}, "Visit your primary home.");
        commandHelp(player, "/home", new String[]{"<name>"}, "Visit a named home.");
    }
}
