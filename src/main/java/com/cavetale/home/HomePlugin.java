package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Value;
import org.bukkit.ChatColor;
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
import org.spigotmc.event.entity.EntityMountEvent;

@Getter
public final class HomePlugin extends JavaPlugin implements Listener {
    private SQLDatabase db;
    private final List<Claim> claims = new ArrayList<>();
    private final List<Home> homes = new ArrayList<>();
    private String homeWorld, homeNetherWorld, homeTheEndWorld;
    private int claimMargin = 1024;
    private int homeMargin = 64;
    private int buildCooldown = 10;
    private double claimBlockCost = 1.0;
    private Random random = new Random(System.currentTimeMillis());
    private static final String META_COOLDOWN_WILD = "home.cooldown.wild";
    private static final String META_LOCATION = "home.location";
    private static final String META_NOFALL = "home.nofall";
    private static final String META_BUY = "home.buyclaimblocks";
    private static final String META_IGNORE = "home.ignore";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class, ClaimTrust.class, Home.class, HomeInvite.class);
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("homeadmin").setExecutor((s, c, l, a) -> onHomeadminCommand(s, c, l, a));
        getCommand("claim").setExecutor((s, c, l, a) -> onClaimCommand(s, c, l, a));
        getCommand("newclaim").setExecutor((s, c, l, a) -> onNewclaimCommand(s, c, l, a));
        getCommand("home").setExecutor((s, c, l, a) -> onHomeCommand(s, c, l, a));
        getCommand("sethome").setExecutor((s, c, l, a) -> onSethomeCommand(s, c, l, a));
        getCommand("homes").setExecutor((s, c, l, a) -> onHomesCommand(s, c, l, a));
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

    void onTick() {
        for (Player player: getServer().getOnlinePlayers()) {
            if (player.hasMetadata(META_NOFALL)) {
                if (player.isOnGround() || player.getLocation().getBlock().isLiquid()) {
                    player.removeMetadata(META_NOFALL, this);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
                }
            }
            if (player.hasMetadata(META_IGNORE)) continue;
            if (!isHomeWorld(player.getWorld())) {
                player.removeMetadata(META_LOCATION, this);
                continue;
            }
            CachedLocation cl1 = (CachedLocation)player.getMetadata(META_LOCATION).stream().filter(a -> a.getOwningPlugin() == this).map(MetadataValue::value).findFirst().orElse(null);
            Location pl = player.getLocation();
            if (cl1 == null || !cl1.world.equals(pl.getWorld().getName()) || cl1.x != pl.getBlockX() || cl1.z != pl.getBlockZ()) {
                Claim claim = getClaimAt(pl);
                CachedLocation cl2 = new CachedLocation(pl.getWorld().getName(), pl.getBlockX(), pl.getBlockZ(), claim == null ? -1 : claim.getId());
                if (cl1 == null) cl1 = cl2; // Taking the easy way out
                player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
                if (claim == null) {
                    if (player.getGameMode() != GameMode.ADVENTURE && !player.hasMetadata(META_IGNORE)) {
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
                    UUID playerId = player.getUniqueId();
                    if (claim.isOwner(playerId)
                        && claim.getBlocks() > claim.getArea().size()
                        && claim.getSetting(Claim.Setting.AUTOGROW) == Boolean.TRUE
                        && random.nextInt(20) == 0) {
                        autoGrowClaim(claim);
                    }
                    if (claim.isOwner(playerId) || claim.canBuild(playerId)) {
                        if (player.getGameMode() != GameMode.SURVIVAL && !player.hasMetadata(META_IGNORE)) {
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
            }
            break;
        default:
            break;
        }
        return false;
    }

    boolean onClaimCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        final UUID playerId = player.getUniqueId();
        switch (args[0]) {
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
                        Msg.button(ChatColor.GREEN, "[Buy]", "/claim confirm " + meta.token, "&aConfirm\nBuy " + buyClaimBlocks + " for " + priceFormat + "."));
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
                db.save(claim);
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
                Msg.msg(player, ChatColor.GREEN, "Claim info");
                Msg.raw(player,
                        Msg.label(ChatColor.WHITE, "Owner "),
                        Msg.label(ChatColor.GRAY, "%s", GenericEvents.cachedPlayerName(claim.getOwner())));
                Msg.raw(player,
                        Msg.label(ChatColor.WHITE, "Size "),
                        Msg.label(ChatColor.GRAY, "%dx%d total %d", claim.getArea().width(), claim.getArea().height(), claim.getArea().size()));
                // Members and Visitors
                List<Object> ls;
                for (int i = 0; i < 2; i += 1) {
                    List<UUID> ids = null;
                    String key = null;
                    switch (i) {
                    case 0: key = "Members"; ids = claim.getMembers(); break;
                    case 1: key = "Visitors"; ids = claim.getVisitors(); break;
                    default: continue;
                    }
                    if (ids.isEmpty()) continue;
                    ls = new ArrayList<>();
                    ls.add("");
                    ls.add(Msg.label(ChatColor.WHITE, key));
                    for (UUID id: ids) {
                        ls.add(" ");
                        ls.add(Msg.label(ChatColor.GRAY, GenericEvents.cachedPlayerName(id)));
                    }
                    Msg.raw(player, ls);
                }
                // Settings
                ls = new ArrayList<>();
                ls.add("");
                ls.add(Msg.label(ChatColor.WHITE, "Settings"));
                for (Claim.Setting setting: Claim.Setting.values()) {
                    Object value = claim.getSetting(setting);
                    if (value == null) continue;
                    ls.add(Msg.label(ChatColor.GRAY, " %s:", setting.name().toLowerCase()));
                    String valueString;
                    ChatColor valueColor;
                    if (value == Boolean.TRUE) {
                        valueColor = ChatColor.BLUE; valueString = "on";
                    } else if (value == Boolean.FALSE) {
                        valueColor = ChatColor.RED; valueString = "off";
                    } else {
                        valueColor = ChatColor.GRAY; valueString = value.toString();
                    }
                    ls.add(Msg.label(valueColor, valueString));
                }
                Msg.raw(player, ls);
                highlightClaim(claim, player);
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
                db.save(claim);
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
                            Msg.button(ChatColor.RED, "[Buy More]", "/claim buy ", "&9/claim buy <amount>\n&fBuy more claim blocks"));
                    return true;
                }
                for (Claim other: claims) {
                    if (other != claim && other.isInWorld(claim.getWorld()) && other.getArea().overlaps(newArea)) {
                        Msg.msg(player, ChatColor.RED, "Your claim would connect with another claim");
                        return true;
                    }
                }
                claim.setArea(newArea);
                db.save(claim);
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
                db.save(claim);
                Msg.msg(player, ChatColor.BLUE, "Shrunk your claim to where you are standing");
                highlightClaim(claim, player);
                return true;
            }
            break;
        default:
            return false;
        }
        return false;
    }

    boolean onNewclaimCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        if (args.length != 0) return false;
        Player player = (Player)sender;
        boolean isHomeWorld = false;
        boolean isHomeEndWorld = false;
        World playerWorld = player.getWorld();
        String playerWorldName = playerWorld.getName();
        if (playerWorldName.equals(homeWorld)) {
            isHomeWorld = true;
        } else if (playerWorldName.equals(homeTheEndWorld)) {
            isHomeEndWorld = true;
        } else {
            Msg.msg(player, ChatColor.RED, "You cannot make a claim in this world");
            return true;
        }
        // Check for other claims
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        for (Claim claim: claims) {
            if (claim.getWorld().equals(playerWorldName)) {
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
        Area area = new Area(x - 31, y - 31, x + 32, y + 32);
        Claim claim = new Claim(this, playerId, playerWorldName, area);
        claim.saveToDatabase();
        claims.add(claim);
        Msg.msg(player, ChatColor.GREEN, "Claim created!");
        highlightClaim(claim, player);
        return true;
    }

    boolean onHomeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player)sender;
        if (args.length == 0) {
            Home home = findHome(player.getUniqueId(), null);
            if (home != null) {
                Location location = home.createLocation();
                if (location == null) {
                    Msg.msg(player, ChatColor.RED, "Primary home could not be found");
                    return true;
                }
                player.teleport(location);
                Msg.msg(player, ChatColor.GREEN, "Welcome home :)");
                return true;
            }
            UUID playerId = player.getUniqueId();
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null) {
                Claim claim = getClaimAt(bedSpawn.getBlock());
                if (claim != null && claim.canVisit(playerId)) {
                    player.teleport(bedSpawn.add(0.5, 0.0, 0.5));
                    Msg.msg(player, ChatColor.BLUE, "Welcome to your bed. :)");
                    return true;
                }
            }
            Claim claim = claims.stream().filter(c -> c.isInWorld(homeWorld) && c.isOwner(playerId)).findFirst().orElse(null);
            if (claim != null) {
                World bworld = getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                Location location = bworld.getHighestBlockAt((area.ax + area.bx) / 2, (area.ay + area.by) / 2).getLocation().add(0.5, 0.0, 0.5);
                player.teleport(location);
                Msg.msg(player, ChatColor.GREEN, "Welcome to your claim. :)");
                highlightClaim(claim, player);
                return true;
            }
            findPlaceToBuild(player);
            return true;
        }
        if (args.length == 1) {
            Home home = findHome(player.getUniqueId(), args[0]);
            if (home == null) {
                Msg.msg(player, ChatColor.RED, "Home not found: %s", args[0]);
                return true;
            }
            Location location = home.createLocation();
            if (location == null) {
                Msg.msg(player, ChatColor.RED, "Home \"%s\" could not be found");
                return true;
            }
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
            Msg.msg(player, ChatColor.BLUE, "You have %d homes", playerHomes.size());
            for (Home home: playerHomes) {
                Object nameButton;
                if (home.getName() == null) {
                    nameButton = Msg.button(ChatColor.GRAY, "&oPrimary", "/home", "&9/home\nTeleport to your primary home");
                } else {
                    String cmd = "/home " + home.getName();
                    nameButton = Msg.button(ChatColor.WHITE, home.getName(), cmd, "&9" + cmd + "\nTeleport to home \"" + home.getName() + "\"");
                }
                Msg.raw(player, "",
                        Msg.label(ChatColor.BLUE, "+ "),
                        nameButton);
            }
            return true;
        }
        switch (args[0]) {
        case "set":
            return onSethomeCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        case "invite":
            if (args.length == 2 || args.length == 3) {
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    Msg.msg(player, ChatColor.RED, "Player not found: %s", targetName);
                    return true;
                }
                String homeName = args.length >= 3 ? args[2] : null;
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
                    HomeInvite invite = new HomeInvite(targetId);
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
                            Msg.button(ChatColor.GREEN, "[Visit]", cmd, "&a" + cmd + "\nVisit this home"));
                } else {
                    String cmd = "/home " + player.getName() + ":" + home.getName();
                    Msg.raw(target, "",
                            Msg.label(ChatColor.WHITE, player.getName() + " invited you to their home: "),
                            Msg.button(ChatColor.GREEN, "[" + home.getName() + "]", cmd, "&a" + cmd + "\nVisit this home"));
                }
                return true;
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
                        Msg.button(ChatColor.GREEN, cmd, cmd, "&a" + cmd + "\nCan also be found under /visit."));
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
                    Msg.msg(player, ChatColor.YELLOW, "Primary home unset. The &o/home&e command will take you to your bed spawn or primary claim.");
                } else {
                    Msg.msg(player, ChatColor.YELLOW, "Home \"%s\" deleted", homeName);
                }
                return true;
            }
            break;
        default:
            break;
        }
        return false;
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
                        Msg.button(ChatColor.WHITE, home.getPublicName(), cmd, "&9" + cmd + "\nVisit this home"),
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
            && null != claims.stream().filter(c -> c.isOwner(playerId) && c.isInWorld(homeWorld)).findFirst().orElse(null)) {
            Msg.msg(player, ChatColor.RED, "You already have a claim!");
            return true;
        }
        findPlaceToBuild(player);
        return true;
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
        World bworld = getServer().getWorld(homeWorld);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + homeWorld);
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
        SAMPLE:
        for (int i = 0; i < 100; i += 1) {
            int x = cx - size / 2 + random.nextInt(size);
            int z = cz - size / 2 + random.nextInt(size);
            for (Claim claim: claims) {
                if (claim.isInWorld(homeWorld) && claim.getArea().isWithin(x, z, claimMargin)) {
                    continue SAMPLE;
                }
            }
            location = bworld.getBlockAt(x, 255, z).getLocation().add(0.5, 0.5, 0.5);
        }
        if (location == null) {
            Msg.msg(player, ChatColor.RED, "Could not find a place to build. Please try again");
            return;
        }
        // Teleport, notify, and set cooldown
        player.teleport(location);
        Msg.raw(player, "",
                Msg.label(ChatColor.WHITE, "Found you a place to build. "),
                Msg.button(ChatColor.GREEN, "[Claim]", "/newclaim ", Msg.format("&a/newclaim&f&o\nCreate a claim and set a home at this location so you can build and return any time.")),
                Msg.label(ChatColor.WHITE, " it or "),
                Msg.button(ChatColor.YELLOW, "[Retry]", "/home", Msg.format("&a/home&f&o\nFind another random location.")));
        player.setMetadata(META_COOLDOWN_WILD, new FixedMetadataValue(this, System.nanoTime()));
        player.setMetadata(META_NOFALL, new FixedMetadataValue(this, System.nanoTime()));
    }

    void showClaimSettings(Claim claim, Player player) {
        Msg.msg(player, ChatColor.BLUE, "Claim Settings");
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
        db.save(claim);
    }

    // Configuration utility

    void loadFromConfig() {
        reloadConfig();
        homeWorld = getConfig().getString("HomeWorld");
        homeNetherWorld = homeWorld + "_nether";
        homeTheEndWorld = homeWorld + "_the_end";
        claimMargin = getConfig().getInt("ClaimMargin");
        homeMargin = getConfig().getInt("HomeMargin");
        buildCooldown = getConfig().getInt("BuildCooldown");
        claimBlockCost = getConfig().getDouble("ClaimBlockCost");
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

    // Claim Utility

    boolean isHomeWorld(World world) {
        String worldName = world.getName();
        return worldName.equals(homeWorld)
            || worldName.equals(homeNetherWorld)
            || worldName.equals(homeTheEndWorld);
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

    Claim getClaimAt(String world, int x, int y) {
        final String claimWorld;
        if (world.equals(homeWorld)) {
            claimWorld = homeWorld;
        } else if (world.equals(homeNetherWorld)) {
            claimWorld = homeWorld;
        } else if (world.equals(homeTheEndWorld)) {
            claimWorld = homeTheEndWorld;
        } else {
            return null;
        }
        // Find claim
        return claims.stream().filter(c -> c.isInWorld(claimWorld) && c.getArea().contains(x, y)).findFirst().orElse(null);
    }

    Claim findNearestOwnedClaim(Player player) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        return claims.stream().filter(c -> c.isOwner(playerId) && c.isInWorld(playerWorld)).min((a, b) -> Integer.compare(a.getArea().distanceToPoint(x, z), b.getArea().distanceToPoint(x, z))).orElse(null);
    }

    List<Home> findHomes(UUID owner) {
        return homes.stream().filter(h -> h.isOwner(owner)).collect(Collectors.toList());
    }

    Home findHome(UUID owner, String name) {
        return homes.stream().filter(h -> h.isOwner(owner) && h.isNamed(name)).findFirst().orElse(null);
    }

    Home findPublicHome(String name) {
        return homes.stream().filter(h -> name.equals(h.getPublicName())).findFirst().orElse(null);
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

    // Event Handling

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
        if (player.hasMetadata(META_IGNORE)) return true;
        String blockWorld = block.getWorld().getName();
        // Figure out claim world
        String claimWorld;
        if (blockWorld.equals(homeWorld)) {
            claimWorld = homeWorld;
        } else if (blockWorld.equals(homeNetherWorld)) {
            claimWorld = homeWorld;
        } else if (blockWorld.equals(homeTheEndWorld)) {
            claimWorld = homeTheEndWorld;
        } else {
            return true;
        }
        // Find claim
        Claim claim = claims.stream().filter(c -> c.isInWorld(claimWorld) && c.getArea().contains(block.getX(), block.getZ())).findFirst().orElse(null);
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
        if (claim.getVisitors().contains(uuid)) {
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

    // private boolean autoCheckAction(Player player, Location location, Action action) {
    //     return Claims.getInstance().autoCheckAction(plugin.createPlayer(player), plugin.createLocation(location), action);
    // }

    // private boolean autoCheckAction(Player player, Location location, Action action, Cancellable cancel) {
    //     boolean result = autoCheckAction(player, location, action);
    //     if (!result) cancel.setCancelled(true);
    //     return result;
    // }

    // private boolean isWorldBlacklisted(World world) {
    //     return plugin.getClaims().getWorldBlacklist().contains(world.getName());
    // }

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
                return;
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
}
