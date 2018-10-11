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
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
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
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
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
    private final Map<String, WorldSettings> worldSettings = new HashMap<>();
    private final Map<String, String> mirrorWorlds = new HashMap<>();
    private String primaryHomeWorld;
    private Random random = new Random(System.nanoTime());
    private static final String META_COOLDOWN_WILD = "home.cooldown.wild";
    private static final String META_LOCATION = "home.location";
    private static final String META_NOFALL = "home.nofall";
    private static final String META_BUY = "home.buyclaimblocks";
    private static final String META_ABANDON = "home.abandonclaim";
    private static final String META_NEWCLAIM = "home.newclaim";
    static final String META_IGNORE = "home.ignore";
    private static final String META_NOCLAIM_WARN = "home.noclaim.warn";
    private static final String META_NOCLAIM_COUNT = "home.noclaim.count";
    private static final String META_NOCLAIM_TIME = "home.noclaim.time";
    private long ticks;
    private DynmapClaims dynmapClaims;

    @Data
    private static class WorldSettings {
        private int claimMargin = 1024;
        private int homeMargin = 64;
        private int wildCooldown = 10;
        private boolean manageGameMode = true;
        private int initialClaimSize = 128;
        private int secondaryClaimSize = 32;
        private double initialClaimCost = 0.0;
        private double secondaryClaimCost = 0.0;
        private double claimBlockCost = 0.1;
        private int minimumClaimSize = 65536;
        private long claimAbandonCooldown = 0;

        void load(ConfigurationSection config) {
            claimMargin = config.getInt("ClaimMargin", claimMargin);
            homeMargin = config.getInt("HomeMargin", homeMargin);
            wildCooldown = config.getInt("WildCooldown", wildCooldown);
            claimBlockCost = config.getDouble("ClaimBlockCost", claimBlockCost);
            manageGameMode = config.getBoolean("ManageGameMode", manageGameMode);
            initialClaimSize = config.getInt("InitialClaimSize", initialClaimSize);
            secondaryClaimSize = config.getInt("SecondaryClaimSize", secondaryClaimSize);
            initialClaimCost = config.getDouble("InitialClaimCost", initialClaimCost);
            secondaryClaimCost = config.getDouble("SecondaryClaimCost", secondaryClaimCost);
            claimAbandonCooldown = config.getLong("ClaimAbandonCooldown", claimAbandonCooldown);
            minimumClaimSize = config.getInt("MinimumClaimSize", minimumClaimSize);
        }
    }

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
        if (getServer().getPluginManager().getPlugin("dynmap") != null) {
            dynmapClaims = new DynmapClaims(this);
            dynmapClaims.update();
        }
    }

    @Override
    public void onDisable() {
        claims.clear();
        homes.clear();
        if (dynmapClaims != null) dynmapClaims.disable();
        dynmapClaims = null;
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

    @Value
    private static class NewClaimMeta {
        String world;
        int x, z;
        Area area;
        double price;
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
                && claim.isOwner(playerId) && (ticks % 100) == 0
                && claim.getBlocks() > claim.getArea().size()
                && claim.getSetting(Claim.Setting.AUTOGROW) == Boolean.TRUE) {
                if (autoGrowClaim(claim)) {
                    highlightClaim(claim, player);
                }
            }
            if (cl1 == null || !cl1.world.equals(pl.getWorld().getName()) || cl1.x != pl.getBlockX() || cl1.z != pl.getBlockZ()) {
                CachedLocation cl2 = new CachedLocation(pl.getWorld().getName(), pl.getBlockX(), pl.getBlockZ(), claim == null ? -1 : claim.getId());
                if (cl1 == null) cl1 = cl2; // Taking the easy way out
                player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
                if (claim == null) {
                    if (player.getGameMode() != GameMode.SURVIVAL && !player.hasMetadata(META_IGNORE) && !player.isOp()) {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                    if (cl1.claimId != cl2.claimId) {
                        Claim oldClaim = getClaimById(cl1.claimId);
                        if (oldClaim != null) {
                            if (oldClaim.isOwner(player.getUniqueId())) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GRAY + "Leaving your claim"));
                            } else {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GRAY + "Leaving " + oldClaim.getOwnerName() + "'s claim"));
                            }
                            highlightClaim(oldClaim, player);
                        }
                    }
                } else { // (claim != null)
                    if (claim.canBuild(playerId) || claim.getSetting(Claim.Setting.PUBLIC) == Boolean.TRUE) {
                        if (player.getGameMode() != GameMode.SURVIVAL && !player.hasMetadata(META_IGNORE) && !player.isOp()) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    } else {
                        if (player.getGameMode() != GameMode.ADVENTURE && !player.hasMetadata(META_IGNORE) && !player.isOp()) {
                            player.setGameMode(GameMode.ADVENTURE);
                        }
                    }
                    if (cl1.claimId != cl2.claimId) {
                        if (claim.isOwner(player.getUniqueId())) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GRAY + "Entering your claim"));
                        } else {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GRAY + "Entering " + claim.getOwnerName() + "'s claim"));
                        }
                        highlightClaim(claim, player);
                    }
                }
            }
        }
        if ((ticks % 200L) == 0L) {
            if (dynmapClaims != null) dynmapClaims.update();
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
                    player.sendMessage(ChatColor.YELLOW + "Respecting home and claim permissions");
                } else {
                    player.setMetadata(META_IGNORE, new FixedMetadataValue(this, true));
                    player.sendMessage(ChatColor.YELLOW + "Ignoring home and claim permissions");
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
                claim.saveToDatabase();
                player.sendMessage(ChatColor.YELLOW + "Claim owner by " + claim.getOwnerName() + " now has " + newblocks + " claim blocks.");
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
        case "adminclaim": {
            if (args.length != 1 || player == null) return false;
            Location loc = player.getLocation();
            Area area = new Area(loc.getBlockX() - 31, loc.getBlockZ() - 31,
                                 loc.getBlockX() + 32, loc.getBlockZ() + 32);
            for (Claim other: findClaimsInWorld(player.getWorld().getName())) {
                if (other.area.contains(area)) {
                    sender.sendMessage(ChatColor.RED + "This claim would intersect an existing claim owned by " + other.getOwnerName() + ".");
                    return true;
                }
            }
            Claim claim = new Claim(this, Claim.ADMIN_ID, player.getWorld().getName(), area);
            claims.add(claim);
            claim.saveToDatabase();
            sender.sendMessage(ChatColor.YELLOW + "Admin claim created");
            highlightClaim(claim, player);
            return true;
        }
        case "transferclaim": {
            if (args.length != 2 || player == null) return false;
            String targetName = args[1];
            UUID targetId;
            if (targetName.equals("-admin")) {
                targetId = Claim.ADMIN_ID;
            } else {
                targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                    return true;
                }
            }
            Claim claim = getClaimAt(player.getLocation());
            if (claim == null) {
                sender.sendMessage(ChatColor.RED + "There is no claim here.");
                return true;
            }
            claim.setOwner(targetId);
            claim.saveToDatabase();
            sender.sendMessage(ChatColor.YELLOW + "Claim transferred to " + claim.getOwnerName() + ".");
            return true;
        }
        case "claiminfo": {
            if (args.length != 1 || player == null) return false;
            Claim claim = getClaimAt(player.getLocation());
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
                    player.sendMessage(ChatColor.RED + "Invalid claim blocks amount: " + args[1]);
                    return true;
                }
                Claim claim = findNearestOwnedClaim(player);
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "You don't have a claim in this world");
                    return true;
                }
                WorldSettings settings = worldSettings.get(claim.getWorld());
                double price = (double)buyClaimBlocks * settings.claimBlockCost;
                String priceFormat = GenericEvents.formatMoney(price);
                if (GenericEvents.getPlayerBalance(playerId) < price) {
                    player.sendMessage(ChatColor.RED + "You do not have " + priceFormat + " to buy " + buyClaimBlocks + " claim blocks");
                    return true;
                }
                BuyClaimBlocks meta = new BuyClaimBlocks(buyClaimBlocks, price, claim.getId(), "" + random.nextInt(9999));
                player.setMetadata(META_BUY, new FixedMetadataValue(this, meta));
                player.sendMessage(ChatColor.WHITE + "Buying " + ChatColor.GREEN + buyClaimBlocks + ChatColor.WHITE + " for " + ChatColor.GREEN + priceFormat + ChatColor.WHITE + ".");
                player.spigot().sendMessage(new ComponentBuilder("")
                                            .append("Confirm this purchase: ").color(ChatColor.GRAY)
                                            .append("[Confirm]").color(ChatColor.GREEN)
                                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + meta.token))
                                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "Confirm\nBuy " + buyClaimBlocks + " for " + priceFormat + ".")))
                                            .append("  ").reset()
                                            .append("[Cancel]").color(ChatColor.RED)
                                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"))
                                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Cancel purchase.")))
                                            .create());
                return true;
            }
            break;
        case "confirm": {
            if (args.length != 2) return true;
            // BuyClaimBlocks confirm
            MetadataValue fmv = player.getMetadata(META_BUY).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
            if (fmv != null) {
                BuyClaimBlocks meta = (BuyClaimBlocks)fmv.value();
                player.removeMetadata(META_BUY, this);
                Claim claim = getClaimById(meta.claimId);
                if (claim == null) return true;
                if (!args[1].equals(meta.token)) {
                    player.sendMessage(ChatColor.RED + "Purchase expired");
                    return true;
                }
                if (!GenericEvents.takePlayerMoney(playerId, meta.price, this, "Buy " + meta.amount + " claim blocks")) {
                    player.sendMessage(ChatColor.RED + "You cannot afford " + GenericEvents.formatMoney(meta.price));
                    return true;
                }
                claim.setBlocks(claim.getBlocks() + meta.amount);
                claim.saveToDatabase();
                if (claim.getSetting(Claim.Setting.AUTOGROW) == Boolean.TRUE) {
                    player.sendMessage(ChatColor.WHITE + "Added " + meta.amount + " blocks to this claim. It will grow automatically.");
                } else {
                    player.sendMessage(ChatColor.WHITE + "Added " + meta.amount + " blocks to this claim. Grow it manually or enable \"autogrow\" in the settings.");
                }
            }
            // AbandonClaim confirm
            fmv = player.getMetadata(META_ABANDON).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
            if (fmv != null) {
                int claimId = fmv.asInt();
                player.removeMetadata(META_ABANDON, this);
                Claim claim = findClaimWithId(claimId);
                if (claim == null || !claim.isOwner(playerId) || !args[1].equals("" + claimId)) {
                    player.sendMessage(ChatColor.RED + "Claim removal expired");
                    return true;
                }
                db.find(Claim.SQLRow.class).eq("id", claimId).delete();
                claims.remove(claim);
                player.sendMessage(ChatColor.YELLOW + "Claim removed");
                setStoredPlayerData(playerId, "AbandonedClaim", 1);
            }
            // NewClaim confirm
            fmv = player.getMetadata(META_NEWCLAIM).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
            if (fmv != null) {
                NewClaimMeta ncmeta = (NewClaimMeta)fmv.value();
                player.removeMetadata(META_NEWCLAIM, this);
                if (!args[1].equals(ncmeta.token)) return true;
                if (!homeWorlds.contains(ncmeta.world)) return true;
                WorldSettings settings = worldSettings.get(ncmeta.world);
                for (Claim claimInWorld: findClaimsInWorld(ncmeta.world)) {
                    if (claimInWorld.getArea().overlaps(ncmeta.area)
                        || claimInWorld.getArea().isWithin(ncmeta.x, ncmeta.z, settings.claimMargin)) {
                        sender.sendMessage(ChatColor.RED + "Your claim would be too close to an existing claim.");
                        return true;
                    }
                }
                if (ncmeta.price >= 0.01 && !GenericEvents.takePlayerMoney(playerId, ncmeta.price, this, "Make new claim in " + worldDisplayName(ncmeta.world))) {
                    sender.sendMessage(ChatColor.RED + "You cannot afford " + GenericEvents.formatMoney(ncmeta.price) + "!");
                    return true;
                }
                Claim claim = new Claim(this, playerId, ncmeta.world, ncmeta.area);
                claim.saveToDatabase();
                claims.add(claim);
                player.spigot().sendMessage(new ComponentBuilder("")
                                            .append("Claim created!  ").color(ChatColor.WHITE)
                                            .append("[View]").color(ChatColor.GREEN)
                                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim"))
                                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/claim\n" + ChatColor.WHITE + ChatColor.ITALIC + "The command to access all your claims.")))
                                            .create());
                highlightClaim(claim, player);
            }
            return true;
        }
        case "cancel":
            if (args.length == 1) {
                if (player.hasMetadata(META_BUY) || player.hasMetadata(META_ABANDON) || player.hasMetadata(META_NEWCLAIM)) {
                    player.removeMetadata(META_BUY, this);
                    player.removeMetadata(META_ABANDON, this);
                    player.removeMetadata(META_NEWCLAIM, this);
                    player.sendMessage(ChatColor.GREEN + "Cancelled");
                }
                return true;
            }
            break;
        case "info":
            if (args.length == 1 || args.length == 2) {
                Claim claim;
                if (args.length == 1) {
                    claim = getClaimAt(player.getLocation());
                    if (claim == null) {
                        player.sendMessage(ChatColor.RED + "Stand in the claim you want info on");
                        return true;
                    }
                } else {
                    int claimId;
                    try {
                        claimId = Integer.parseInt(args[1]);
                    } catch (NumberFormatException nfe) {
                        return true;
                    }
                    claim = findClaimWithId(claimId);
                    if (claim == null) return true;
                }
                printClaimInfo(player, claim);
                highlightClaim(claim, player);
                return true;
            }
            break;
        case "add":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "Stand in the claim to which you want to add members");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                    return true;
                }
                if (claim.canBuild(targetId)) {
                    player.sendMessage(ChatColor.RED + "Player is already a member of this claim");
                    return true;
                }
                ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.MEMBER, targetId);
                db.save(ct);
                claim.getMembers().add(targetId);
                if (claim.getVisitors().contains(targetId)) {
                    claim.getVisitors().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                }
                player.sendMessage(ChatColor.WHITE + "Member added: " + targetName);
                return true;
            }
            break;
        case "invite":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "Stand in the claim to which you want to invite people");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                    return true;
                }
                if (claim.canVisit(targetId)) {
                    player.sendMessage(ChatColor.RED + "Player is already invited to this claim");
                    return true;
                }
                ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.VISIT, targetId);
                db.save(ct);
                claim.getMembers().add(targetId);
                player.sendMessage(ChatColor.WHITE + "Player invited: " + targetName);
                return true;
            }
            break;
        case "remove":
            if (args.length == 2) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "Stand in the claim to which you want to invite people");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "You are not the owner of this claim");
                    return true;
                }
                String targetName = args[1];
                UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
                if (targetId == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                    return true;
                }
                if (claim.getMembers().contains(targetId)) {
                    claim.getMembers().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                    player.sendMessage(ChatColor.YELLOW + targetName + " may no longer build");
                } else if (claim.getVisitors().contains(targetId)) {
                    claim.getVisitors().remove(targetId);
                    db.find(ClaimTrust.class).eq("claim_id", claim.getId()).eq("trustee", targetId).delete();
                    player.sendMessage(ChatColor.YELLOW + targetName + " may no longer visit");
                } else {
                    player.sendMessage(ChatColor.RED + targetName + " has no permission in this claim");
                    return true;
                }
                return true;
            }
            break;
        case "set":
            if (args.length == 1) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "Stand in the claim you wish to edit");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "Only the claim owner can do this");
                    return true;
                }
                showClaimSettings(claim, player);
                return true;
            } else if (args.length == 3) {
                Claim claim = getClaimAt(player.getLocation());
                if (claim == null) {
                    player.sendMessage(ChatColor.RED + "Stand in the claim you wish to edit");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "Only the claim owner can do this");
                    return true;
                }
                Claim.Setting setting;
                try {
                    setting = Claim.Setting.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException iae) {
                    player.sendMessage(ChatColor.RED + "Unknown claim setting: " + args[1]);
                    return true;
                }
                Object value;
                switch (args[2]) {
                case "on": case "true": case "enabled": value = true; break;
                case "off": case "false": case "disabled": value = false; break;
                default:
                    player.sendMessage(ChatColor.RED + "Unknown settings value: " + args[2]);
                    return true;
                }
                if (!value.equals(claim.getSetting(setting))) {
                    if (value.equals(setting.defaultValue)) {
                        claim.getSettings().remove(setting);
                    } else {
                        claim.getSettings().put(setting, value);
                    }
                }
                claim.saveToDatabase();
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
                    player.sendMessage(ChatColor.RED + "You don't have a claim nearby");
                    return true;
                }
                if (claim.getArea().contains(x, z)) {
                    player.sendMessage(ChatColor.RED + "Stand where you want the claim to grow to");
                    highlightClaim(claim, player);
                    return true;
                }
                Area area = claim.getArea();
                int ax = Math.min(area.ax, x);
                int ay = Math.min(area.ay, z);
                int bx = Math.max(area.bx, x);
                int by = Math.max(area.by, z);
                Area newArea = new Area(ax, ay, bx, by);
                WorldSettings settings = worldSettings.get(claim.getWorld());
                if (claim.getBlocks() < newArea.size()) {
                    int needed = newArea.size() - claim.getBlocks();
                    String formatMoney = GenericEvents.formatMoney((double)needed * settings.claimBlockCost);
                    player.spigot().sendMessage(new ComponentBuilder("")
                                                .append(needed + " more claim blocks required. ").color(ChatColor.RED)
                                                .append("[Buy More]").color(ChatColor.GRAY)
                                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim buy " + needed))
                                                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "/claim buy " + ChatColor.ITALIC + needed + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Buy more " + needed + " claim blocks for " + formatMoney + ".")))
                                                .create());
                    return true;
                }
                for (Claim other: claims) {
                    if (other != claim && other.isInWorld(claim.getWorld()) && other.getArea().overlaps(newArea)) {
                        player.sendMessage(ChatColor.RED + "Your claim would connect with another claim");
                        return true;
                    }
                }
                claim.setArea(newArea);
                claim.saveToDatabase();
                player.sendMessage(ChatColor.BLUE + "Grew your claim to where you are standing");
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
                    player.sendMessage(ChatColor.RED + "Stand in the claim you wish to shrink");
                    return true;
                }
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "You can only shrink your own claims");
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
                if (!newArea.contains(claim.centerX, claim.centerY)) {
                    player.sendMessage(ChatColor.RED + "Your cannot move a claim from its origin.");
                    return true;
                }
                claim.setArea(newArea);
                claim.saveToDatabase();
                player.sendMessage(ChatColor.BLUE + "Shrunk your claim to where you are standing");
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
                if (!claim.isOwner(playerId)) {
                    player.sendMessage(ChatColor.RED + "This claim does not belong to you.");
                    return true;
                }
                long life = System.currentTimeMillis() - claim.getCreated();
                WorldSettings settings = worldSettings.get(claim.getWorld());
                long cooldown = settings.claimAbandonCooldown * 1000L * 60L;
                if (life < cooldown) {
                    long wait = (cooldown - life) / (1000L * 60L);
                    if (wait <= 1) {
                        player.sendMessage(ChatColor.RED + "You must wait one more minute to abandon this claim.");
                    } else {
                        player.sendMessage(ChatColor.RED + "You must wait " + wait + " more minutes to abandon this claim.");
                    }
                    return true;
                }
                player.setMetadata(META_ABANDON, new FixedMetadataValue(this, claim.getId()));
                player.spigot().sendMessage(TextComponent.fromLegacyText("Really delete this claim? All claim blocks will be lost.", ChatColor.WHITE));
                player.spigot().sendMessage(new ComponentBuilder("").append("This cannot be undone! ").color(ChatColor.RED)
                                            .append("[Confirm]").color(ChatColor.YELLOW)
                                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + claim.getId()))
                                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "Confirm claim removal.")))
                                            .append(" ").reset()
                                            .append("[Cancel]").color(ChatColor.RED)
                                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"))
                                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Cancel claim removal.")))
                                            .create());
                return true;
            }
            break;
        default:
            break;
        }
        listClaims(player);
        return true;
    }

    ComponentBuilder frame(ComponentBuilder cb, String text) {
        ChatColor fc = ChatColor.BLUE;
        return cb.append("            ").color(fc).strikethrough(true)
            .append("[ ").color(fc).strikethrough(false)
            .append(text).color(ChatColor.WHITE)
            .append(" ]").color(fc)
            .append("            ").color(fc).strikethrough(true)
            .append("").strikethrough(false);
    }

    void printClaimInfo(Player player, Claim claim) {
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Info");
        player.spigot().sendMessage(cb.create());
        player.spigot().sendMessage(new ComponentBuilder("").append("Owner ").color(ChatColor.GRAY).append(claim.getOwnerName()).color(ChatColor.WHITE).create());
        player.spigot().sendMessage(new ComponentBuilder("").append("Location ").color(ChatColor.GRAY)
                                    .append(worldDisplayName(claim.getWorld()) + " " + claim.centerX).color(ChatColor.WHITE)
                                    .append(",").color(ChatColor.GRAY)
                                    .append("" + claim.centerY).color(ChatColor.WHITE)
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
                cb.append(" ").reset();
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
            cb.append(" ").reset();
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
        if (claim.isOwner(playerId)) {
            cb.append("  ").append("[Abandon]").color(ChatColor.DARK_RED)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim abandon"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.DARK_RED + "/claim abandon\n" + ChatColor.WHITE + ChatColor.ITALIC + "Abandon this claim.")));
        }
        player.spigot().sendMessage(cb.create());
        WorldSettings settings = worldSettings.get(claim.getWorld());
        if (claim.isOwner(playerId) && claim.contains(playerLocation)) {
            cb = new ComponentBuilder("");
            cb.append("Manage").color(ChatColor.GRAY);
            cb.append("  ").append("[Buy]").color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim buy "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/claim buy " + ChatColor.ITALIC + "AMOUNT\n" + ChatColor.WHITE + ChatColor.ITALIC + "Add some claim blocks to this claim. One claim block costs " + GenericEvents.formatMoney(settings.claimBlockCost) + ".")));
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
                ci = (ci + 1) % colors.length;
                ChatColor color = colors[ci];
                cb.append("[" + worldDisplayName(claim.getWorld()) + "]").color(color)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim info " + claim.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color + "Your claim in " + worldDisplayName(claim.getWorld()))));
            }
            player.spigot().sendMessage(cb.create());
        }
        playerClaims.clear();
        for (Claim claim: claims) {
            if (!claim.isOwner(playerId) && claim.canVisit(playerId)) playerClaims.add(claim);
        }
        if (!playerClaims.isEmpty()) {
            cb = new ComponentBuilder("");
            cb.append("Invited").color(ChatColor.GRAY);
            for (Claim claim: playerClaims) {
                cb.append("  ");
                ci = (ci + 1) % colors.length;
                ChatColor color = colors[ci];
                cb.append("[" + claim.getOwnerName() + "]").color(color)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim info " + claim.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(color + claim.getOwnerName() + " invited you to this claim.")));
            }
            player.spigot().sendMessage(cb.create());
        }
        if (isHomeWorld(player.getWorld()) && findClaimsInWorld(playerId, player.getWorld().getName()).isEmpty()) {
            cb = new ComponentBuilder("");
            cb.append("Make one ").color(ChatColor.GRAY);
            cb.append("  ").append("[New]").color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim new"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/claim new\n" + ChatColor.WHITE + ChatColor.ITALIC + "Make a claim right here.")));
            player.spigot().sendMessage(cb.create());
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
            player.sendMessage(ChatColor.RED + "You cannot make claims in this world");
            return true;
        }
        if (mirrorWorlds.containsKey(playerWorldName)) playerWorldName = mirrorWorlds.get(playerWorldName);
        // Check for other claims
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        // Check for claim collisions
        WorldSettings settings = worldSettings.get(playerWorldName);
        for (Claim claim: findClaimsInWorld(playerWorldName)) {
            // Check claim distance
            if (claim.getArea().isWithin(x, y, settings.claimMargin)) {
                player.sendMessage(ChatColor.RED + "You are too close to another claim");
                return true;
            }
        }
        // Create the claim
        List<Claim> playerClaims = findClaimsInWorld(playerId, playerWorldName);
        for (Claim playerClaim: playerClaims) {
            if (playerClaim.getBlocks() < settings.minimumClaimSize) {
                player.sendMessage(ChatColor.RED + "One of your claims in this world has fewer than " + settings.minimumClaimSize + " claim blocks. Buy more blocks before making a new claim.");
                return true;
            }
        }
        int claimSize;
        double claimCost;
        if (playerClaims.isEmpty()) {
            claimSize = settings.initialClaimSize;
            claimCost = settings.initialClaimCost;
        } else {
            claimSize = settings.secondaryClaimSize;
            claimCost = settings.secondaryClaimCost;
        }
        if (GenericEvents.getPlayerBalance(playerId) < claimCost) {
            sender.sendMessage(ChatColor.RED + "You cannot afford " + GenericEvents.formatMoney(claimCost) + "!");
            return true;
        }
        int rad = claimSize / 2;
        int tol = 0;
        if (rad * 2 == claimSize) tol = 1;
        Area area = new Area(x - rad + tol, y - rad + tol, x + rad, y + rad);
        NewClaimMeta ncmeta = new NewClaimMeta(playerWorldName, x, y, area, claimCost, "" + random.nextInt(9999));
        player.setMetadata(META_NEWCLAIM, new FixedMetadataValue(this, ncmeta));
        sender.sendMessage("");
        sender.spigot().sendMessage(frame(new ComponentBuilder(""), "New Claim").create());
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.AQUA + "You are about to create a claim of " + ChatColor.WHITE + area.width() + "x" + area.height() + " blocks" + ChatColor.AQUA + " around your current location.");
        if (claimCost >= 0.01) {
            sb.append(" " + ChatColor.AQUA + "This will cost you " + ChatColor.WHITE + GenericEvents.formatMoney(claimCost) + ChatColor.AQUA + ".");
        }
        sb.append(" " + ChatColor.AQUA + "Your new claim will have " + ChatColor.WHITE + area.size() + ChatColor.AQUA + " claim blocks.");
        if (settings.claimBlockCost >= 0.01) {
            sb.append(" " + ChatColor.AQUA + "You can buy additional claim blocks for " + ChatColor.WHITE + GenericEvents.formatMoney(settings.claimBlockCost) + ChatColor.AQUA + " per block.");
        }
        sb.append(ChatColor.AQUA + " You can abandon the claim after a cooldown of " + ChatColor.WHITE + settings.claimAbandonCooldown + " minutes" + ChatColor.AQUA + ". Claim blocks will " + ChatColor.WHITE + "not" + ChatColor.AQUA + " be refunded.");
        player.sendMessage(sb.toString());
        sender.spigot().sendMessage(new ComponentBuilder("")
                                    .append("Proceed?  ").color(ChatColor.WHITE)
                                    .append("[Confirm]").color(ChatColor.GREEN)
                                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + ncmeta.token))
                                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "Confirm this purchase")))
                                    .append("  ").reset()
                                    .append("[Cancel]").color(ChatColor.RED)
                                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"))
                                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Cancel this purchase")))
                                    .create());
        sender.sendMessage("");
        return true;
    }

    boolean onHomeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player)sender;
        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            // Try to find a set home
            Home home = findHome(player.getUniqueId(), null);
            if (home != null) {
                Location location = home.createLocation();
                Claim claim = getClaimAt(location);
                if (claim == null || !claim.canVisit(playerId)) {
                    player.sendMessage(ChatColor.RED + "This home is not claimed by you.");
                    return true;
                }
                if (location == null) {
                    player.sendMessage(ChatColor.RED + "Primary home could not be found.");
                    return true;
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
                    player.sendMessage(ChatColor.BLUE + "Welcome to your bed. :)");
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
                player.sendMessage(ChatColor.GREEN + "Welcome to your claim. :)");
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
                    player.sendMessage(ChatColor.RED + "Player not found: " + toks[0]);
                    return true;
                }
                home = findHome(targetId, toks[1].isEmpty() ? null : toks[1]);
                if (home == null || !home.isInvited(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Home not found.");
                    return true;
                }
            } else {
                home = findHome(player.getUniqueId(), arg);
            }
            if (home == null) {
                player.sendMessage(ChatColor.RED + "Home not found: " + arg);
                return true;
            }
            Location location = home.createLocation();
            if (location == null) {
                player.sendMessage(ChatColor.RED + "Home \"%s\" could not be found.");
                return true;
            }
            Claim claim = getClaimAt(location);
            if (claim == null || !claim.canVisit(playerId)) {
                player.sendMessage(ChatColor.RED + "This home is not claimed by you.");
                return true;
            }
            player.sendMessage(ChatColor.GREEN + "Going home.");
            player.sendTitle("", ChatColor.GREEN + "Going home.", 10, 60, 10);
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
            player.sendMessage(ChatColor.RED + "You cannot set homes in this world");
            return true;
        }
        if (claim == null) {
            player.sendMessage(ChatColor.RED + "You can only set homes inside a claim");
            return true;
        }
        if (!claim.canBuild(playerId)) {
            player.sendMessage(ChatColor.RED + "You cannot set homes in this claim");
            return true;
        }
        String playerWorld = player.getWorld().getName();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();
        String homeName = args.length == 0 ? null : args[0];
        WorldSettings settings = worldSettings.get(playerWorld);
        for (Home home: homes) {
            if (home.isOwner(playerId) && home.isInWorld(playerWorld)
                && !home.isNamed(homeName)
                && Math.abs(playerX - (int)home.getX()) < settings.homeMargin
                && Math.abs(playerZ - (int)home.getZ()) < settings.homeMargin) {
                if (home.getName() == null) {
                    player.sendMessage(ChatColor.RED + "Your primary home is nearby");
                } else {
                    player.sendMessage(ChatColor.RED + "You have a home named \"" + home.getName() + "\" nearby");
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
            player.sendMessage(ChatColor.GREEN + "Primary home set");
        } else {
            player.sendMessage(ChatColor.GREEN + "Home \"" + homeName + "\" set");
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
                player.sendMessage(ChatColor.YELLOW + "No homes to show");
                return true;
            }
            player.sendMessage("");
            if (playerHomes.size() == 1) {
                player.sendMessage(ChatColor.BLUE + "You have one home");
            } else {
                player.sendMessage(ChatColor.BLUE + "You have " + playerHomes.size() + " homes");
            }
            for (Home home: playerHomes) {
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append(" + ").color(ChatColor.BLUE);
                if (home.getName() == null) {
                    cb.append("Primary").color(ChatColor.GRAY).italic(true)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home"))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "/home\n" + ChatColor.WHITE + ChatColor.ITALIC + "Teleport to your primary home")))
                        .append("").italic(false);
                } else {
                    String cmd = "/home " + home.getName();
                    cb.append(home.getName()).color(ChatColor.WHITE)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Teleport to your home \"" + home.getName() + "\".")));
                }
                cb.append(" ");
                String infocmd = home.getName() == null ? "/homes info" : "/homes info " + home.getName();
                cb.append("(info)").color(ChatColor.GRAY)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, infocmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + infocmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Get more info on this home.")));
                if (home.getInvites().size() == 1) cb.append(" 1 invite").color(ChatColor.GREEN);
                if (home.getInvites().size() > 1) cb.append(home.getInvites().size() + " invites").color(ChatColor.GREEN);
                if (home.getPublicName() != null) cb.append(" public").reset().color(ChatColor.AQUA);
                player.spigot().sendMessage(cb.create());
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
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.LIGHT_PURPLE + "/homes invites\n" + ChatColor.WHITE + ChatColor.ITALIC + "List home invites")));
                player.spigot().sendMessage(cb.create());
            }
            ComponentBuilder cb = new ComponentBuilder("")
                .append("[Set]").color(ChatColor.BLUE)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes set "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + "/homes set [name]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set a home.")))
                .append("  ")
                .append("[Info]").color(ChatColor.YELLOW)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes info "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/homes info " + ChatColor.ITALIC + "HOME\n" + ChatColor.WHITE + ChatColor.ITALIC + "Get home info.")))
                .append("  ")
                .append("[Invite]").color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes invite "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/homes invite " + ChatColor.ITALIC + "PLAYER HOME\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set a home.")))
                .append("  ")
                .append("[Public]").color(ChatColor.AQUA)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes public "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.AQUA + "/homes public " + ChatColor.ITALIC + "HOME ALIAS\n" + ChatColor.WHITE + ChatColor.ITALIC + "Make home public.")))
                .append("  ")
                .append("[Visit]").color(ChatColor.DARK_AQUA)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/homes visit"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/homes visit " + ChatColor.ITALIC + "HOME\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit a public home.")))
                .append("  ")
                .append("[Delete]").color(ChatColor.RED)
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes delete "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "/homes delete " + ChatColor.ITALIC + "HOME\n" + ChatColor.WHITE + ChatColor.ITALIC + "Delete home.")));
            player.spigot().sendMessage(cb.create());
            player.sendMessage("");
            return true;
        }
        switch (args[0]) {
        case "set":
            return onSethomeCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        case "visit":
            return onVisitCommand(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
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
                    player.sendMessage(ChatColor.RED + "Home not found: " + homeName);
                    return true;
                }
                if (home.getPublicName() != null) {
                    player.sendMessage(ChatColor.RED + "Home is already public under the alias \"" + home.getPublicName() + "\"");
                    return true;
                }
                String publicName = args.length >= 3 ? args[2] : home.getName();
                if (publicName == null) {
                    player.sendMessage(ChatColor.RED + "Please supply a public name for this home");
                    return true;
                }
                if (findPublicHome(publicName) != null) {
                    player.sendMessage(ChatColor.RED + "A public home by that name already exists. Please supply a different alias.");
                    return true;
                }
                home.setPublicName(publicName);
                db.save(home);
                String cmd = "/visit " + publicName;
                ComponentBuilder cb = new ComponentBuilder("")
                    .append("Home made public. Players may visit via ").color(ChatColor.WHITE)
                    .append(cmd).color(ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\nCan also be found under /visit.")));
                player.spigot().sendMessage(cb.create());
                return true;
            }
            break;
        case "delete":
            if (args.length == 1 || args.length == 2) {
                String homeName = args.length >= 2 ? args[1] : null;
                Home home = findHome(playerId, homeName);
                if (home == null) {
                    if (homeName == null) {
                        player.sendMessage(ChatColor.RED + "Your primary home is not set");
                    } else {
                        player.sendMessage(ChatColor.RED + "You do not have a home named \"" + homeName + "\"");
                    }
                    return true;
                }
                db.find(HomeInvite.class).eq("home_id", home.getId()).delete();
                db.delete(home);
                homes.remove(home);
                if (homeName == null) {
                    player.sendMessage(ChatColor.YELLOW + "Primary home unset. The " + ChatColor.ITALIC + "/home" + ChatColor.YELLOW + " command will take you to your bed spawn or primary claim.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Home \"" + homeName + "\" deleted");
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
                    player.sendMessage(ChatColor.RED + "Home not found.");
                    return true;
                }
                player.sendMessage("");
                if (home.getName() == null) {
                    player.spigot().sendMessage(frame(new ComponentBuilder(""), "Primary Home Info").create());
                } else {
                    player.spigot().sendMessage(frame(new ComponentBuilder(""), home.getName() + " Info").create());
                }
                StringBuilder sb = new StringBuilder();
                for (UUID inviteId: home.getInvites()) {
                    sb.append(" ").append(GenericEvents.cachedPlayerName(inviteId));
                }
                player.sendMessage(ChatColor.GRAY + " Location: " + ChatColor.WHITE + String.format("%s %d,%d,%d", worldDisplayName(home.getWorld()), (int)Math.floor(home.getX()), (int)Math.floor(home.getY()), (int)Math.floor(home.getZ())));
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append(" Invited: " + home.getInvites().size()).color(ChatColor.GRAY);
                for (UUID invitee: home.getInvites()) {
                    cb.append(" ").append(GenericEvents.cachedPlayerName(invitee)).color(ChatColor.WHITE);
                }
                player.spigot().sendMessage(cb.create());
                player.sendMessage(ChatColor.GRAY + " Public: " + ChatColor.WHITE + (home.getPublicName() != null ? "yes" : "no"));
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
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "home " + homename + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Use this home invite.")));
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
            player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }
        String homeName = args.length >= 2 ? args[1] : null;
        Home home = findHome(playerId, homeName);
        if (home == null) {
            if (homeName == null) {
                player.sendMessage(ChatColor.RED + "Your primary home is not set");
            } else {
                player.sendMessage(ChatColor.RED + "You have no home named " + homeName);
            }
            return true;
        }
        if (!home.invites.contains(targetId)) {
            HomeInvite invite = new HomeInvite(home.getId(), targetId);
            db.save(invite);
            home.invites.add(targetId);
        }
        player.sendMessage(ChatColor.GREEN + "Invite sent to " + targetName);
        Player target = getServer().getPlayer(targetId);
        if (target == null) return true;
        if (home.getName() == null) {
            String cmd = "/home " + player.getName() + ":";
            ComponentBuilder cb = new ComponentBuilder("")
                .append(player.getName() + " invited you to their primary home: ").color(ChatColor.WHITE)
                .append("[Visit]").color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")));
            player.spigot().sendMessage(cb.create());
        } else {
            String cmd = "/home " + player.getName() + ":" + home.getName();
            ComponentBuilder cb = new ComponentBuilder("")
                .append(player.getName() + " invited you to their home: ").color(ChatColor.WHITE)
                .append("[" + home.getName() + "]").color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")));
            player.spigot().sendMessage(cb.create());
        }
        return true;
    }

    boolean onVisitCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        if (args.length == 0) {
            List<Home> publicHomes = homes.stream().filter(h -> h.getPublicName() != null).collect(Collectors.toList());
            player.sendMessage("");
            if (publicHomes.size() == 1) {
                player.spigot().sendMessage(frame(new ComponentBuilder(""), "One public home").create());
            } else {
                player.spigot().sendMessage(frame(new ComponentBuilder(""), publicHomes.size() + " public homes").create());
            }
            for (Home home: publicHomes) {
                String cmd = "/visit " + home.getPublicName();
                ComponentBuilder cb = new ComponentBuilder("")
                    .append(" + ").color(ChatColor.AQUA)
                    .append(home.getPublicName()).color(ChatColor.WHITE)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")))
                    .append(" by " + home.getOwnerName()).color(ChatColor.GRAY);
                player.spigot().sendMessage(cb.create());
            }
            player.sendMessage("");
            return true;
        }
        Home home = findPublicHome(args[0]);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "Public home not found: " + args[0]);
            return true;
        }
        Location location = home.createLocation();
        if (location == null) {
            player.sendMessage(ChatColor.RED + "Could not take you to this home.");
            return true;
        }
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + home.getOwnerName() + "'s public home \"" + home.getPublicName() + "\"");
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
            player.sendMessage(ChatColor.RED + "You already have a claim!");
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
            return Arrays.asList("set", "invite", "visit", "public", "delete", "info", "invites").stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
        }
        return null;
    }

    void findPlaceToBuild(Player player) {
        // Determine center and border
        String worldName = primaryHomeWorld; // Set up for future expansion
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            player.sendMessage(ChatColor.RED + "Something went wrong. Please contact an administrator.");
            return;
        }
        // Cooldown
        WorldSettings settings = worldSettings.get(worldName);
        MetadataValue meta = player.getMetadata(META_COOLDOWN_WILD).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
        if (meta != null) {
            long remain = (meta.asLong() - System.nanoTime()) / 1000000000 - (long)settings.wildCooldown;
            if (remain > 0) {
                player.sendMessage(ChatColor.RED + "Please wait " + remain + " more seconds");
                return;
            }
        }
        // Borders
        WorldBorder border = bworld.getWorldBorder();
        Location center = border.getCenter();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int size = (int)Math.min(50000.0, border.getSize()) - settings.claimMargin * 2;
        if (size < 0) return;
        Location location = null;
        // Try 100 times to find a random spot, then give up
        List<Claim> worldClaims = findClaimsInWorld(worldName);
        SAMPLE:
        for (int i = 0; i < 100; i += 1) {
            int x = cx - size / 2 + random.nextInt(size);
            int z = cz - size / 2 + random.nextInt(size);
            for (Claim claim: worldClaims) {
                if (claim.getArea().isWithin(x, z, settings.claimMargin)) {
                    continue SAMPLE;
                }
            }
            location = bworld.getBlockAt(x, 255, z).getLocation().add(0.5, 0.5, 0.5);
            location.setPitch(90.0f);
            location.setYaw((float)Math.random() * 360.0f - 180.0f);
        }
        if (location == null) {
            player.sendMessage(ChatColor.RED + "Could not find a place to build. Please try again");
            return;
        }
        // Teleport, notify, and set cooldown
        player.teleport(location);
        ComponentBuilder cb = new ComponentBuilder("")
            .append("Found you a place to build. ").color(ChatColor.WHITE)
            .append("[Claim]").color(ChatColor.GREEN)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim new"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/claim new\n" + ChatColor.WHITE + ChatColor.ITALIC + "Create a claim and set a home at this location so you can build and return any time.")))
            .append("  ", ComponentBuilder.FormatRetention.NONE)
            .append("[Retry]").color(ChatColor.YELLOW)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/home"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "/home\n" + ChatColor.WHITE + ChatColor.ITALIC + "Find another place to build.")));
        player.spigot().sendMessage(cb.create());
        player.setMetadata(META_COOLDOWN_WILD, new FixedMetadataValue(this, System.nanoTime()));
        player.setMetadata(META_NOFALL, new FixedMetadataValue(this, System.nanoTime()));
    }

    void showClaimSettings(Claim claim, Player player) {
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Settings");
        player.spigot().sendMessage(cb.create());
        for (Claim.Setting setting: Claim.Setting.values()) {
            cb = new ComponentBuilder(" ");
            Object value = claim.getSetting(setting);
            String key = setting.name().toLowerCase();
            if (value == Boolean.TRUE) {
                cb.append("[ON]").color(ChatColor.BLUE)
                    .append("  ", ComponentBuilder.FormatRetention.NONE)
                    .append("[OFF]").color(ChatColor.GRAY)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim set " + key + " off"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.RED + "Disable " + setting.displayName)));
            } else if (value == Boolean.FALSE) {
                cb.append("[ON]").color(ChatColor.GRAY)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim set " + key + " on"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "Enable " + setting.displayName)))
                    .append("  ", ComponentBuilder.FormatRetention.NONE)
                    .append("[OFF]").color(ChatColor.RED);
            }
            cb.append(" " + setting.displayName).color(ChatColor.WHITE);
            player.spigot().sendMessage(cb.create());
        }
        player.sendMessage("");
    }

    boolean autoGrowClaim(Claim claim) {
        Area area = claim.getArea();
        Area newArea = new Area(area.ax - 1, area.ay - 1, area.bx + 1, area.by + 1);
        if (newArea.size() > claim.getBlocks()) return false;
        String claimWorld = claim.getWorld();
        for (Claim other: claims) {
            if (other != claim && other.isInWorld(claimWorld) && other.getArea().overlaps(newArea)) {
                return false;
            }
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        return true;
    }

    // --- Configuration utility

    void loadFromConfig() {
        reloadConfig();
        ConfigurationSection section = getConfig().getConfigurationSection("Worlds");
        homeWorlds.clear();
        worldSettings.clear();
        for (String key: section.getKeys(false)) {
            ConfigurationSection worldSection = section.getConfigurationSection(key);
            WorldSettings settings = new WorldSettings();
            worldSettings.put(key, settings);
            settings.load(getConfig());
            if (worldSection != null) {
                settings.load(worldSection);
                if (worldSection.isSet("mirror")) {
                    mirrorWorlds.put(key, worldSection.getString("mirror"));
                }
            }
            homeWorlds.add(key);
        }
        if (!homeWorlds.isEmpty()) {
            primaryHomeWorld = homeWorlds.get(0);
        } else {
            primaryHomeWorld = getServer().getWorlds().get(0).getName();
        }
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
        if (world == null) return worldName;
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
        final String w = mirrorWorlds.containsKey(playerWorld) ? mirrorWorlds.get(playerWorld) : playerWorld;
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        return claims.stream().filter(c -> c.isOwner(playerId) && c.isInWorld(w)).min((a, b) -> Integer.compare(a.getArea().distanceToPoint(x, z), b.getArea().distanceToPoint(x, z))).orElse(null);
    }

    void highlightClaim(Claim claim, Player player) {
        List<Block> blocks = new ArrayList<>();
        Area area = claim.getArea();
        Location playerLocation = player.getLocation();
        int playerX = playerLocation.getBlockX();
        int playerY = playerLocation.getBlockY();
        int playerZ = playerLocation.getBlockZ();
        World world = player.getWorld();
        final int maxd = 16 * 6;
        // Upper X axies
        if (Math.abs(area.ay - playerZ) <= maxd) {
            for (int x = area.ax; x <= area.bx; x += 1) {
                if (Math.abs(x - playerX) <= maxd && world.isChunkLoaded(x >> 4, area.ay >> 4)) {
                    blocks.add(world.getBlockAt(x, playerY, area.ay));
                }
            }
        }
        // Lower X axis
        if (Math.abs(area.by - playerZ) <= maxd) {
            for (int x = area.ax; x <= area.bx; x += 1) {
                if (Math.abs(x - playerX) <= maxd && world.isChunkLoaded(x >> 4, area.by >> 4)) {
                    blocks.add(world.getBlockAt(x, playerY, area.by));
                }
            }
        }
        // Left Z axis
        if (Math.abs(area.ax - playerX) <= maxd) {
            for (int z = area.ay; z < area.by; z += 1) {
                if (Math.abs(z - playerZ) <= maxd && world.isChunkLoaded(area.ax >> 4, z >> 4)) {
                    blocks.add(world.getBlockAt(area.ax, playerY, z));
                }
            }
        }
        // Right Z axis
        if (Math.abs(area.bx - playerX) <= maxd) {
            for (int z = area.ay; z < area.by; z += 1) {
                if (Math.abs(z - playerZ) <= maxd && world.isChunkLoaded(area.bx >> 4, z >> 4)) {
                    blocks.add(world.getBlockAt(area.bx, playerY, z));
                }
            }
        }
        for (Block block: blocks) {
            while (block.isEmpty() && block.getY() > 0) block = block.getRelative(0, -1, 0);
            while (!block.isEmpty() && block.getY() < 127) block = block.getRelative(0, 1, 0);
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

    int getStoredPlayerInt(UUID playerId, String key) {
        Object val = getStoredPlayerData(playerId, key);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number)val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt((String)val);
            } catch (NumberFormatException nfe) { }
        }
        return 0;
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
        COMBAT,
        VEHICLE,
        BUCKET;
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
            case VEHICLE:
            case BUCKET:
            default:
                return true;
            }
        }
        // We know there is a claim, so return on the player is
        // privileged here.  The owner and members can do anything.
        UUID uuid = player.getUniqueId();
        if (claim.canBuild(uuid)) return true;
        if (claim.getSetting(Claim.Setting.PUBLIC) == Boolean.TRUE) return true;
        // Visitors may interact and do combat.
        if (claim.canVisit(uuid)) {
            switch (action) {
            case COMBAT:
            case INTERACT:
                return true;
            case BUILD:
            case VEHICLE:
            case BUCKET:
            default:
                // Forbidden actions are cancelled further down.
                break;
            }
        } else {
            switch (action) {
            case COMBAT: return true;
            default: break;
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
        if (entity instanceof Tameable) {
            Tameable tameable = (Tameable)entity;
            if (!tameable.isTamed()) return false;
            AnimalTamer owner = tameable.getOwner();
            if (owner == null) return false;
            if (owner.getUniqueId().equals(player.getUniqueId())) return true;
        }
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

    void noClaimWarning(Player player) {
        long now = System.nanoTime();
        for (MetadataValue meta: player.getMetadata(META_NOCLAIM_WARN)) {
            if (meta.getOwningPlugin() == this) {
                long time = meta.asLong();
                if (now - time < 10000000000L) return;
                break;
            }
        }
        ComponentBuilder cb = new ComponentBuilder("")
            .append("You did not ").color(ChatColor.RED)
            .append("claim").color(ChatColor.YELLOW)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.YELLOW + "/claim\n" + ChatColor.WHITE + ChatColor.ITALIC + "Claim land and make it yours.")))
            .append(" this area. Building is limited.", ComponentBuilder.FormatRetention.NONE).color(ChatColor.RED);
        player.playSound(player.getEyeLocation(), Sound.ENTITY_POLAR_BEAR_WARNING, SoundCategory.MASTER, 2.0f, 1.0f);
        player.setMetadata(META_NOCLAIM_WARN, new FixedMetadataValue(this, now));
        player.spigot().sendMessage(cb.create());
    }

    boolean noClaimBuild(Player player, Block block) {
        long now = System.nanoTime();
        long noClaimCount = 0L;
        long noClaimTime = 0L;
        for (MetadataValue meta: player.getMetadata(META_NOCLAIM_TIME)) {
            if (meta.getOwningPlugin() == this) {
                noClaimTime = meta.asLong();
                break;
            }
        }
        if (now - noClaimTime > 30000000000L) {
            noClaimTime = now;
            noClaimCount = 1L;
        } else {
            for (MetadataValue meta: player.getMetadata(META_NOCLAIM_COUNT)) {
                if (meta.getOwningPlugin() == this) {
                    noClaimCount = meta.asLong();
                    break;
                }
            }
            noClaimCount += 1L;
            if (noClaimCount > 4L) {
                noClaimWarning(player);
                return false;
            }
        }
        player.setMetadata(META_NOCLAIM_TIME, new FixedMetadataValue(this, noClaimTime));
        player.setMetadata(META_NOCLAIM_COUNT, new FixedMetadataValue(this, noClaimCount));
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isHomeWorld(block.getWorld())) return;
        Claim claim = getClaimAt(block);
        Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        if (claim == null) {
            switch (block.getType()) {
            case CHEST:
            case SPAWNER:
            case TRAPPED_CHEST:
            case IRON_ORE:
            case DIAMOND_ORE:
            case GOLD_ORE:
            case EMERALD_ORE:
            case COAL_ORE:
                event.setCancelled(true);
                return;
            default:
                break;
            }
            if (!noClaimBuild(player, block)) event.setCancelled(true);
            return;
        }
        checkPlayerAction(player, block, Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isHomeWorld(block.getWorld())) return;
        Claim claim = getClaimAt(block);
        Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        if (claim == null) {
            switch (block.getType()) {
            case FIRE:
            case LAVA:
            case TNT:
                event.setCancelled(true);
                return;
            default:
                break;
            }
            if (!noClaimBuild(player, block)) event.setCancelled(true);
            return;
        }
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
        Vehicle vehicle = event.getVehicle();
        if (!isHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        if (isOwner(player, vehicle)) return;
        if (getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!isHomeWorld(vehicle.getWorld())) return;
        Player player = getPlayerDamager(event.getAttacker());
        if (player == null) return;
        if (isOwner(player, vehicle)) return;
        if (getClaimAt(vehicle.getLocation()) == null) return;
        checkPlayerAction(player, vehicle.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!isHomeWorld(vehicle.getWorld())) return;
        if (!(vehicle instanceof LivingEntity) && getClaimAt(vehicle.getLocation()) == null) {
            vehicle.setPersistent(false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        final Entity entity = event.getRightClicked();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.INTERACT, event);
    }

    // Should this just be the same as onPlayerInteractEntity() ?
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player)event.getEntity();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        final Entity mount = event.getMount();
        if (isOwner(player, mount)) return;
        checkPlayerAction(player, mount.getLocation().getBlock(), Action.INTERACT, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
        final Entity entity = event.getEntity();
        if (isOwner(player, entity)) return;
        checkPlayerAction(player, entity.getLocation().getBlock(), Action.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
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
            checkPlayerAction(player, block, Action.INTERACT, event);
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
        if (event.getBucket() == Material.WATER_BUCKET) {
            checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUCKET, event);
        } else {
            checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUILD, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        checkPlayerAction(event.getPlayer(), event.getBlockClicked(), Action.BUCKET, event);
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
        if (player.isOp() || player.hasMetadata(META_IGNORE)) return;
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

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("dynmap")) {
            dynmapClaims = new DynmapClaims(this);
            dynmapClaims.update();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("dynmap")) {
            if (dynmapClaims != null) {
                dynmapClaims.disable();
                dynmapClaims = null;
            }
        }
    }
}
