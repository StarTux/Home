package com.cavetale.home;

import com.winthier.playercache.PlayerCache;
import com.winthier.playerinfo.PlayerInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

@RequiredArgsConstructor
public final class HomeAdminCommand implements TabExecutor {
    private final HomePlugin plugin;
    static final List<String> COMMANDS = Arrays
        .asList("claims", "homes", "ignore", "reload", "debug", "giveclaimblocks", "tp",
                "deleteclaim", "adminclaim", "transferclaim", "claiminfo", "findold");

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
                if (plugin.doesIgnoreClaims(player)) {
                    plugin.ignoreClaims(player, false);
                    player.sendMessage(Component.text("Respecting home and claim permissions", NamedTextColor.YELLOW));
                } else {
                    plugin.ignoreClaims(player, true);
                    player.sendMessage(Component.text("Ignoring home and claim permissions", NamedTextColor.YELLOW));
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
            if (args.length >= 2) {
                String worldName = args[1];
                plugin.getClaimCache().debug(sender, worldName);
            } else {
                sender.sendMessage("Nothing to show!");
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
                    player.sendMessage(Component.text("No claim here.", NamedTextColor.RED));
                    return true;
                }
                int blocks;
                try {
                    blocks = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    player.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
                    return true;
                }
                int newblocks = claim.getBlocks() + blocks;
                if (newblocks < 0) newblocks = 0;
                claim.setBlocks(newblocks);
                claim.saveToDatabase();
                player.sendMessage(Component.text("Claim owned by " + claim.getOwnerName()
                                                  + " now has " + newblocks + " claim blocks.",
                                                  NamedTextColor.YELLOW));
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
                    player.sendMessage(Component.text("No claim here.", NamedTextColor.RED));
                    return true;
                }
                plugin.deleteClaim(claim);
                player.sendMessage(Component.text("Deleted claim owned by " + claim.getOwnerName(),
                                                  NamedTextColor.YELLOW));
                return true;
            }
            break;
        case "adminclaim": {
            if (args.length != 1 || player == null) return false;
            Location loc = player.getLocation();
            String playerWorld = loc.getWorld().getName();
            final String w = plugin.mirrorWorlds.getOrDefault(playerWorld, playerWorld);
            Area area = new Area(loc.getBlockX() - 31, loc.getBlockZ() - 31,
                                 loc.getBlockX() + 32, loc.getBlockZ() + 32);
            for (Claim other : plugin.getClaimCache().within(w, area)) {
                if (other.area.contains(area)) {
                    sender.sendMessage(Component.text("This claim would overlap an existing claim owned by "
                                                      + other.getOwnerName() + ".",
                                                      NamedTextColor.RED));
                    return true;
                }
            }
            Claim claim = new Claim(plugin, Claim.ADMIN_ID, w, area);
            plugin.getClaimCache().add(claim);
            claim.saveToDatabase();
            sender.sendMessage(Component.text("Admin claim created", NamedTextColor.YELLOW));
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
                    sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                    return true;
                }
            }
            Claim claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                sender.sendMessage(Component.text("There is no claim here.", NamedTextColor.RED));
                return true;
            }
            claim.setOwner(targetId);
            claim.saveToDatabase();
            sender.sendMessage(Component.text("Claim transferred to "
                                              + claim.getOwnerName() + ".",
                                              NamedTextColor.YELLOW));
            return true;
        }
        case "claiminfo": {
            if (args.length != 1 || player == null) return false;
            Claim claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                sender.sendMessage(Component.text("No claim here.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage("" + claim);
            return true;
        }
        case "findold": return findOld(sender, Arrays.copyOfRange(args, 1, args.length));
        case "tp": return tp(sender, Arrays.copyOfRange(args, 1, args.length));
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
        if (args.length != 1) return false;
        String name = args[0];
        UUID uuid = PlayerCache.uuidForName(name);
        if (uuid == null) {
            sender.sendMessage(Component.text("Player not found: " + name, NamedTextColor.RED));
            return true;
        }
        name = PlayerCache.nameForUuid(uuid);
        List<Claim> claims = plugin.findClaims(uuid);
        int count = claims.size();
        sender.sendMessage(Component.text(name + " has " + count
                                          + (count == 1 ? " claim:" : " claims:"),
                                          NamedTextColor.YELLOW));
        int id = 0;
        for (Claim claim : claims) {
            String brief = "-"
                + "id=" + claim.id
                + (" loc=" + claim.world + ":"
                   + claim.area.centerX() + "," + claim.area.centerY())
                + " blocks=" + claim.blocks;
            sender.sendMessage(Component.text(brief, NamedTextColor.YELLOW));
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
            sender.sendMessage(Component.text("Player not found: " + name, NamedTextColor.RED));
            return true;
        }
        name = PlayerCache.nameForUuid(uuid);
        List<Home> homes = plugin.findHomes(uuid);
        int count = homes.size();
        sender.sendMessage(Component.text(name + " has " + count
                                          + (count == 1 ? " home:" : " homes:"),
                                          NamedTextColor.YELLOW));
        for (Home home : homes) {
            String brief = "-"
                + "id=" + home.id
                + " name=" + (home.name != null ? home.name : "-")
                + (" loc=" + home.world + ":"
                   + blk(home.x) + "," + blk(home.y) + "," + blk(home.z))
                + " public=" + (home.publicName != null
                                 ? home.publicName : "-");
            sender.sendMessage(Component.text(brief, NamedTextColor.YELLOW));
        }
        return true;
    }

    @Value
    protected final class OldClaim {
        protected final Claim claim;
        protected final Date lastSeen;
    }

    protected boolean findOld(CommandSender sender, String[] args) {
        int days = args.length >= 1
            ? Integer.parseInt(args[0])
            : 90;
        long now = System.currentTimeMillis();
        long then = now - Duration.ofDays(days).toMillis();
        Map<String, Integer> perWorldClaimCount = new HashMap<>();
        Map<String, Integer> perWorldTotal = new HashMap<>();
        List<OldClaim> oldClaims = new ArrayList<>();
        int[] count = new int[1];
        for (Claim claim : plugin.getClaimCache().getAllClaims()) {
            perWorldClaimCount.compute(claim.getWorld(), (w, i) -> i != null ? i + 1 : 1);
            if (claim.getOwner() == null || claim.isAdminClaim()) continue;
            if (claim.getCreated() > then) continue;
            int initialSize = plugin.getWorldSettings().get(claim.getWorld()).initialClaimSize;
            if (claim.getArea().width() > initialSize) continue;
            if (claim.getArea().height() > initialSize) continue;
            count[0] += 1;
            PlayerInfo.getInstance().lastLog(claim.getOwner(), date -> {
                    if (date.getTime() <= then) {
                        oldClaims.add(new OldClaim(claim, date));
                        perWorldTotal.compute(claim.getWorld(), (w, i) -> i != null ? i + 1 : 1);
                    }
                    count[0] -= 1;
                    if (count[0] == 0) {
                        int total = 0;
                        Collections.sort(oldClaims, (a, b) -> a.lastSeen.compareTo(b.lastSeen));
                        for (OldClaim oldClaim : oldClaims) {
                            sender.sendMessage("Claim " + oldClaim.claim.getId()
                                               + " at " + oldClaim.claim.getWorld()
                                               + "," + oldClaim.claim.getArea().centerX()
                                               + "," + oldClaim.claim.getArea().centerY()
                                               + " owner " + oldClaim.claim.getOwnerName()
                                               + ", last seen " + oldClaim.lastSeen);
                        }
                        sender.sendMessage("Claims older than " + days + " days:");
                        for (Map.Entry<String, Integer> entry : perWorldTotal.entrySet()) {
                            String worldName = entry.getKey();
                            int worldTotal = entry.getValue();
                            int worldClaimCount = perWorldClaimCount.getOrDefault(worldName, 0);
                            int percentage = worldClaimCount > 0
                                ? (worldTotal * 100 - 1) / worldClaimCount + 1
                                : 100;
                            sender.sendMessage(worldName + ": "
                                               + worldTotal + "/" + worldClaimCount
                                               + " (" + percentage + "%)");
                            total += worldTotal;
                        }
                        sender.sendMessage("Total: " + total);
                    }
                });
        }
        return true;
    }

    protected boolean tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[homeadmin:tp] player expected");
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) return false;
        int claimId;
        try {
            claimId = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            return false;
        }
        Claim claim = plugin.findClaimWithId(claimId);
        if (claim == null) {
            player.sendMessage(Component.text("Claim not found: " + claimId, NamedTextColor.RED));
            return true;
        }
        final World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return true;
        final int x = claim.centerX;
        final int z = claim.centerY;
        world.getChunkAtAsync(x >> 4, z >> 4, (Consumer<Chunk>) chunk -> {
                if (!player.isValid()) return;
                final Location target;
                if (world.getEnvironment() == World.Environment.NETHER) {
                    Block block = world.getBlockAt(x, 1, z);
                    while (!block.isEmpty() || !block.getRelative(0, 1, 0).isEmpty() || !block.getRelative(0, -1, 0).getType().isSolid()) {
                        block = block.getRelative(0, 1, 0);
                    }
                    target = block.getLocation().add(0.5, 0.0, 0.5);
                } else {
                    target = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1.0, 0.5);
                }
                Location ploc = player.getLocation();
                target.setYaw(ploc.getYaw());
                target.setPitch(ploc.getPitch());
                player.teleport(target, TeleportCause.COMMAND);
                player.sendMessage(Component.text("Teleporting to claim " + claim.getId(), NamedTextColor.YELLOW));
            });
        return true;
    }
}
