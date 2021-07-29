package com.cavetale.home;

import com.winthier.playercache.PlayerCache;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class HomeAdminCommand implements TabExecutor {
    private final HomePlugin plugin;
    static final List<String> COMMANDS = Arrays
        .asList("claims", "homes", "ignore", "reload", "debug", "giveclaimblocks",
                "deleteclaim", "adminclaim", "transferclaim", "claiminfo");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        Player player = sender instanceof Player ? (Player) sender : null;
        String[] argl = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
        case "claims": return claimsCommand(sender, argl);
        case "homes": return homesCommand(sender, argl);
        case "ignore":
            if (args.length == 1) {
                if (player.hasMetadata(plugin.META_IGNORE)) {
                    player.removeMetadata(plugin.META_IGNORE, plugin);
                    player.sendMessage(ChatColor.YELLOW + "Respecting home and claim permissions");
                } else {
                    plugin.setMetadata(player, plugin.META_IGNORE, true);
                    player.sendMessage(ChatColor.YELLOW + "Ignoring home and claim permissions");
                }
                return true;
            }
            break;
        case "reload":
            if (args.length == 1) {
                plugin.loadFromConfig();
                plugin.loadFromDatabase();
                sender.sendMessage("Configuration files and databases reloaded");
                return true;
            }
            break;
        case "debug": {
            long hitPercentage = (plugin.getCacheHits() * 100L) / plugin.getCacheLookups();
            sender.sendMessage("Cache hits=" + plugin.getCacheHits() + "(" + hitPercentage + "%)"
                               + " misses=" + plugin.getCacheMisses());
            if (player != null) {
                Claim claim = plugin.getClaimAt(player.getLocation());
                if (claim != null) {
                    player.sendMessage("Claim index: " + plugin.getClaims().indexOf(claim));
                }
            }
            return true;
        }
        case "giveclaimblocks":
            if (args.length == 2) {
                if (player == null) {
                    sender.sendMessage("Player expected");
                    return true;
                }
                Claim claim = plugin.getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "No claim here.");
                    return true;
                }
                int blocks;
                try {
                    blocks = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
                    return true;
                }
                int newblocks = claim.getBlocks() + blocks;
                if (newblocks < 0) newblocks = 0;
                claim.setBlocks(newblocks);
                claim.saveToDatabase();
                player.sendMessage(ChatColor.YELLOW + "Claim owned by " + claim.getOwnerName()
                                   + " now has " + newblocks + " claim blocks.");
                return true;
            }
            break;
        case "deleteclaim":
            if (args.length == 1) {
                if (player == null) {
                    sender.sendMessage("Player expected");
                    return true;
                }
                Claim claim = plugin.getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "No claim here.");
                    return true;
                }
                plugin.deleteClaim(claim);
                player.sendMessage(ChatColor.YELLOW + "Deleted claim owned by "
                                   + claim.getOwnerName() + ".");
                return true;
            }
            break;
        case "adminclaim": {
            if (args.length != 1 || player == null) return false;
            Location loc = player.getLocation();
            Area area = new Area(loc.getBlockX() - 31, loc.getBlockZ() - 31,
                                 loc.getBlockX() + 32, loc.getBlockZ() + 32);
            for (Claim other : plugin.findClaimsInWorld(player.getWorld().getName())) {
                if (other.area.contains(area)) {
                    sender.sendMessage(ChatColor.RED
                                       + "This claim would intersect an existing claim owned by "
                                       + other.getOwnerName() + ".");
                    return true;
                }
            }
            Claim claim = new Claim(plugin, Claim.ADMIN_ID, player.getWorld().getName(), area);
            plugin.getClaims().add(claim);
            claim.saveToDatabase();
            sender.sendMessage(ChatColor.YELLOW + "Admin claim created");
            plugin.highlightClaim(claim, player);
            return true;
        }
        case "transferclaim": {
            if (args.length != 2 || player == null) return false;
            String targetName = args[1];
            UUID targetId;
            if (targetName.equals("-admin")) {
                targetId = Claim.ADMIN_ID;
            } else {
                targetId = PlayerCache.uuidForName(targetName);
                if (targetId == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                    return true;
                }
            }
            Claim claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                sender.sendMessage(ChatColor.RED + "There is no claim here.");
                return true;
            }
            claim.setOwner(targetId);
            claim.saveToDatabase();
            sender.sendMessage(ChatColor.YELLOW + "Claim transferred to "
                               + claim.getOwnerName() + ".");
            return true;
        }
        case "claiminfo": {
            if (args.length != 1 || player == null) return false;
            Claim claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                sender.sendMessage(ChatColor.RED + "No claim here.");
                return true;
            }
            sender.sendMessage("" + claim);
            return true;
        }
        default:
            break;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return COMMANDS.stream()
                                  .filter(arg -> arg.startsWith(args[0]))
                                  .collect(Collectors.toList());
        return null;
    }

    boolean claimsCommand(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 0) {
            int i = 0;
            for (Claim claim : plugin.getClaims()) {
                sender.sendMessage(i++ + " " + claim);
            }
            return true;
        }
        String name = args[0];
        UUID uuid = PlayerCache.uuidForName(name);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + name);
            return true;
        }
        name = PlayerCache.nameForUuid(uuid);
        List<Claim> claims = plugin.findClaims(uuid);
        int count = claims.size();
        sender.sendMessage(ChatColor.YELLOW + name + " has " + count
                           + (count == 1 ? " claim:" : " claims:"));
        int id = 0;
        for (Claim claim : claims) {
            String brief = "-" + ChatColor.YELLOW
                + "id=" + claim.id
                + (" loc=" + claim.world + ":"
                   + claim.area.centerX() + "," + claim.area.centerY())
                + " blocks=" + claim.blocks;
            sender.sendMessage(brief);
        }
        return true;
    }

    private static int blk(double c) {
        return (int) Math.floor(c);
    }

    boolean homesCommand(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        UUID uuid = PlayerCache.uuidForName(name);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + name);
            return true;
        }
        name = PlayerCache.nameForUuid(uuid);
        List<Home> homes = plugin.findHomes(uuid);
        int count = homes.size();
        sender.sendMessage(ChatColor.YELLOW + name + " has " + count
                           + (count == 1 ? " home:" : " homes:"));
        for (Home home : homes) {
            String brief = "-" + ChatColor.YELLOW
                + "id=" + home.id
                + " name=" + (home.name != null ? home.name : "-")
                + (" loc=" + home.world + ":"
                   + blk(home.x) + "," + blk(home.y) + "," + blk(home.z))
                + " public=" + (home.publicName != null
                                 ? home.publicName : "-");
            sender.sendMessage(brief);
        }
        return true;
    }
}
