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
    private Random random = new Random(System.currentTimeMillis());
    private static final String META_COOLDOWN_WILD = "home.cooldown.wild";
    private static final String META_LOCATION = "home.location";
    private static final String META_NOFALL = "home.nofall";

    @Override
    public void onEnable() {
        reloadConfig();
        saveDefaultConfig();
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class, ClaimTrust.class, Home.class, HomeInvite.class);
        db.createAllTables();
        loadFromConfig();
        loadFromDatabase();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("claim").setExecutor((s, c, l, a) -> onClaimCommand(s, c, l, a));
        getCommand("newclaim").setExecutor((s, c, l, a) -> onNewclaimCommand(s, c, l, a));
        getCommand("home").setExecutor((s, c, l, a) -> onHomeCommand(s, c, l, a));
        getCommand("sethome").setExecutor((s, c, l, a) -> onSethomeCommand(s, c, l, a));
        getCommand("homes").setExecutor((s, c, l, a) -> onHomesCommand(s, c, l, a));
        getCommand("visit").setExecutor((s, c, l, a) -> onVisitCommand(s, c, l, a));
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

    void onTick() {
        for (Player player: getServer().getOnlinePlayers()) {
            if (!isHomeWorld(player.getWorld())) {
                player.removeMetadata(META_LOCATION, this);
                continue;
            }
            CachedLocation cl1 = (CachedLocation)player.getMetadata(META_LOCATION).stream().filter(a -> a.getOwningPlugin() == this).map(a -> a.value()).findFirst().orElse(null);
            Location pl = player.getLocation();
            if (cl1 == null || !cl1.world.equals(pl.getWorld().getName()) || cl1.x != pl.getBlockX() || cl1.z != pl.getBlockZ()) {
                Claim claim = getClaimAt(pl);
                CachedLocation cl2 = new CachedLocation(pl.getWorld().getName(), pl.getBlockX(), pl.getBlockZ(), claim == null ? -1 : claim.getId());
                if (cl1 == null) cl1 = cl2; // Taking the easy way out
                player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
                if (claim == null) {
                    if (player.getGameMode() != GameMode.ADVENTURE) {
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                    if (cl1.claimId != cl2.claimId) {
                        Claim oldClaim = getClaimById(cl1.claimId);
                        if (oldClaim != null) {
                            if (oldClaim.isOwner(player.getUniqueId())) {
                                Msg.msg(player, ChatColor.YELLOW, "Leaving your claim");
                            } else {
                                Msg.msg(player, ChatColor.YELLOW, "Leaving %s's claim", GenericEvents.cachedPlayerName(oldClaim.getOwner()));
                            }
                        }
                    }
                } else {
                    UUID uuid = player.getUniqueId();
                    if (uuid.equals(claim.getOwner()) || claim.getMembers().contains(uuid)) {
                        if (player.getGameMode() != GameMode.SURVIVAL) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }
                    } else {
                        if (player.getGameMode() != GameMode.ADVENTURE) {
                            player.setGameMode(GameMode.ADVENTURE);
                        }
                    }
                    if (cl1.claimId != cl2.claimId) {
                        if (claim.isOwner(player.getUniqueId())) {
                            Msg.msg(player, ChatColor.BLUE, "Entering your claim");
                        } else {
                            Msg.msg(player, ChatColor.YELLOW, "Entering %s's claim", GenericEvents.cachedPlayerName(claim.getOwner()));
                        }
                    }
                }
            }
        }
    }

    boolean onClaimCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        if (!(sender instanceof Player)) return false;
        final Player player = (Player)sender;
        final UUID playerId = player.getUniqueId();
        switch (args[0]) {
        case "buy":
            if (args.length == 2) {
                int a = 0;
            }
            break;
        default:
            return false;
        }
        return true;
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
            Claim claim = claims.stream().filter(c -> c.getWorld().equals(homeWorld) && c.getOwner().equals(playerId)).findFirst().orElse(null);
            if (claim != null) {
                World bworld = getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                Location location = bworld.getHighestBlockAt((area.ax + area.bx) / 2, (area.ay + area.by) / 2).getLocation().add(0.5, 0.0, 0.5);
                player.teleport(location);
                Msg.msg(player, ChatColor.GREEN, "Welcome to your claim. :)");
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
                        Msg.button(ChatColor.BLUE, "+ ", null, null),
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
                            Msg.button(ChatColor.WHITE, player.getName() + " invited you to their primary home: ", null, null),
                            Msg.button(ChatColor.GREEN, "[Visit]", cmd, "&a" + cmd + "\nVisit this home"));
                } else {
                    String cmd = "/home " + player.getName() + ":" + home.getName();
                    Msg.raw(target, "",
                            Msg.button(ChatColor.WHITE, player.getName() + " invited you to their home: ", null, null),
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
                        Msg.button(ChatColor.WHITE, "Home made public. Players may visit via ", null, null),
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
                        Msg.button(ChatColor.GREEN, "+ ", null, null),
                        Msg.button(ChatColor.WHITE, home.getPublicName(), cmd, "&9" + cmd + "\nVisit this home"),
                        Msg.button(ChatColor.GRAY, " by " + home.getOwnerName(), null, null));
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

    void findPlaceToBuild(Player player) {
        // Cooldown
        MetadataValue meta = player.getMetadata(META_COOLDOWN_WILD).stream().filter(m -> m.getOwningPlugin() == this).findFirst().orElse(null);
        if (meta != null) {
            long remain = (meta.asLong() - System.nanoTime()) / 1000000000 - 10;
            if (remain > 0) {
                Msg.msg(player, ChatColor.RED, "Please wait %d more seconds", remain);
                return;
            }
        }
        // Determine center and border
        World bworld = getServer().getWorld(homeWorld);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + homeWorld);
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
                Msg.button(ChatColor.WHITE, "Found you a place to build. ", null, null),
                Msg.button(ChatColor.GREEN, "[Claim]", "/newclaim ", Msg.format("&a/newclaim&f&o\nCreate a claim and set a home at this location so you can build and return any time.")),
                Msg.button(ChatColor.WHITE, " it or ", null, null),
                Msg.button(ChatColor.YELLOW, "[Retry]", "/home", Msg.format("&a/home&f&o\nFind another random location.")));
        player.setMetadata(META_COOLDOWN_WILD, new FixedMetadataValue(this, System.nanoTime()));
        player.setMetadata(META_NOFALL, new FixedMetadataValue(this, true));
    }

    // Configuration utility

    void loadFromConfig() {
        homeWorld = getConfig().getString("HomeWorld");
        homeNetherWorld = homeWorld + "_nether";
        homeTheEndWorld = homeWorld + "_the_end";
        claimMargin = getConfig().getInt("ClaimMargin");
        homeMargin = getConfig().getInt("HomeMargin");
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
        return claims.stream().filter(c -> claimWorld.equals(c.getWorld()) && c.getArea().contains(x, y)).findFirst().orElse(null);
    }

    List<Home> findHomes(UUID owner) {
        return homes.stream().filter(h -> h.isOwner(owner)).collect(Collectors.toList());
    }

    Home findHome(UUID owner, String name) {
        if (name == null) {
            return homes.stream().filter(h -> h.isOwner(owner) && h.getName() == null).findFirst().orElse(null);
        } else {
            return homes.stream().filter(h -> h.isOwner(owner) && h.getName().equals(name)).findFirst().orElse(null);
        }
    }

    Home findPublicHome(String name) {
        return homes.stream().filter(h -> name.equals(h.getPublicName())).findFirst().orElse(null);
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
        Claim claim = claims.stream().filter(c -> claimWorld.equals(c.getWorld()) && c.getArea().contains(block.getX(), block.getZ())).findFirst().orElse(null);
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
        case CREEPER:
        case SKELETON:
        case SPIDER:
        case GIANT:
        case ZOMBIE:
        case SLIME:
        case GHAST:
        case PIG_ZOMBIE:
        case ENDERMAN:
        case CAVE_SPIDER:
        case SILVERFISH:
        case BLAZE:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case WITHER:
        case WITCH:
        case ENDERMITE:
        case GUARDIAN:
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
