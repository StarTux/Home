package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONValue;
import org.spigotmc.event.entity.EntityMountEvent;

@Getter
public final class HomePlugin extends JavaPlugin implements Listener {
    private SQLDatabase db;
    private final List<Claim> claims = new ArrayList<>();
    private final List<Home> homes = new ArrayList<>();
    private final List<String> homeWorlds = new ArrayList<>();
    private String primaryHomeWorld;
    private final Map<String, String> mirrorWorlds = new HashMap<>();
    private int claimMargin = 1024;
    private int homeMargin = 64;
    private int buildCooldown = 10;
    private double claimBlockCost = 1.0;
    private boolean manageGameMode = true;
    private int initialClaimSize = 128;
    private Random random = new Random(System.nanoTime());
    private static final String META_COOLDOWN_WILD = "home.cooldown.wild";
    private static final String META_LOCATION = "home.location";
    private static final String META_NOFALL = "home.nofall";
    private static final String META_BUY = "home.buyclaimblocks";
    private static final String META_ABANDON = "home.abandonclaim";
    private static final String META_IGNORE = "home.ignore";
    private long ticks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class, ClaimTrust.class, Home.class, HomeInvite.class, SQLPlayer.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("homeadmin").setExecutor((s, c, l, a) -> onHomeadminCommand(s, c, l, a));
        getCommand("claim").setExecutor((s, c, l, a) -> onClaimCommand(s, c, l, a));
        getCommand("claim").setTabCompleter((s, c, l, a) -> onClaimTabComplete(s, c, l, a));
        getCommand("home").setExecutor((s, c, l, a) -> onHomeCommand(s, c, l, a));
        getCommand("home").setTabCompleter((s, c, l, a) -> onHomeTabComplete(s, c, l, a));
        getCommand("sethome").setExecutor((s, c, l, a) -> onSethomeCommand(s, c, l, a));
        getCommand("invitehome").setExecutor((s, c, l, a) -> onInviteHomeCommand(s, c, l, a));
        getCommand("homes").setExecutor((s, c, l, a) -> onHomesCommand(s, c, l, a));
        getCommand("homes").setTabCompleter((s, c, l, a) -> onHomesTabComplete(s, c, l, a));
        getCommand("visit").setExecutor((s, c, l, a) -> onVisitCommand(s, c, l, a));
        getCommand("build").setExecutor((s, c, l, a) -> onBuildCommand(s, c, l, a));
        loadFromConfig();
        loadFromDatabase();
        new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        }.runTaskTimer(this, 1, 1);
    }

    @Override
    public void onDisable() {
        claims.clear();
        homes.clear();
    }

    // --- Inner classes for utility

    @Value
    class CachedLocation {
        private final String world;
        private final int x, z;
        private final int claimId;
    }

    @Value
    private static class BuyClaimBlocks {
        int amount;
        double price;
        int claimId;
        String token;
    }

    // --- Ticking

    void onTick() {
        ticks += 1;
        for (Player player: getServer().getOnlinePlayers()) {
            if (player.hasMetadata(META_NOFALL)) {
                if (player.isOnGround() || player.getLocation().getBlock().isLiquid()) {
                    player.removeMetadata(META_NOFALL, this);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                }
            }
            if (!isHomeWorld(player.getWorld())) {
                player.removeMetadata(META_LOCATION, this);
                continue;
            }
            CachedLocation cl1 = (CachedLocation)player.getMetadata(META_LOCATION).stream().filter(a -> a.getOwningPlugin() == this).map(MetadataValue::value).findFirst().orElse(null);
            Location pl = player.getLocation();
            Claim claim = getClaimAt(pl);
            UUID playerId = player.getUniqueId();
            if (claim != null
                && claim.isOwner(playerId)
                && (ticks % 20) == 0
                && claim.getBlocks() > claim.getArea().size()
                && claim.getSetting(Claim.Setting.AUTOGROW) == Boolean.TRUE) {
                autoGrowClaim(claim);
            }
            if (cl1 == null || !cl1.world.equals(pl.getWorld().getName()) || cl1.x != pl.getBlockX() || cl1.z != pl.getBlockZ()) {
                CachedLocation cl2 = new CachedLocation(pl.getWorld().getName(), pl.getBlockX(), pl.getBlockZ(), claim == null ? -1 : claim.getId());
                if (cl1 == null) cl1 = cl2; // Taking the easy way out
                player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
                if (claim == null) {
                    if (player.getGameMode() != GameMode.ADVENTURE && !player.hasMetadata(META_IGNORE) && !player.isOp()) {
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                    if (cl1.claimId != cl2.claimId) {
                        Claim oldClaim = getClaimById(cl1.claimId);
                        if (oldClaim != null) {
                            if (oldClaim.isOwner(player.getUniqueId())) {
                                Msg.actionBar(player, ChatColor.GRAY, "Leaving your claim");
                            } else {
                                Msg.actionBar(player, ChatColor.GRAY, "Leaving %s's claim", GenericEvents.cachedPlayerName(oldClaim.getOwner()));
                            }
                            highlightClaim(oldClaim, player);
                        }
                    }
                } else {
                    if (claim.isOwner(playerId) || claim.canBuild(playerId)) {
                        if (player.getGameMode() != GameMode.SURVIVAL && !player.hasMetadata(META_IGNORE) && !player.isOp()) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    } else {
                        if (player.getGameMode() != GameMode.ADVENTURE) {
                            player.setGameMode(GameMode.ADVENTURE);
                        }
                    }
                    if (cl1.claimId != cl2.claimId) {
                        if (claim.isOwner(player.getUniqueId())) {
                            Msg.actionBar(player, ChatColor.GRAY, "Entering your claim");
                        } else {
                            Msg.actionBar(player, ChatColor.GRAY, "Entering %s's claim", GenericEvents.cachedPlayerName(claim.getOwner()));
                        }
                        highlightClaim(claim, player);
                    }
                }
            }
        }
    }

    // --- Player interactivity

    boolean onHomeadminCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        Player player = sender instanceof Player ? (Player)sender : null;
        switch (args[0]) {
        case "claims":
            if (args.length == 1) {
                int i = 0;
                for (Claim claim: claims) {
                    sender.sendMessage(i++ + " " + claim);
                }
                return true;
            }
            break;
        case "ignore":
            if (args.length == 1) {
                if (player.hasMetadata(META_IGNORE)) {
                    player.removeMetadata(META_IGNORE, this);
                    Msg.msg(player, ChatColor.YELLOW, "Respecting home and claim permissions");
                } else {
                    player.setMetadata(META_IGNORE, new FixedMetadataValue(this, true));
                    Msg.msg(player, ChatColor.YELLOW, "Ignoring home and claim permissions");
                }
                return true;
            }
            break;
        case "reload":
            if (args.length == 1) {
                loadFromConfig();
                loadFromDatabase();
                sender.sendMessage("Configuration files and databases reloaded");
                return true;
            }
            break;
        case "debug":
            if (args.length == 1) {
                sender.sendMessage("Nofall: " + player.hasMetadata(META_NOFALL));
                sender.sendMessage("Nofall sz: " + player.getMetadata(META_NOFALL).size());
                sender.sendMessage("OnGround: " + player.isOnGround());
                sender.sendMessage("Liquid: " + player.getLocation().getBlock().isLiquid());
                return true;
            }
            break;
        case "giveclaimblocks":
            if (args.length == 2) {
                if (player == null) {
                    sender.sendMessage("Player expected");
                    return true;
                }
                Claim claim = getClaimAt(player.getLocation());
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
                db.save(claim.toSQLRow());
                player.sendMessage(ChatColor.YELLOW + "Claim owner by " + claim.getOwnerName() + " now has " + newblocks + " claim blocks.");
                return true;
            }
            break;
        case "giveallclaimblocks":
            if (args.length == 2) {
                final int blocks;
                try {
                    blocks = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
                    return true;
                }
                for (Claim claim: claims) {
                    int newblocks = Math.max(0, claim.getBlocks() + blocks);
                    claim.setBlocks(newblocks);
                    db.save(claim.toSQLRow());
                }
                sender.sendMessage(ChatColor.YELLOW + "Adjusted all existing claims by " + blocks + " claim blocks.");
                return true;
            }
            break;
        case "deleteclaim":
            if (args.length == 1) {
                if (player == null) {
                    sender.sendMessage("Player expected");
                    return true;
                }
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "No claim here.");
                    return true;
                }
                claims.remove(claim);
                db.find(Claim.SQLRow.class).eq("id", claim.getId()).delete();
                player.sendMessage(ChatColor.YELLOW + "Deleted claim owned by " + claim.getOwnerName() + ".");
                return true;
            }
            break;
        default:
            break;
        }
        return false;
    }

    boolean onClaimCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected");
            return true;
        }
        final Player player = (Player)sender;
        if (args.length == 0) {
            Claim claim = getClaimAt(player.getLocation());
            if (claim != null) {
                printClaimInfo(player, claim);
            } else {
                listClaims(player);
            }
            return true;
        }
        final UUID playerId = player.getUniqueId();
        switch (args[0]) {
        case "new":
            if (args.length == 1) return onNewclaimCommand(sender, command, alias, new String[0]);
            break;
        case "home":
            if (args.length == 1 || args.length == 2) {
                Claim claim;
                if (args.length == 2) {
                    int claimId;
                    try {
                        claimId = Integer.parseInt(args[1]);
                    } catch (NumberFormatException nfe) {
                        return true;
                    }
                    claim = findClaimWithId(claimId);
                    if (claim == null) return true;
                } else {
                    claim = findPrimaryClaim(playerId);
                    if (claim == null) {
                        player.sendMessage(ChatColor.RED + "You don't have a claim yet.");
                        return true;
                    }
                }
                if (!claim.canVisit(playerId)) return true;
                World world = getServer().getWorld(claim.getWorld());
                if (world == null) return true;
                int x = (claim.getArea().ax + claim.getArea().bx) / 2;
                int z = (claim.getArea().ay + claim.getArea().by) / 2;
                Location target = world.getHighestBlockAt(x, z).getLocation().add(0.5, 0.0, 0.5);
                player.teleport(target);
                player.sendMessage(ChatColor.BLUE + "Teleported to claim.");
                return true;
            }
            break;
        case "buy":
            if (args.length == 2) {
                int buyClaimBlocks;
                try {
                    buyClaimBlocks = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    buyClaimBlocks = -1;
                }
                if (buyClaimBlocks <= 0) {
                    Msg.msg(player, ChatColor.RED, "Invalid claim blocks amount: %s", args[1]);
                    return true;
                }
                Claim claim = findNearestOwnedClaim(player);
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "You don't have a claim in this world");
                    return true;
                }
                double price = (double)buyClaimBlocks * claimBlockCost;
                String priceFormat = GenericEvents.formatMoney(price);
                if (GenericEvents.getPlayerBalance(playerId) < price) {
                    Msg.msg(player, ChatColor.RED, "You do not have %s to buy %d claim blocks", priceFormat, buyClaimBlocks);
                    return true;
                }
                BuyClaimBlocks meta = new BuyClaimBlocks(buyClaimBlocks, price, claim.getId(), "" + (random.nextInt() & 0xFFFF));
                player.setMetadata(META_BUY, new FixedMetadataValue(this, meta));
                Msg.raw(player, "",
                        Msg.label(ChatColor.WHITE, "Click here to confirm this purchase: "),
                        Msg.button(ChatColor.GREEN, "[Buy]", "/claim confirm " + meta.token, "§aConfirm\nBuy " + buyClaimBlocks + " for " + priceFormat + "."));
                return true;
            }
            break;
        case "confirm":
            if (args.length == 2) {
                MetadataValue fmv = player.getMetadata(META_BUY).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
                if (fmv == null) return true;
                BuyClaimBlocks meta = (BuyClaimBlocks)fmv.value();
                player.removeMetadata(META_BUY, this);
                Claim claim = getClaimById(meta.claimId);
                if (claim == null) return true;
                if (!GenericEvents.takePlayerMoney(playerId, meta.price, this, "buy " + meta.amount + " claim blocks")) {
                    Msg.msg(player, ChatColor.RED, "You cannot afford %s", GenericEvents.formatMoney(meta.price));
                    return true;
                }
                claim.setBlocks(claim.getBlocks() + meta.amount);
                db.save(claim.toSQLRow());
                if (claim.getSetting(Claim.Setting.AUTOGROW) == Boolean.TRUE) {
                    Msg.msg(player, ChatColor.WHITE, "Added %d blocks to this claim. It will grow automatically.", meta.amount);
                } else {
                    Msg.msg(player, ChatColor.WHITE, "Added %d blocks to this claim. Grow it manually or enable \"autogrow\" in the settings.", meta.amount);
                }
                return true;
            }
            break;
        case "info":
            if (args.length == 1) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim you want info on");
                    return true;
                }
                printClaimInfo(player, claim);
                return true;
            }
            break;
        case "add":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim to which you want to add members");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    Msg.msg(player, ChatColor.RED, "Player not found: %s", targetName);
                    return true;
                }
                if (claim.canBuild(targetId)) {
                    Msg.msg(player, ChatColor.RED, "Player is already a member of this claim");
                    return true;
                }
                ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.MEMBER, targetId);
                db.save(ct);
                claim.getMembers().add(targetId);
                if (claim.getVisitors().contains(targetId)) {
                    claim.getVisitors().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                }
                Msg.msg(player, ChatColor.WHITE, "Member added: %s", targetName);
                return true;
            }
            break;
        case "invite":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim to which you want to invite people");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    Msg.msg(player, ChatColor.RED, "Player not found: %s", targetName);
                    return true;
                }
                if (claim.canVisit(targetId)) {
                    Msg.msg(player, ChatColor.RED, "Player is already invited to this claim");
                    return true;
                }
                ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.VISIT, targetId);
                db.save(ct);
                claim.getMembers().add(targetId);
                Msg.msg(player, ChatColor.WHITE, "Player invited: %s", targetName);
                return true;
            }
            break;
        case "remove":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim to which you want to invite people");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    Msg.msg(player, ChatColor.RED, "Player not found: %s", targetName);
                    return true;
                }
                if (claim.getMembers().contains(targetId)) {
                    claim.getMembers().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                    Msg.msg(player, ChatColor.YELLOW, "%s may no longer build", targetName);
                } else if (claim.getVisitors().contains(targetId)) {
                    claim.getVisitors().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                    Msg.msg(player, ChatColor.YELLOW, "%s may no longer visit", targetName);
                } else {
                    Msg.msg(player, ChatColor.RED, "%s has no permission in this claim", targetName);
                    return true;
                }
                return true;
            }
            break;
        case "set":
            if (args.length == 1) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim you wish to edit");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "Only the claim owner can do this");
                    return true;
                }
                showClaimSettings(claim, player);
                return true;
            } else if (args.length == 3) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim you wish to edit");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "Only the claim owner can do this");
                    return true;
                }
                Claim.Setting setting;
                try {
                    setting = Claim.Setting.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    Msg.msg(player, ChatColor.RED, "Unknown claim setting: %s", args[1]);
                    return true;
                }
                Object value;
                switch (args[2]) {
                case "on": case "true": case "enabled": value = true; break;
                case "off": case "false": case "disabled": value = false; break;
                default:
                    Msg.msg(player, ChatColor.RED, "Unknown settings value: %s", args[2]);
                    return true;
                }
                if (!value.equals(claim.getSetting(setting))) {
                    if (value.equals(setting.defaultValue)) {
                        claim.getSettings().remove(setting);
                    } else {
                        claim.getSettings().put(setting, value);
                    }
                }
                db.save(claim.toSQLRow());
                showClaimSettings(claim, player);
                return true;
            }
        case "grow":
            if (args.length == 1) {
                Location playerLocation = player.getLocation();
                int x = playerLocation.getBlockX();
                int z = playerLocation.getBlockZ();
                Claim claim = findNearestOwnedClaim(player);
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "You don't have a claim nearby");
                    return true;
                }
                if (claim.getArea().contains(x, z)) {
                    Msg.msg(player, ChatColor.RED, "Stand where you want the claim to grow to");
                    highlightClaim(claim, player);
                    return true;
                }
                Area area = claim.getArea();
                int ax = Math.min(area.ax, x);
                int ay = Math.min(area.ay, z);
                int bx = Math.max(area.bx, x);
                int by = Math.max(area.by, z);
                Area newArea = new Area(ax, ay, bx, by);
                if (claim.getBlocks() < newArea.size()) {
                    Msg.raw(player,
                            Msg.label(ChatColor.RED, "%s more claim blocks required. ", newArea.size() - claim.getBlocks()),
                            Msg.button(ChatColor.GRAY, "[Buy More]", "/claim buy ", Msg.format("§7/claim buy <amount>\n§f§oBuy more claim blocks")));
                    return true;
                }
                for (Claim other: claims) {
                    if (other != claim && other.isInWorld(claim.getWorld()) && other.getArea().overlaps(newArea)) {
                        Msg.msg(player, ChatColor.RED, "Your claim would connect with another claim");
                        return true;
                    }
                }
                claim.setArea(newArea);
                db.save(claim.toSQLRow());
                Msg.msg(player, ChatColor.BLUE, "Grew your claim to where you are standing");
                highlightClaim(claim, player);
                return true;
            }
            break;
        case "shrink":
            if (args.length == 1) {
                Location playerLocation = player.getLocation();
                int x = playerLocation.getBlockX();
                int z = playerLocation.getBlockZ();
                Claim claim = getClaimAt(playerLocation);
                if (claim == null) {
                    Msg.msg(player, ChatColor.RED, "Stand in the claim you wish to shrink");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    Msg.msg(player, ChatColor.RED, "You can only shrink your own claims");
                    return true;
                }
                Area area = claim.getArea();
                int ax = area.ax;
                int ay = area.ay;
                int bx = area.bx;
                int by = area.by;
                if (Math.abs(ax - x) < Math.abs(bx - x)) { // Closer to western edge
                    ax = x;
                } else {
                    bx = x;
                }
                if (Math.abs(ay - z) < Math.abs(by - z)) {
                    ay = z;
                } else {
                    by = z;
                }
                Area newArea = new Area(ax, ay, bx, by);
                claim.setArea(newArea);
                db.save(claim.toSQLRow());
                Msg.msg(player, ChatColor.BLUE, "Shrunk your claim to where you are standing");
                highlightClaim(claim, player);
                return true;
            }
            break;
        case "abandon":
            if (args.length == 1) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "There is no claim here.");
                    return true;
                }
            }
            break;
        default:
            break;
        }
        listClaims(player);
        return true;
    }

    void frame(ComponentBuilder cb, String text) {
        ChatColor fc = ChatColor.BLUE;
        cb.append("            ").color(fc).strikethrough(true);
        cb.append("[ ").color(fc).strikethrough(false);
        cb.append(text).color(ChatColor.WHITE).bold(true);
        cb.append(" ]").color(fc).bold(false);
        cb.append("            ").color(fc).strikethrough(true);
        cb.append("").strikethrough(false);
    }

    void printClaimInfo(Player player, Claim claim) {
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Info");
        player.spigot().sendMessage(cb.create());
        player.spigot().sendMessage(new ComponentBuilder("").append("Owner ").color(ChatColor.GRAY).append(claim.getOwnerName()).color(ChatColor.WHITE).create());
        int x = claim.getArea().ax + claim.getArea().bx / 2;
        int z = claim.getArea().ay + claim.getArea().by / 2;
        player.spigot().sendMessage(new ComponentBuilder("").append("Location ").color(ChatColor.GRAY)
                                    .append(worldDisplayName(claim.getWorld()) + " " + x).color(ChatColor.WHITE)
                                    .append(",").color(ChatColor.GRAY)
                                    .append("" + z).color(ChatColor.WHITE)
                                    .create());
        player.spigot().sendMessage(new ComponentBuilder("").append("Size ").color(ChatColor.GRAY)
                                    .append("" + claim.getArea().width()).color(ChatColor.WHITE)
                                    .append("x").color(ChatColor.GRAY)
                                    .append("" + claim.getArea().height()).color(ChatColor.WHITE)
                                    .append(" => ").color(ChatColor.GRAY)
                                    .append("" + claim.getArea().size()).color(ChatColor.WHITE)
                                    .append(" / ").color(ChatColor.GRAY)
                                    .append("" + claim.getBlocks()).color(ChatColor.WHITE)
                                    .append(" blocks").color(ChatColor.GRAY)
                                    .create());
        // Members and Visitors
        for (int i = 0; i < 2; i += 1) {
            List<UUID> ids = null;
            String key = null;
            switch (i) {
            case 0: key = "Members"; ids = claim.getMembers(); break;
            case 1: key = "Visitors"; ids = claim.getVisitors(); break;
            default: continue;
            }
            if (ids.isEmpty()) continue;
            cb = new ComponentBuilder("");
            cb.append(key).color(ChatColor.GRAY);
            for (UUID id: ids) {
                cb.append(" ");
                cb.append(GenericEvents.cachedPlayerName(id)).color(ChatColor.WHITE);
            }
            player.spigot().sendMessage(cb.create());
        }
        // Settings
        cb = new ComponentBuilder("");
        cb.append("Settings").color(ChatColor.GRAY);
        for (Claim.Setting setting: Claim.Setting.values()) {
            Object value = claim.getSetting(setting);
            if (value == null) continue;
            cb.append(" ");
            cb.append(setting.name().toLowerCase()).color(ChatColor.WHITE);
            cb.append("=").color(ChatColor.GRAY);
            String valueString;
            ChatColor valueColor;
            if (value == Boolean.TRUE) {
                cb.append("on").color(ChatColor.BLUE);
            } else if (value == Boolean.FALSE) {
                cb.append("off").color(ChatColor.RED);
            } else {
                valueColor = ChatColor.GRAY; valueString = value.toString();
                cb.append(value.toString()).color(ChatColor.GRAY);
            }
        }
        player.spigot().sendMessage(cb.create());
        // Buttons
        Location playerLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        cb = new ComponentBuilder("");
        cb.append("General").color(ChatColor.GRAY);
        cb.append("  ").append("[Info]").color(ChatColor.YELLOW)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim info"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/claim info\n" + ChatColor.WHITE + ChatColor.ITALIC + "Get claim info.")));
        if (claim.canVisit(playerId)) {
            cb.append("  ").append("[Home]").color(ChatColor.BLUE)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim home " + claim.getId()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + "Teleport to this claim.")));
        }
        cb.append("  ").append("[List]").color(ChatColor.GOLD)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim list" + claim.getId()))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/claim list\n" + ChatColor.WHITE + ChatColor.ITALIC + "List all your claims.")));
        player.spigot().sendMessage(cb.create());
        if (claim.isOwner(playerId) && claim.contains(playerLocation)) {
            cb = new ComponentBuilder("");
            cb.append("Manage").color(ChatColor.GRAY);
            cb.append("  ").append("[Buy]").color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim buy "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/claim buy " + ChatColor.ITALIC + "AMOUNT\n" + ChatColor.WHITE + ChatColor.ITALIC + "Add some claim blocks to this claim. One claim block costs " + GenericEvents.formatMoney(claimBlockCost) + ".")));
            cb.append("  ").append("[Settings]").color(ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim set"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/claim set\n" + ChatColor.WHITE + ChatColor.ITALIC + "View or change claim settings.")));
            cb.append("  ").append("[Grow]").color(ChatColor.GRAY)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim grow"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "/claim grow\n" + ChatColor.WHITE + ChatColor.ITALIC + "Grow this claim to your current location.")));
            cb.append("  ").append("[Shrink]").color(ChatColor.DARK_GRAY)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim shrink"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.DARK_GRAY + "/claim shrink\n" + ChatColor.WHITE + ChatColor.ITALIC + "Reduce this claim's size so that the nearest corner snaps to your current location.")));
            player.spigot().sendMessage(cb.create());
            cb = new ComponentBuilder("");
            cb.append("Friends").color(ChatColor.GRAY);
            cb.append("  ").append("[Add]").color(ChatColor.BLUE)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim add "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + "/claim add " + ChatColor.ITALIC + "PLAYER\n" + ChatColor.WHITE + ChatColor.ITALIC + "Trust some player to build in this claim.")));
            cb.append("  ").append("[Invite]").color(ChatColor.AQUA)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim invite "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.AQUA + "/claim invite " + ChatColor.ITALIC + "PLAYER\n" + ChatColor.WHITE + ChatColor.ITALIC + "Trust some player to visit your claim. They will be able to open doors and such.")));
            cb.append("  ").append("[Remove]").color(ChatColor.RED)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim remove "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "/claim remove " + ChatColor.ITALIC + "PLAYER\n" + ChatColor.WHITE + ChatColor.ITALIC + "Remove someone's trust from this claim.")));
            player.spigot().sendMessage(cb.create());
        }
        player.sendMessage("");
        highlightClaim(claim, player);
    }

    void listClaims(Player player) {
        UUID playerId = player.getUniqueId();
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim List");
        player.spigot().sendMessage(cb.create());
        List<Claim> playerClaims = findClaims(playerId);
        ChatColor[] colors = {ChatColor.BLUE, ChatColor.GREEN, ChatColor.GOLD, ChatColor.AQUA};
        int ci = 0;
        if (playerClaims.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You don't have any claims of your own.");
        } else {
            cb = new ComponentBuilder("");
            cb.append("Owned").color(ChatColor.GRAY);
            for (Claim claim: playerClaims) {
                cb.append("  ");
                ChatColor color = colors[ci++];
                cb.append("[" + worldDisplayName(claim.getWorld()) + "]").color(color)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim home " + claim.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color + "Visit your claim in " + worldDisplayName(claim.getWorld()))));
            }
            player.spigot().sendMessage(cb.create());
        }
        playerClaims.clear();
        for (Claim claim: claims) {
            if (!claim.isOwner(playerId) && claim.canVisit(playerId)) playerClaims.add(claim);
        }
        if (!playerClaims.isEmpty()) {
            cb = new ComponentBuilder("");
            cb.append("Owned").color(ChatColor.GRAY);
            for (Claim claim: playerClaims) {
                cb.append("  ");
                ChatColor color = colors[ci++];
                cb.append("[" + claim.getOwnerName() + "]").color(color)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim home " + claim.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color + "Visit this claim owned by " + claim.getOwnerName())));
            }
            player.spigot().sendMessage(cb.create());
        }
        if (findClaimsInWorld(playerId, player.getWorld().getName()).isEmpty()) {
            for (Claim claim: playerClaims) {
                cb = new ComponentBuilder("");
                cb.append("Make one ").color(ChatColor.GRAY);
                cb.append("  ").append("[New]").color(ChatColor.GOLD)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim new" + claim.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/claim new\n" + ChatColor.WHITE + ChatColor.ITALIC + "Attempt to make a claim right here.")));
                player.spigot().sendMessage(cb.create());
            }
        }
        player.sendMessage("");
    }

    boolean onNewclaimCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length != 0) return false;
        Player player = (Player)sender;
        boolean isHomeWorld = false;
        boolean isHomeEndWorld = false;
        World playerWorld = player.getWorld();
        String playerWorldName = playerWorld.getName();
        if (!homeWorlds.contains(playerWorldName)) {
            Msg.msg(player, ChatColor.RED, "You cannot make a claim in this world");
            return true;
        }
        if (mirrorWorlds.containsKey(playerWorldName)) playerWorldName = mirrorWorlds.get(playerWorldName);
        // Check for other claims
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        for (Claim claim: claims) {
            if (claim.isInWorld(playerWorldName)) {
                if (claim.getOwner().equals(playerId)) {
                    Msg.msg(player, ChatColor.RED, "You already have a claim in this world");
                    return true;
                }
                // Check claim distance
                if (claim.getArea().isWithin(x, y, claimMargin)) {
                    Msg.msg(player, ChatColor.RED, "You are too close to another claim");
                    return true;
                }
            }
        }
        // Create the claim
        int rad = initialClaimSize / 2;
        int tol = 0;
        if (rad * 2 == initialClaimSize) tol = 1;
        Area area = new Area(x - rad + tol, y - rad + tol, x + rad, y + rad);
        Claim claim = new Claim(this, playerId, playerWorldName, area);
        claim.saveToDatabase();
        claims.add(claim);
        player.spigot().sendMessage(new ComponentBuilder("")
                                    .append("Claim created! ").color(ChatColor.GREEN)
                                    .append("[View]").color(ChatColor.YELLOW)
                                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim"))
                                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/claim\n" + ChatColor.WHITE + ChatColor.ITALIC + "The command to access all your claims.")))
                                    .create());
        highlightClaim(claim, player);
        return true;
    }

    boolean onHomeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player)sender;
        if (args.length == 0) {
            // Try to find a set home
            Home home = findHome(player.getUniqueId(), null);
            if (home != null) {
                Location location = home.createLocation();
                if (location == null) {
                    Msg.msg(player, ChatColor.RED, "Primary home could not be found");
                    return true;
                }
                player.teleport(location);
                Msg.msg(player, ChatColor.GREEN, "Welcome home :)");
                Msg.title(player, "", "&aWelcome home :)");
                return true;
            }
            // No home was found, so if the player has no claim in the
            // home world, find a place to build.  We do this here so
            // that an existing bed spawn does not prevent someone
            // from using /home as expected.  Either making a claim or
            // setting a home will have caused this function to exit
            // already.
            UUID playerId = player.getUniqueId();
            if (findClaimsInWorld(playerId, primaryHomeWorld).isEmpty()) {
                findPlaceToBuild(player);
                return true;
            }
            // Try the bed spawn next.
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null) {
                Claim claim = getClaimAt(bedSpawn.getBlock());
                if (claim != null && claim.canVisit(playerId)) {
                    player.teleport(bedSpawn.add(0.5, 0.0, 0.5));
                    Msg.msg(player, ChatColor.BLUE, "Welcome to your bed. :)");
                    return true;
                }
            }
            // Try the primary claim in the home world.
            List<Claim> playerClaims = findClaimsInWorld(playerId, primaryHomeWorld);
            // or any claim
            if (playerClaims.isEmpty()) playerClaims = findClaims(playerId);
            if (!playerClaims.isEmpty()) {
                Claim claim = playerClaims.get(0);
                World bworld = getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                Location location = bworld.getHighestBlockAt((area.ax + area.bx) / 2, (area.ay + area.by) / 2).getLocation().add(0.5, 0.0, 0.5);
                player.teleport(location);
                Msg.msg(player, ChatColor.GREEN, "Welcome to your claim. :)");
                highlightClaim(claim, player);
                return true;
            }
            // Give up and default to a random build location, again.
            findPlaceToBuild(player);
            return true;
        }
        if (args.length == 1) {
            Home home;
            String arg = args[0];
            if (arg.contains(":")) {
                String[] toks = arg.split(":", 2);
                UUID targetId = GenericEvents.cachedPlayerUuid(toks[0]);
                if (targetId == null) {
                    Msg.msg(player, ChatColor.RED, "Player not found: %s", toks[0]);
                    return true;
                }
                home = findHome(targetId, toks[1].isEmpty() ? null : toks[1]);
                if (home == null || !home.isInvited(player.getUniqueId())) {
                    Msg.msg(player, ChatColor.RED, "Home not found.");
                    return true;
                }
            } else {
                home = findHome(player.getUniqueId(), arg);
            }
            if (home == null) {
                Msg.msg(player, ChatColor.RED, "Home not found: %s", arg);
                return true;
            }
            Location location = home.createLocation();
            if (location == null) {
                Msg.msg(player, ChatColor.RED, "Home \"%s\" could not be found");
                return true;
            }
            Msg.msg(player, ChatColor.GREEN, "Going home.");
            Msg.title(player, "", "&aGoing home.");
            player.teleport(location);
            return true;
        }
        return false;
    }

    boolean onSethomeCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length > 1) return false;
        Player player = (Player)sender;
        UUID playerId = player.getUniqueId();
        Claim claim = getClaimAt(player.getLocation().getBlock());
        if (!isHomeWorld(player.getWorld())) {
            Msg.msg(player, ChatColor.RED, "You cannot set homes in this world");
            return true;
        }
        if (claim == null) {
            Msg.msg(player, ChatColor.RED, "You can only set homes inside a claim");
            return true;
        }
        if (!claim.canBuild(playerId)) {
            Msg.msg(player, ChatColor.RED, "You cannot set homes in this claim");
            return true;
        }
        String playerWorld = player.getWorld().getName();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();
        String homeName = args.length == 0 ? null : args[0];
        for (Home home: homes) {
            if (home.isOwner(playerId) && home.isInWorld(playerWorld)
                && !home.isNamed(homeName)
                && Math.abs(playerX - (int)home.getX()) < homeMargin
                && Math.abs(playerZ - (int)home.getZ()) < homeMargin) {
                if (home.getName() == null) {
                    Msg.msg(player, ChatColor.RED, "Your primary home is nearby", home.getName());
                } else {
                    Msg.msg(player, ChatColor.RED, "You have a home named \"%s\" nearby", home.getName());
                }
                return true;
            }
        }
        Home home = findHome(playerId, homeName);
        if (home == null) {
            home = new Home(playerId, player.getLocation(), homeName);
            homes.add(home);
        } else {
            home.setLocation(player.getLocation());
        }
        db.save(home);
        if (homeName == null) {
            Msg.msg(player, ChatColor.GREEN, "Primary home set");
        } else {
            Msg.msg(player, ChatColor.GREEN, "Home \"%s\" set", homeName);
        }
        return true;
    }

    boolean onHomesCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        final UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            List<Home> playerHomes = findHomes(playerId);
            if (playerHomes.isEmpty()) {
                Msg.msg(player, ChatColor.YELLOW, "No homes to show");
                return true;
            }
            player.sendMessage("");
            if (playerHomes.size() == 1) {
                Msg.msg(player, ChatColor.BLUE, "You have one home", playerHomes.size());
            } else {
                Msg.msg(player, ChatColor.BLUE, "You have %d homes", playerHomes.size());
            }
            for (Home home: playerHomes) {
                List<Object> json = new ArrayList<>();
                Object nameButton;
                if (home.getName() == null) {
                    nameButton = Msg.button(ChatColor.GRAY, "§oPrimary", "/home", "§9/home\nTeleport to your primary home");
                } else {
                    String cmd = "/home " + home.getName();
                    nameButton = Msg.button(ChatColor.WHITE, home.getName(), cmd, "§9" + cmd + "\nTeleport to home \"" + home.getName() + "\"");
                }
                json.add("");
                json.add(Msg.label(ChatColor.BLUE, " + "));
                json.add(nameButton);
                json.add(" ");
                String infocmd = home.getName() == null ? "/homes info" : "/homes info " + home.getName();
                json.add(Msg.button(ChatColor.GRAY, " (i)", infocmd, "§c" + infocmd + "\n§f§oMore info."));
                if (home.getInvites().size() == 1) json.add(Msg.label(ChatColor.GREEN, " 1 invite"));
                if (home.getInvites().size() > 1) json.add(Msg.label(ChatColor.GREEN, " " + home.getInvites().size() + " invites"));
                if (home.getPublicName() != null) json.add(Msg.label(ChatColor.AQUA, " public"));
                Msg.raw(player, json);
            }
            int homeInvites = (int)homes.stream().filter(h -> h.isInvited(playerId)).count();
            if (homeInvites > 0) {
                ComponentBuilder cb = new ComponentBuilder(" ");
                if (homeInvites == 1) {
                    cb.append("You have one home invite ").color(ChatColor.BLUE);
                } else {
                    cb.append("You have " + homeInvites + " home invites ").color(ChatColor.BLUE);
                }
                cb.append("[List]").color(ChatColor.LIGHT_PURPLE)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/homes invites"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("§d/homes invites\n§f§oList home invites")));
                player.spigot().sendMessage(cb.create());
            }
            Msg.raw(player, Msg.button(ChatColor.BLUE, "[Set]", "/home set ", "§9/homes set [name]\n§f§oSet a home."),
                    "  ", Msg.button(ChatColor.GRAY, "[Info]", "/home info ", "§7/homes info §oHOME\n§f§oGet home info."),
                    "  ", Msg.button(ChatColor.GREEN, "[Invite]", "/home invite ", "§a/homes invite §oPLAYER HOME\n§f§oSet a home."),
                    "  ", Msg.button(ChatColor.AQUA, "[Public]", "/home public ", "§b/homes public §oHOME ALIAS\n§f§oMake home public."),
                    "  ", Msg.button(ChatColor.DARK_AQUA, "[Visit]", "/visit", "§a/homes visit §oHOME\n§f§oVisit a public home."),
                    "  ", Msg.button(ChatColor.RED, "[Delete]", "/home delete ", "§c/homes delete §oHOME\n§f§oDelete home."));
            player.sendMessage("");
            return true;
        }
        switch (args[0]) {
        case "set":
            return onSethomeCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        case "invite":
            if (args.length == 2 || args.length == 3) {
                return onInviteHomeCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
            }
            break;
        case "public":
            if (args.length == 2 || args.length == 3) {
                String homeName = args[1];
                Home home = findHome(playerId, homeName);
                if (home == null) {
                    Msg.msg(player, ChatColor.RED, "Home not found: %s", homeName);
                    return true;
                }
                if (home.getPublicName() != null) {
                    Msg.msg(player, ChatColor.RED, "Home is already public under the alias \"%s\"", home.getPublicName());
                    return true;
                }
                String publicName = args.length >= 3 ? args[2] : home.getName();
                if (publicName == null) {
                    Msg.msg(player, ChatColor.RED, "Please supply a public name for this home");
                    return true;
                }
                if (findPublicHome(publicName) != null) {
                    Msg.msg(player, ChatColor.RED, "A public home by that name already exists. Please supply a different alias.");
                    return true;
                }
                home.setPublicName(publicName);
                db.save(home);
                String cmd = "/visit " + publicName;
                Msg.raw(player, "",
                        Msg.label(ChatColor.WHITE, "Home made public. Players may visit via "),
                        Msg.button(ChatColor.GREEN, cmd, cmd, "§a" + cmd + "\nCan also be found under /visit."));
                return true;
            }
            break;
        case "delete":
            if (args.length == 1 || args.length == 2) {
                String homeName = args.length >= 2 ? args[1] : null;
                Home home = findHome(playerId, homeName);
                if (home == null) {
                    if (homeName == null) {
                        Msg.msg(player, ChatColor.RED, "Your primary home is not set");
                    } else {
                        Msg.msg(player, ChatColor.RED, "You do not have a home named \"%s\"", homeName);
                    }
                    return true;
                }
                db.find(HomeInvite.class).eq("home_id", home.getId()).delete();
                db.delete(home);
                homes.remove(home);
                if (homeName == null) {
                    Msg.msg(player, ChatColor.YELLOW, "Primary home unset. The §o/home§e command will take you to your bed spawn or primary claim.");
                } else {
                    Msg.msg(player, ChatColor.YELLOW, "Home \"%s\" deleted", homeName);
                }
                return true;
            }
            break;
        case "info":
            if (args.length == 1 || args.length == 2) {
                Home home;
                if (args.length == 1) {
                    home = findHome(playerId, null);
                } else {
                    home = findHome(playerId, args[1]);
                }
                if (home == null) {
                    Msg.msg(player, ChatColor.RED, "Home not found.");
                    return true;
                }
                player.sendMessage("");
                if (home.getName() == null) {
                    Msg.msg(player, ChatColor.BLUE, "Primary Home Info");
                } else {
                    Msg.msg(player, ChatColor.BLUE, "Home Info: %s", home.getName());
                }
                StringBuilder sb = new StringBuilder();
                for (UUID inviteId: home.getInvites()) {
                    sb.append(" ").append(GenericEvents.cachedPlayerName(inviteId));
                }
                Msg.msg(player, ChatColor.WHITE, " §7Location: §f%s %d,%d,%d", worldDisplayName(home.getWorld()), (int)Math.floor(home.getX()), (int)Math.floor(home.getY()), (int)Math.floor(home.getZ()));
                Msg.msg(player, ChatColor.WHITE, " §7Invited: §f%s", sb.toString());
                Msg.msg(player, ChatColor.WHITE, " §7Public: §f%s", home.getPublicName() == null ? "yes" : "no");
                player.sendMessage("");
                return true;
            }
            break;
        case "invites":
            if (args.length == 1) {
                ComponentBuilder cb = new ComponentBuilder("Your invites:").color(ChatColor.GRAY);
                for (Home home: homes) {
                    if (home.isInvited(playerId)) {
                        String homename = home.getName() == null ? home.getOwnerName() + ":" : home.getOwnerName() + ":" + home.getName();
                        cb.append(" ");
                        cb.append(homename).color(ChatColor.GREEN)
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + homename))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("§a/home " + homename + "\n§f§oUse this home invite.")));
                    }
                }
                player.spigot().sendMessage(cb.create());
                return true;
            }
            break;
        default:
            break;
        }
        return false;
    }

    boolean onInviteHomeCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length < 1 || args.length > 2) return false;
        final Player player = (Player)sender;
        final UUID playerId = player.getUniqueId();
        String targetName = args[0];
        UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
        if (targetId == null) {
            Msg.msg(player, ChatColor.RED, "Player not found: %s", targetName);
            return true;
        }
        String homeName = args.length >= 2 ? args[1] : null;
        Home home = findHome(playerId, homeName);
        if (home == null) {
            if (homeName == null) {
                Msg.msg(player, ChatColor.RED, "Your primary home is not set");
            } else {
                Msg.msg(player, ChatColor.RED, "You have no home named %s", homeName);
            }
            return true;
        }
        if (!home.invites.contains(targetId)) {
            HomeInvite invite = new HomeInvite(home.getId(), targetId);
            db.save(invite);
            home.invites.add(targetId);
        }
        Msg.msg(player, ChatColor.GREEN, "Invite sent to %s", targetName);
        Player target = getServer().getPlayer(targetId);
        if (target == null) return true;
        if (home.getName() == null) {
            String cmd = "/home " + player.getName() + ":";
            Msg.raw(target, "",
                    Msg.label(ChatColor.WHITE, player.getName() + " invited you to their primary home: "),
                    Msg.button(ChatColor.GREEN, "[Visit]", cmd, "§a" + cmd + "\n§f§oVisit this home"));
        } else {
            String cmd = "/home " + player.getName() + ":" + home.getName();
            Msg.raw(target, "",
                    Msg.label(ChatColor.WHITE, player.getName() + " invited you to their home: "),
                    Msg.button(ChatColor.GREEN, "[" + home.getName() + "]", cmd, "§a" + cmd + "\nVisit this home"));
        }
        return true;
    }

    boolean onVisitCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        if (args.length == 0) {
            List<Home> publicHomes = homes.stream().filter(h -> h.getPublicName() != null).collect(Collectors.toList());
            Msg.msg(player, ChatColor.GREEN, "%d public homes", publicHomes.size());
            for (Home home: publicHomes) {
                String cmd = "/visit " + home.getPublicName();
                Msg.raw(player, "",
                        Msg.label(ChatColor.GREEN, "+ "),
                        Msg.button(ChatColor.WHITE, home.getPublicName(), cmd, "§9" + cmd + "\nVisit this home"),
                        Msg.label(ChatColor.GRAY, " by " + home.getOwnerName()));
            }
            return true;
        }
        Home home = findPublicHome(args[0]);
        if (home == null) {
            Msg.msg(player, ChatColor.RED, "Public home not found: %s", args[0]);
            return true;
        }
        Location location = home.createLocation();
        if (location == null) {
            Msg.msg(player, ChatColor.RED, "Could not take you to this home.");
            return true;
        }
        player.teleport(location);
        Msg.msg(player, ChatColor.GREEN, "Teleported to %s's public home \"%s\"", home.getOwnerName(), home.getPublicName());
        return true;
    }

    boolean onBuildCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        final UUID playerId = player.getUniqueId();
        if (!player.hasMetadata(META_IGNORE)
            && !player.isOp()
            && !findClaimsInWorld(playerId, primaryHomeWorld).isEmpty()) {
            Msg.msg(player, ChatColor.RED, "You already have a claim!");
            return true;
        }
        findPlaceToBuild(player);
        return true;
    }

    public List<String> onHomeTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player)sender;
        UUID playerId = player.getUniqueId();
        List<String> result = new ArrayList<>();
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            for (Home home: homes) {
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

    public List<String> onClaimTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player)sender;
        UUID playerId = player.getUniqueId();
        String cmd = args.length == 0 ? "" : args[0];
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return Arrays.asList("new", "info", "list", "home", "buy", "add", "invite", "remove", "set", "grow", "shrink").stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
        } else if (args.length > 1) {
            switch (cmd) {
            case "new": case "home": case "set": case "grow": case "shrink":
                return Collections.emptyList();
            case "buy":
                if (arg.isEmpty()) return Arrays.asList("10", "100", "1000", "10000");
                try {
                    int amount = Integer.parseInt(arg);
                    return Arrays.asList("" + amount, "" + amount + "0");
                } catch (NumberFormatException nfe) {
                    return Collections.emptyList();
                }
            default:
                return null;
            }
        }
        return null;
    }

    public List<String> onHomesTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (alias.equals("homes") && args.length == 1) {
            return Arrays.asList("set", "invite", "public", "delete", "info", "invites").stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
        }
        return null;
    }

    void findPlaceToBuild(Player player) {
        // Cooldown
        MetadataValue meta = player.getMetadata(META_COOLDOWN_WILD).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
        if (meta != null) {
            long remain = (meta.asLong() - System.nanoTime()) / 1000000000 - (long)buildCooldown;
            if (remain > 0) {
                Msg.msg(player, ChatColor.RED, "Please wait %d more seconds", remain);
                return;
            }
        }
        // Determine center and border
        String worldName = primaryHomeWorld; // Set up for future expansion
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            Msg.msg(player, ChatColor.RED, "Something went wrong. Please contact an administrator.");
            return;
        }
        WorldBorder border = bworld.getWorldBorder();
        Location center = border.getCenter();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int size = (int)Math.min(50000.0, border.getSize()) - claimMargin * 2;
        if (size < 0) return;
        Location location = null;
        // Try 100 times to find a random spot, then give up
        List<Claim> worldClaims = findClaimsInWorld(worldName);
        SAMPLE:
        for (int i = 0; i < 100; i += 1) {
            int x = cx - size / 2 + random.nextInt(size);
            int z = cz - size / 2 + random.nextInt(size);
            for (Claim claim: worldClaims) {
                if (claim.getArea().isWithin(x, z, claimMargin)) {
                    continue SAMPLE;
                }
            }
            location = bworld.getBlockAt(x, 255, z).getLocation().add(0.5, 0.5, 0.5);
            location.setPitch(90.0f);
            location.setYaw((float)Math.random() * 360.0f - 180.0f);
        }
        if (location == null) {
            Msg.msg(player, ChatColor.RED, "Could not find a place to build. Please try again");
            return;
        }
        // Teleport, notify, and set cooldown
        player.teleport(location);
        Msg.raw(player, "",
                Msg.label(ChatColor.WHITE, "Found you a place to build. "),
                Msg.button(ChatColor.GREEN, "[Claim]", "/claim new ", Msg.format("§a/claim new§f§o\nCreate a claim and set a home at this location so you can build and return any time.")),
                Msg.label(ChatColor.WHITE, " it or "),
                Msg.button(ChatColor.YELLOW, "[Retry]", "/home", Msg.format("§a/home§f§o\nFind another random location.")));
        player.setMetadata(META_COOLDOWN_WILD, new FixedMetadataValue(this, System.nanoTime()));
        player.setMetadata(META_NOFALL, new FixedMetadataValue(this, System.nanoTime()));
    }

    void showClaimSettings(Claim claim, Player player) {
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Settings");
        player.spigot().sendMessage(cb.create());
        for (Claim.Setting setting: Claim.Setting.values()) {
            List<Object> json = new ArrayList<>();
            json.add(" ");
            Object value = claim.getSetting(setting);
            String key = setting.name().toLowerCase();
            if (value == Boolean.TRUE) {
                json.add(Msg.label(ChatColor.BLUE, "[ON]"));
                json.add(" ");
                json.add(Msg.button(ChatColor.GRAY, "[OFF]", "/claim set " + key + " off", "Disable " + setting.displayName));
            } else if (value == Boolean.FALSE) {
                json.add(Msg.button(ChatColor.GRAY, "[ON]", "/claim set " + key + " on", "Enable " + setting.displayName));
                json.add(" ");
                json.add(Msg.label(ChatColor.RED, "[OFF]"));
            }
            json.add(Msg.label(ChatColor.WHITE, " %s", setting.displayName));
            Msg.raw(player, json);
        }
        player.sendMessage("");
    }

    void autoGrowClaim(Claim claim) {
        Area area = claim.getArea();
        Area newArea = new Area(area.ax - 1, area.ay - 1, area.bx + 1, area.by + 1);
        if (newArea.size() > claim.getBlocks()) return;
        String claimWorld = claim.getWorld();
        for (Claim other: claims) {
            if (other != claim && other.isInWorld(claimWorld) && other.getArea().overlaps(newArea)) {
                return;
            }
        }
        claim.setArea(newArea);
        db.save(claim.toSQLRow());
    }

    // --- Configuration utility

    void loadFromConfig() {
        reloadConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("Worlds");
        homeWorlds.clear();
        for (String key: section.getKeys(false)) {
            ConfigurationSection worldSection = section.getConfigurationSection(key);
            if (worldSection != null && worldSection.isSet("mirror")) {
                mirrorWorlds.put(key, worldSection.getString("mirror"));
            }
            homeWorlds.add(key);
        }
        if (!homeWorlds.isEmpty()) {
            primaryHomeWorld = homeWorlds.get(0);
        } else {
            primaryHomeWorld = getServer().getWorlds().get(0).getName();
        }
        claimMargin = getConfig().getInt("ClaimMargin");
        homeMargin = getConfig().getInt("HomeMargin");
        buildCooldown = getConfig().getInt("BuildCooldown");
        claimBlockCost = getConfig().getDouble("ClaimBlockCost");
        manageGameMode = getConfig().getBoolean("ManageGameMode");
        initialClaimSize = getConfig().getInt("InitialClaimSize");
    }

    void loadFromDatabase() {
        claims.clear();
        homes.clear();
        for (Claim.SQLRow row: db.find(Claim.SQLRow.class).findList()) {
            Claim claim = new Claim(this);
            claim.loadSQLRow(row);
            claims.add(claim);
        }
        for (ClaimTrust trust: db.find(ClaimTrust.class).findList()) {
            for (Claim claim: claims) {
                if (claim.id.equals(trust.claimId)) {
                    switch (trust.type) {
                    case "visit": claim.visitors.add(trust.trustee); break;
                    case "member": claim.members.add(trust.trustee); break;
                    default: break;
                    }
                    break;
                }
            }
        }
        homes.addAll(db.find(Home.class).findList());
        for (HomeInvite invite: db.find(HomeInvite.class).findList()) {
            for (Home home: homes) {
                if (home.id.equals(invite.homeId)) {
                    home.invites.add(invite.getInvitee());
                    break;
                }
            }
        }
    }

    String worldDisplayName(String worldName) {
        World world = getServer().getWorld(worldName);
        if (world == null) return worldName;;
        switch (world.getEnvironment()) {
        case NORMAL: return "overworld";
        case NETHER: return "nether";
        case THE_END: return "end";
        default: return worldName;
        }
    }

    // --- Claim Utility

    boolean isHomeWorld(World world) {
        return homeWorlds.contains(world.getName());
    }

    Claim getClaimById(int claimId) {
        return claims.stream().filter(c -> c.getId() == claimId).findFirst().orElse(null);
    }

    Claim getClaimAt(Block block) {
        return getClaimAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    Claim getClaimAt(Location location) {
        return getClaimAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    Claim getClaimAt(String w, int x, int y) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream().filter(c -> c.isInWorld(world) && c.getArea().contains(x, y)).findFirst().orElse(null);
    }

    Claim findNearestOwnedClaim(Player player) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        return claims.stream().filter(c -> c.isOwner(playerId) && c.isInWorld(playerWorld)).min((a, b) -> Integer.compare(a.getArea().distanceToPoint(x, z), b.getArea().distanceToPoint(x, z))).orElse(null);
    }

    void highlightClaim(Claim claim, Player player) {
        List<Block> blocks = new ArrayList<>();
        Area area = claim.getArea();
        Location playerLocation = player.getLocation();
        int playerX = playerLocation.getBlockX();
        int playerY = playerLocation.getBlockY();
        int playerZ = playerLocation.getBlockZ();
        World world = player.getWorld();
        for (int x = area.ax; x <= area.bx; x += 1) {
            blocks.add(world.getBlockAt(x, playerY, area.ay));
            blocks.add(world.getBlockAt(x, playerY, area.by));
        }
        for (int z = area.ay; z < area.by; z += 1) {
            blocks.add(world.getBlockAt(area.ax, playerY, z));
            blocks.add(world.getBlockAt(area.bx, playerY, z));
        }
        for (Block block: blocks) {
            while (block.isEmpty()) block = block.getRelative(0, -1, 0);
            while (!block.isEmpty()) block = block.getRelative(0, 1, 0);
            int dx = Math.abs(block.getX() - playerX);
            int dz = Math.abs(block.getZ() - playerZ);
            int dist = dx * dx + dz * dz;
            if (dist > 9216) continue; // 6 * 16, squared
            player.spawnParticle(Particle.BARRIER, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // --- Public home and claim finders

    public List<Home> findHomes(UUID owner) {
        return homes.stream().filter(h -> h.isOwner(owner)).collect(Collectors.toList());
    }

    public Home findHome(UUID owner, String name) {
        return homes.stream().filter(h -> h.isOwner(owner) && h.isNamed(name)).findFirst().orElse(null);
    }

    public Home findPublicHome(String name) {
        return homes.stream().filter(h -> name.equals(h.getPublicName())).findFirst().orElse(null);
    }

    public Claim findPrimaryClaim(UUID owner) {
        for (Claim claim: claims) {
            if (claim.isOwner(owner)) return claim;
        }
        return null;
    }

    public List<Claim> findClaims(UUID owner) {
        return claims.stream().filter(c -> c.isOwner(owner)).collect(Collectors.toList());
    }

    public List<Claim> findClaimsInWorld(UUID owner, String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream().filter(c -> c.isOwner(owner) && c.isInWorld(world)).collect(Collectors.toList());
    }

    public List<Claim> findClaimsInWorld(String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        return claims.stream().filter(c -> c.isInWorld(world)).collect(Collectors.toList());
    }

    public Claim findClaimWithId(int id) {
        for (Claim claim: claims) {
            if (claim.getId() == id) return claim;
        }
        return null;
    }

    // --- Player stored data

    Object getStoredPlayerData(UUID playerId, String key) {
        SQLPlayer row = db.find(SQLPlayer.class).eq("uuid", playerId).findUnique();
        if (row == null) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>)JSONValue.parse(row.getData());
        if (json == null) return null;
        return json.get(key);
    }

    void setStoredPlayerData(UUID playerId, String key, Object value) {
        SQLPlayer row = db.find(SQLPlayer.class).eq("uuid", playerId).findUnique();
        Map<String, Object> json;
        if (row != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tmp = (Map<String, Object>)JSONValue.parse(row.getData());
            if (tmp == null) tmp = new HashMap<>();
            json = tmp;
        } else {
            row = new SQLPlayer(playerId);
            json = new HashMap<>();
        }
        if (value == null) {
            json.remove(key);
        } else {
            json.put(key, value);
        }
        row.setData(JSONValue.toJSONString(json));
        db.save(row);
    }

    // --- Event Handling

    enum Action {
        BUILD,
        INTERACT,
        COMBAT;
    }

    /**
     * Check if a player action is permissible and cancel it if not.
     * If the action is in a world not subject to this plugin, nothing
     * will be cancelled and true returned.
     *
     * @return True if the event is permitted, false otherwise.
     */
    private boolean checkPlayerAction(Player player, Block block, Action action, Cancellable cancellable) {
        if (player.hasMetadata(META_IGNORE) || player.isOp()) return true;
        String w = block.getWorld().getName();
        if (!homeWorlds.contains(w)) return true;
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        // Find claim
        Claim claim = claims.stream().filter(c -> c.isInWorld(world) && c.getArea().contains(block.getX(), block.getZ())).findFirst().orElse(null);
        if (claim == null) {
            // Action is not in a claim.  Apply default permissions.
            // Building is not allowed, combat is.
            switch (action) {
            case BUILD:
                if (cancellable != null) cancellable.setCancelled(true);
                return false;
            case COMBAT:
            case INTERACT:
            default:
                return true;
            }
        }
        // We know there is a claim, so return on the player is
        // privileged here.  The owner and members can do anything.
        UUID uuid = player.getUniqueId();
        if (claim.getOwner().equals(uuid)) return true;
        if (claim.getMembers().contains(uuid)) return true;
        // Visitors may interact and do combat.
        if (claim.canVisit(uuid)) {
            switch (action) {
            case COMBAT:
            case INTERACT:
                return true;
            case BUILD:
            default:
                // Forbidden actions are cancelled further down.
                break;
            }
        }
        // Action is not covered by visitor, member, or owner
        // privilege.  Therefore, nothing is allowed.
        if (cancellable != null) cancellable.setCancelled(true);
        return false;
    }

    /**
     * Utility function to determine whether a player owns an
     * entity. To own an entity, it has to be tameable and the
     * player has to have tamed it. Tamed animals should always be
     * able to be interacted with, or even damaged, by their
     * owner.
     *
     * @arg player the player
     * @arg entity the entity
     * @return true if player owns entity, false otherwise
     */
    static boolean isOwner(Player player, Entity entity) {
        if (!(entity instanceof Tameable)) return false;
        Tameable tameable = (Tameable)entity;
        if (!tameable.isTamed()) return false;
        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return false;
        if (owner.getUniqueId().equals(player.getUniqueId())) return true;
        return false;
    }

    private boolean isHostileMob(Entity entity) {
        switch (entity.getType()) {
        case GHAST:
        case SLIME:
        case PHANTOM:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
            return true;
        default:
            return entity instanceof Monster;
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player)damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile)damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player)projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlock(), Action.BUILD, event);
    }

    // Frost Walker
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        checkPlayerAction((Player)event.getEntity(), event.getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity entity = event.getEntity();
        if (!isHomeWorld(entity.getWorld())) return;
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null && entity instanceof Player) {
            if (player.hasMetadata(META_IGNORE)) return;
            // PvP
            if (player.equals(entity)) return;
            Claim claim = getClaimAt(entity.getLocation().getBlock());
            if (claim == null) {
                // PvP is disabled in claim worlds, outside of claims
                event.setCancelled(true);
                return;
            }
            if (claim.getSetting(Claim.Setting.PVP) == Boolean.TRUE) return;
            event.setCancelled(true);
        } else if (player != null) {
            checkPlayerAction(player, entity.getLocation().getBlock(), isHostileMob(entity) ? Action.COMBAT : Action.BUILD, event);
        } else {
            switch (event.getCause()) {
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                Claim claim = getClaimAt(entity.getLocation().getBlock());
                if (claim == null) {
                    // Explosion damage is disabled in claim worlds,
                    // outside of claims
                    event.setCancelled(true);
                    return;
                }
                if (claim.getSetting(Claim.Setting.EXPLOSIONS) == Boolean.TRUE) return;
                event.setCancelled(true);
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!isHomeWorld(event.getEntity().getWorld())) return;
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        if (damager.hasMetadata(META_IGNORE)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            if (damager.equals(entity)) return;
            Claim claim = getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || claim.getSetting(Claim.Setting.PVP) != Boolean.TRUE) {
                event.setCancelled(true);
            }
            return;
        }
        if (isOwner(damager, entity)) return;
        checkPlayerAction(damager, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = getPlayerDamager(event.getAttacker());
        if (player != null) checkPlayerAction(player, event.getVehicle().getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = getPlayerDamager(event.getAttacker());
        if (player != null) checkPlayerAction(player, event.getVehicle().getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.INTERACT, event);
    }

    // Should this just be the same as onPlayerInteractEntity() ?
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    /**
     * Make sure to whitelist anything that should be caught here
     * in the PlayerInteractEntityEvent.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player)event.getEntity();
        final Entity mount = event.getMount();
        if (isOwner(player, mount)) return;
        checkPlayerAction(player, mount.getLocation().getBlock(), Action.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();
        if (block == null) return;
        // Consider soil trampling
        switch (event.getAction()) {
        case PHYSICAL:
            if (block.getType() == Material.FARMLAND) {
                checkPlayerAction(event.getPlayer(), event.getClickedBlock(), Action.BUILD, event);
            } else {
                checkPlayerAction(event.getPlayer(), event.getClickedBlock(), Action.INTERACT, event);
            }
            return;
        case RIGHT_CLICK_BLOCK:
            checkPlayerAction(player, block, Action.INTERACT, event);
            return;
        case LEFT_CLICK_BLOCK:
            checkPlayerAction(player, block, Action.BUILD, event);
            return;
        default:
            break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!isHomeWorld(event.getEntity().getWorld())) return;
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        checkPlayerAction(event.getPlayer(), event.getEntity().getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            final Player player = (Player)event.getRemover();
            checkPlayerAction(player, event.getEntity().getLocation().getBlock(), Action.BUILD, event);
        }
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            Claim claim = getClaimAt(event.getEntity().getLocation().getBlock());
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isHomeWorld(event.getEntity().getWorld())) return;
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            Claim claim = getClaimAt(iter.next());
            if (claim == null || claim.getSetting(Claim.Setting.EXPLOSIONS) != Boolean.TRUE) {
                iter.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!isHomeWorld(event.getBlock().getWorld())) return;
        Claim claim = getClaimAt(event.getBlock());
        if (claim == null || claim.getSetting(Claim.Setting.FIRE) != Boolean.TRUE) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!isHomeWorld(event.getBlock().getWorld())) return;
        switch (event.getCause()) {
        case ENDER_CRYSTAL:
        case EXPLOSION:
        case FIREBALL:
        case FLINT_AND_STEEL:
            return;
        case LAVA:
        case LIGHTNING:
        case SPREAD:
        default:
            break;
        }
        Claim claim = getClaimAt(event.getBlock());
        if (claim == null || claim.getSetting(Claim.Setting.FIRE) != Boolean.TRUE) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!isHomeWorld(event.getEntity().getWorld())) return;
        Player player = getPlayerDamager(event.getEntity());
        if (player == null) return;
        if (event.getHitBlock() != null) {
            if (!checkPlayerAction(player, event.getHitBlock(), Action.BUILD, null)) {
                event.getEntity().remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!isHomeWorld(event.getEntity().getWorld())) return;
        Player player = getPlayerDamager(event.getEntity());
        if (player != null) {
            checkPlayerAction(player, event.getBlock(), Action.BUILD, event);
        } else if (event.getEntity().getType() == EntityType.ENDERMAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player)event.getPlayer();
        if (!isHomeWorld(player.getWorld())) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;
        if (holder instanceof Entity) {
            if (holder.equals(player)) return;
            if (isOwner(player, (Entity)holder)) return;
            checkPlayerAction(player, ((Entity)holder).getLocation().getBlock(), Action.BUILD, event);
        } else if (holder instanceof BlockState) {
            checkPlayerAction(player, ((BlockState)holder).getBlock(), Action.BUILD, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!isHomeWorld(event.getBlock().getWorld())) return;
        // Piston movement is not allowed:
        // - Outside claims
        // - Crossing claim borders
        Claim claim = getClaimAt(event.getBlock());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        for (Block block: event.getBlocks()) {
            Claim claim2 = getClaimAt(block);
            if (claim != claim2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (!isHomeWorld(event.getBlock().getWorld())) return;
        // Piston movement is not allowed:
        // - Outside claims
        // - Crossing claim borders
        Claim claim = getClaimAt(event.getBlock());
        if (claim == null) {
            event.setCancelled(true);
            return;
        }
        for (Block block: event.getBlocks()) {
            Claim claim2 = getClaimAt(block);
            if (claim != claim2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Cancel fall damage once if it happens because of
            // findPlaceToBuild().
            Player player = (Player)event.getEntity();
            if (player.hasMetadata(META_NOFALL)) {
                player.removeMetadata(META_NOFALL, this);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isHomeWorld(event.getEntity().getLocation().getWorld())) return;
        if (event.getEntity().getType() == EntityType.PHANTOM
            && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }
}
