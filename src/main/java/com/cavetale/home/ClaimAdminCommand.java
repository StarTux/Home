package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import com.winthier.playerinfo.PlayerInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class ClaimAdminCommand extends AbstractCommand<HomePlugin> {
    protected ClaimAdminCommand(final HomePlugin plugin) {
        super(plugin, "claimadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("list").arguments("<player>")
            .description("List Player Claims")
            .completers(PlayerCache.NAME_COMPLETER)
            .senderCaller(this::list);
        rootNode.addChild("info").denyTabCompletion()
            .description("Print Claim Info")
            .playerCaller(this::info);
        rootNode.addChild("blocks").arguments("<amount>")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .description("Change Claim Blocks")
            .playerCaller(this::blocks);
        rootNode.addChild("tp").arguments("<id>")
            .description("Teleport to Claim")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .playerCaller(this::tp);
        rootNode.addChild("delete").denyTabCompletion()
            .description("Delete Claim")
            .playerCaller(this::delete);
        rootNode.addChild("createadmin").denyTabCompletion()
            .description("Create Admin Claim")
            .playerCaller(this::createAdmin);
        rootNode.addChild("transfer").arguments("<player>")
            .description("Transfer Claim")
            .playerCaller(this::transfer);
        rootNode.addChild("findold").denyTabCompletion()
            .description("Find Old Claims")
            .senderCaller(this::findOld);
        rootNode.addChild("transferall").arguments("<from> <to>")
            .description("Transfer all of a Player's Claims")
            .completers(PlayerCache.NAME_COMPLETER,
                        PlayerCache.NAME_COMPLETER)
            .senderCaller(this::transferAll);
    }

    private boolean list(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        PlayerCache player = PlayerCache.forName(name);
        if (player == null) throw new CommandWarn("Player not found: " + name);
        List<Claim> claims = plugin.findClaims(player.uuid);
        int count = claims.size();
        sender.sendMessage(text(player.name + " has " + count
                                          + (count == 1 ? " claim:" : " claims:"),
                                          YELLOW));
        int id = 0;
        for (Claim claim : claims) {
            String brief = "-"
                + "id=" + claim.id
                + (" loc=" + claim.world + ":"
                   + claim.area.centerX() + "," + claim.area.centerY())
                + " blocks=" + claim.blocks;
            sender.sendMessage(text(brief, YELLOW));
        }
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) throw new CommandWarn("There is no claim here!");
        player.sendMessage(text("" + claim, AQUA));
        return true;
    }

    private boolean blocks(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) throw new CommandWarn("There is no claim here!");
        int blocks = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        int newblocks = claim.getBlocks() + blocks;
        if (newblocks < 0) newblocks = 0;
        claim.setBlocks(newblocks);
        claim.saveToDatabase();
        player.sendMessage(text("Claim owned by " + claim.getOwnerName()
                                + " now has " + newblocks + " claim blocks.",
                                AQUA));
        return true;
    }

    private boolean tp(Player player, String[] args) {
        if (args.length != 1) return false;
        int claimId = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        Claim claim = plugin.getClaimById(claimId);
        if (claim == null) throw new CommandWarn("Claim not found: " + claimId);
        final World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) throw new CommandWarn("World not found: " + claim.getWorld());
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
                player.sendMessage(text("Teleporting to claim " + claim.getId(), AQUA));
            });
        return true;
    }

    private boolean delete(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) throw new CommandWarn("There is no claim here!");
        plugin.deleteClaim(claim);
        player.sendMessage(text("Deleted claim owned by " + claim.getOwnerName(), AQUA));
        return true;
    }

    private boolean createAdmin(Player player, String[] args) {
        if (args.length != 0) return false;
        Location loc = player.getLocation();
        String playerWorld = loc.getWorld().getName();
        final String w = plugin.mirrorWorlds.getOrDefault(playerWorld, playerWorld);
        Area area = new Area(loc.getBlockX() - 31, loc.getBlockZ() - 31,
                             loc.getBlockX() + 32, loc.getBlockZ() + 32);
        for (Claim other : plugin.getClaimCache().within(w, area)) {
            if (other.area.contains(area)) {
                player.sendMessage(text("This claim would overlap an existing claim owned by "
                                        + other.getOwnerName() + ".",
                                        RED));
                return true;
            }
        }
        Claim claim = new Claim(plugin, Claim.ADMIN_ID, w, area);
        plugin.getClaimCache().add(claim);
        claim.saveToDatabase();
        player.sendMessage(text("Admin claim created", AQUA));
        plugin.highlightClaim(claim, player);
        return true;
    }

    private boolean transfer(Player player, String[] args) {
        if (args.length != 1) return false;
        String targetName = args[0];
        UUID targetId;
        if (targetName.equals("-admin")) {
            targetId = Claim.ADMIN_ID;
        } else {
            targetId = PlayerCache.uuidForName(targetName);
            if (targetId == null) throw new CommandWarn("Player not found: " + targetName);
        }
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) throw new CommandWarn("There is no claim here!");
        claim.setOwner(targetId);
        claim.saveToDatabase();
        player.sendMessage(text("Claim transferred to " + claim.getOwnerName(), AQUA));
        return true;
    }

    @Value
    protected final class OldClaim {
        protected final Claim claim;
        protected final Date lastSeen;
    }

    private boolean findOld(CommandSender sender, String[] args) {
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

    private boolean transferAll(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache from = PlayerCache.forArg(args[0]);
        if (from == null) throw new CommandWarn("Player not found: " + args[0]);
        PlayerCache to = PlayerCache.forArg(args[1]);
        if (to == null) throw new CommandWarn("Player not found: " + args[1]);
        if (from.equals(to)) throw new CommandWarn("Players are identical: " + from.getName());
        int total = 0;
        int claimCount = 0;
        int trustCount = 0;
        int subclaimCount = 0;
        for (Claim claim : plugin.claimCache.getAllClaims()) {
            if (from.uuid.equals(claim.getOwner())) {
                claim.setOwner(to.uuid);
                claim.saveToDatabase();
                claimCount += 1;
                total += 1;
            }
            ClaimTrust trust;
            // Remove `to` to avoid duplicates
            trust = claim.getTrusted().remove(to.uuid);
            if (trust != null) {
                plugin.db.delete(trust);
                total += 1;
            }
            // Convert from->to
            trust = claim.getTrusted().remove(from.uuid);
            if (trust != null) {
                trust.setTrustee(to.uuid);
                claim.getTrusted().put(to.uuid, trust);
                plugin.db.update(trust);
                total += 1;
                trustCount += 1;
            }
            for (Subclaim subclaim : claim.getSubclaims()) {
                Subclaim.Trust otrust = subclaim.getTag().getTrusted().remove(to.uuid);
                Subclaim.Trust strust = subclaim.getTag().getTrusted().remove(from.uuid);
                if (strust != null) {
                    subclaim.getTag().getTrusted().put(to.uuid, strust);
                }
                if (otrust != null || strust != null) {
                    subclaim.saveToDatabase();
                    total += 1;
                    subclaimCount += 1;
                }
            }
        }
        if (total == 0) throw new CommandWarn(from.name + " does not have any claims!");
        sender.sendMessage(text("Transferred " + claimCount + " claims from "
                                + from.name + " to " + to.name
                                + " trust=" + trustCount
                                + " subclaim=" + subclaimCount, AQUA));
        return true;
    }
}
