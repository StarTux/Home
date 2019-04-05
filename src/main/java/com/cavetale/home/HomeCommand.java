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
    public boolean onCommand(Player player, String[] args) throws CommandException {
        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            // Try to find a set home
            Home home = plugin.findHome(player.getUniqueId(), null);
            if (home != null) {
                Location location = home.createLocation();
                Claim claim = plugin.getClaimAt(location);
                if (claim == null || !claim.canVisit(playerId)) {
                    throw new CommandException("This home is not claimed by you.");
                }
                if (location == null) {
                    throw new CommandException("Primary home could not be found.");
                }
                player.teleport(location);
                player.sendMessage(ChatColor.GREEN + "Welcome home :)");
                player.sendTitle("", ChatColor.GREEN + "Welcome home :)", 10, 20, 10);
                return true;
            }
            // No home was found, so if the player has no claim in the
            // home world, find a place to build.  We do this here so
            // that an existing bed spawn does not prevent someone
            // from using /home as expected.  Either making a claim or
            // setting a home will have caused this function to exit
            // already.
            if (plugin.findClaimsInWorld(playerId, plugin.getPrimaryHomeWorld()).isEmpty()) {
                plugin.findPlaceToBuild(player);
                return true;
            }
            // Try the bed spawn next.
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null) {
                Claim claim = plugin.getClaimAt(bedSpawn.getBlock());
                if (claim != null && claim.canVisit(playerId)) {
                    player.teleport(bedSpawn.add(0.5, 0.0, 0.5));
                    player.sendMessage(ChatColor.BLUE + "Welcome to your bed. :)");
                    return true;
                }
            }
            // Try the primary claim in the home world.
            List<Claim> playerClaims = plugin.findClaimsInWorld(playerId, plugin.getPrimaryHomeWorld());
            // or any claim
            if (playerClaims.isEmpty()) playerClaims = plugin.findClaims(playerId);
            if (!playerClaims.isEmpty()) {
                Claim claim = playerClaims.get(0);
                World bworld = plugin.getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                Location location = bworld.getHighestBlockAt((area.ax + area.bx) / 2, (area.ay + area.by) / 2).getLocation().add(0.5, 0.0, 0.5);
                player.teleport(location);
                player.sendMessage(ChatColor.GREEN + "Welcome to your claim. :)");
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
                    throw new CommandException("Player not found: " + toks[0]);
                }
                home = plugin.findHome(targetId, toks[1].isEmpty() ? null : toks[1]);
                if (home == null || !home.isInvited(player.getUniqueId())) {
                    throw new CommandException("Home not found.");
                }
            } else {
                home = plugin.findHome(player.getUniqueId(), arg);
            }
            if (home == null) {
                throw new CommandException("Home not found: " + arg);
            }
            Location location = home.createLocation();
            if (location == null) {
                throw new CommandException("Home \"%s\" could not be found.");
            }
            Claim claim = plugin.getClaimAt(location);
            if (claim == null || (home.getOwner() != null && !claim.canVisit(home.getOwner()) && !claim.canVisit(playerId))) {
                throw new CommandException("This home is not claimed.");
            }
            player.sendMessage(ChatColor.GREEN + "Going home.");
            player.sendTitle("", ChatColor.GREEN + "Going home.", 10, 60, 10);
            player.teleport(location);
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
                    if (home.isInvited(playerId)) {
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
        player.sendMessage(C_CMD + "/home" + C_DASH + C_DESC + "Visit your primary home.");
        player.sendMessage(C_CMD + "/home " + C_ARG + "NAME" + C_DASH + C_DESC + "Visit a named home.");
    }
}
