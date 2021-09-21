package com.cavetale.home;

import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class HomePlugin extends JavaPlugin {
    @Getter private static HomePlugin instance;
    // Globals
    static final String META_LOCATION = "home.location";
    static final String META_BUY = "home.buyclaimblocks";
    static final String META_ABANDON = "home.abandonclaim";
    static final String META_NEWCLAIM = "home.newclaim";
    static final String META_IGNORE = "home.ignore";
    private final boolean deleteOverlappingClaims = false;
    // Database
    SQLDatabase db;
    // Worlds
    String primaryHomeWorld;
    final Map<String, WorldSettings> worldSettings = new HashMap<>();
    final Map<String, String> mirrorWorlds = new HashMap<>();
    // Homes
    final List<Home> homes = new ArrayList<>();
    final List<String> homeWorlds = new ArrayList<>();
    final List<Claim> claims = new ArrayList<>();
    final Sessions sessions = new Sessions(this);
    final EventListener eventListener = new EventListener(this);
    private MagicMapListener magicMapListener;
    private ClaimListener claimListener;
    // Utilty
    long ticks;
    Random random = ThreadLocalRandom.current();
    // Interface
    DynmapClaims dynmapClaims;
    // Commands
    final HomeAdminCommand homeAdminCommand = new HomeAdminCommand(this);
    final ClaimCommand claimCommand = new ClaimCommand(this);
    final HomeCommand homeCommand = new HomeCommand(this);
    final HomesCommand homesCommand = new HomesCommand(this);
    final VisitCommand visitCommand = new VisitCommand(this);
    final SetHomeCommand setHomeCommand = new SetHomeCommand(this);
    final BuildCommand buildCommand = new BuildCommand(this);
    final InviteHomeCommand inviteHomeCommand = new InviteHomeCommand(this);
    final SubclaimCommand subclaimCommand = new SubclaimCommand(this);
    // Cache
    private int cachedClaimIndex = -1;
    private long cacheLookups = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class, Subclaim.SQLRow.class, ClaimTrust.class,
                          Home.class, HomeInvite.class);
        db.createAllTables();
        claimListener = new ClaimListener(this).enable();
        eventListener.enable();
        getCommand("homeadmin").setExecutor(homeAdminCommand);
        getCommand("claim").setExecutor(claimCommand);
        getCommand("home").setExecutor(homeCommand);
        getCommand("homes").setExecutor(homesCommand);
        getCommand("visit").setExecutor(visitCommand);
        getCommand("sethome").setExecutor(setHomeCommand);
        getCommand("invitehome").setExecutor(inviteHomeCommand);
        getCommand("build").setExecutor(buildCommand);
        subclaimCommand.enable();
        loadFromConfig();
        loadFromDatabase();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
        if (getServer().getPluginManager().isPluginEnabled("dynmap")) {
            enableDynmap();
        }
        if (getServer().getPluginManager().isPluginEnabled("MagicMap")) {
            magicMapListener = new MagicMapListener(this).enable();
        }
    }

    @Override
    public void onDisable() {
        cachedClaimIndex = -1;
        claims.clear();
        homes.clear();
        disableDynmap();
        db.waitForAsyncTask();
        db.close();
    }

    // --- Inner classes for utility

    @Value
    class CachedLocation {
        final String world;
        final int x;
        final int z;
        final int claimId;
    }

    // --- Ticking

    void onTick() {
        ticks += 1;
        for (Player player : getServer().getOnlinePlayers()) {
            tickPlayer(player);
        }
        for (World world : getServer().getWorlds()) {
            if (!(isHomeWorld(world))) continue;
            if (world.getEnvironment() == World.Environment.NORMAL
                && world.getTime() > 13000L && world.getTime() < 23000L) {
                int total = 0;
                int sleeping = 0;
                for (Player player : world.getPlayers()) {
                    if (player.isSleepingIgnored()) continue;
                    if (player.getGameMode() == GameMode.SPECTATOR) continue;
                    total += 1;
                    if (player.isSleeping() && player.getSleepTicks() >= 100) {
                        sleeping += 1;
                    }
                }
                int half = (total - 1) / 2 + 1;
                if (total > 0 && sleeping > 0 && sleeping >= half) {
                    getLogger().info("Skipping night in " + world.getName());
                    world.setTime(0L);
                    for (Player player : world.getPlayers()) {
                        // false = seSpawnLocation
                        if (player.isSleeping()) player.wakeup(false);
                    }
                }
            }
        }
        if ((ticks % 200L) == 0L) {
            if (dynmapClaims != null) dynmapClaims.update();
        }
    }

    void tickPlayer(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Location loc = player.getLocation();
        final World world = loc.getWorld();
        final String worldName = world.getName();
        if (!isHomeWorld(world)) {
            player.removeMetadata(META_LOCATION, this);
            return;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        if (player.isGliding() && ticks % 10 == 0) {
            for (Claim claim : claims) {
                if (claim.isInWorld(worldName) && claim.getArea().isWithin(x, z, 64) && !claim.getBoolSetting(Claim.Setting.ELYTRA)) {
                    player.sendTitle("" + ChatColor.RED + ChatColor.BOLD + "WARNING",
                                     "" + ChatColor.RED + ChatColor.BOLD + "Approaching No-Fly Zone!",
                                     0, 11, 0);
                    player.playSound(player.getEyeLocation(),
                                     Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER,
                                     1.0f, 2.0f);
                    break;
                }
            }
        }
        Claim claim = getClaimAt(loc);
        if (claim != null) {
            if (claim.isOwner(player) && (ticks % 100) == 0
                && claim.getBlocks() > claim.getArea().size()
                && claim.getBoolSetting(Claim.Setting.AUTOGROW)
                && autoGrowClaim(claim)) {
                highlightClaim(claim, player);
            }
            if (player.isGliding() && !claim.getBoolSetting(Claim.Setting.ELYTRA)) {
                player.setGliding(false);
            }
        }
        CachedLocation cl1 = getMetadata(player, META_LOCATION, CachedLocation.class)
            .orElse(null);
        if (cl1 == null || !cl1.world.equals(worldName)
            || cl1.x != loc.getBlockX() || cl1.z != loc.getBlockZ()) {
            CachedLocation cl2 = new CachedLocation(worldName,
                                                    loc.getBlockX(), loc.getBlockZ(),
                                                    claim == null ? -1 : claim.getId());
            if (cl1 == null) cl1 = cl2; // Taking the easy way out
            player.setMetadata(META_LOCATION, new FixedMetadataValue(this, cl2));
            if (claim == null) {
                if (cl1.claimId != cl2.claimId) {
                    Claim oldClaim = getClaimById(cl1.claimId);
                    if (oldClaim != null) {
                        if (!oldClaim.getBoolSetting(Claim.Setting.HIDDEN)) {
                            if (oldClaim.isOwner(player)) {
                                player.sendActionBar(ChatColor.GRAY + "Leaving your claim");
                            } else {
                                player.sendActionBar(ChatColor.GRAY + "Leaving "
                                                     + oldClaim.getOwnerName() + "'s claim");
                            }
                            if (oldClaim.getBoolSetting(Claim.Setting.SHOW_BORDERS)) {
                                highlightClaim(oldClaim, player);
                            }
                        }
                    }
                }
            } else { // (claim != null)
                if (cl1.claimId != cl2.claimId) {
                    if (!claim.getBoolSetting(Claim.Setting.HIDDEN)) {
                        if (claim.isOwner(player)) {
                            player.sendActionBar(ChatColor.GRAY + "Entering your claim");
                        } else {
                            player.sendActionBar(ChatColor.GRAY + "Entering "
                                                 + claim.getOwnerName() + "'s claim");
                        }
                        if (claim.getBoolSetting(Claim.Setting.SHOW_BORDERS)) {
                            highlightClaim(claim, player);
                        }
                    }
                }
            }
        }
    }

    // --- Player interactivity

    void findPlaceToBuild(Player player) throws PlayerCommand.Wrong {
        // Determine center and border
        String worldName = primaryHomeWorld; // Set up for future expansion
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            throw new PlayerCommand
                .Wrong("Something went wrong. Please contact an administrator.");
        }
        WildTask wildTask = new WildTask(this, bworld, player);
        wildTask.withCooldown();
    }

    boolean autoGrowClaim(Claim claim) {
        Area area = claim.getArea();
        Area newArea = new Area(area.ax - 1, area.ay - 1, area.bx + 1, area.by + 1);
        if (newArea.size() > claim.getBlocks()) return false;
        String claimWorld = claim.getWorld();
        for (Claim other : claims) {
            if (other != claim && other.isInWorld(claimWorld)
                && other.getArea().overlaps(newArea)) {
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
        for (String key : section.getKeys(false)) {
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
        cachedClaimIndex = -1;
        claims.clear();
        homes.clear();
        for (Claim.SQLRow row : db.find(Claim.SQLRow.class).findList()) {
            Claim claim = new Claim(this);
            claim.loadSQLRow(row);
            claims.add(claim);
        }
        if (deleteOverlappingClaims) {
            List<Claim> deleteClaims = new ArrayList<>();
            for (int i = 0; i < claims.size() - 1; i += 1) {
                List<Claim> overlappingClaims = new ArrayList<>();
                Claim a = claims.get(i);
                overlappingClaims.add(a);
                for (int j = i + 1; j < claims.size(); j += 1) {
                    Claim b = claims.get(j);
                    if (a.getWorld().equals(b.getWorld()) && a.getArea().overlaps(b.getArea())) {
                        getLogger().warning("Claims overlap: " + a.getId() + "/" + b.getId()
                                            + " at " + a.getArea().centerX() + ", " + a.getArea().centerY());
                        overlappingClaims.add(b);
                    }
                }
                if (overlappingClaims.size() == 1) continue;
                Collections.sort(overlappingClaims, (l, r) -> Integer.compare(r.blocks, l.blocks));
                for (int j = 1; j < overlappingClaims.size(); j += 1) {
                    deleteClaims.add(overlappingClaims.get(j));
                }
            }
            if (!deleteClaims.isEmpty()) {
                getLogger().warning("Deleting " + deleteClaims.size() + " claims...");
                for (Claim deleteClaim : deleteClaims) {
                    deleteClaim(deleteClaim);
                }
            }
        }
        for (ClaimTrust trust : db.find(ClaimTrust.class).findList()) {
            for (Claim claim : claims) {
                if (claim.id == trust.claimId) {
                    switch (trust.type) {
                    case "visit": claim.visitors.add(trust.trustee); break;
                    case "member": claim.members.add(trust.trustee); break;
                    default: break;
                    }
                    break;
                }
            }
        }
        for (Subclaim.SQLRow row : db.find(Subclaim.SQLRow.class).findList()) {
            Claim claim = getClaimById(row.getClaimId());
            if (claim == null) {
                getLogger().warning("Subclaim lacks parent claim: id=" + row.getId() + " claim_id=" + row.getClaimId());
                continue;
            }
            Subclaim subclaim = new Subclaim(this, claim, row);
            claim.addSubclaim(subclaim);
        }
        for (Home home : db.find(Home.class).findList()) {
            home.unpack();
            homes.add(home);
        }
        for (HomeInvite invite : db.find(HomeInvite.class).findList()) {
            for (Home home : homes) {
                if (home.id.equals(invite.homeId)) {
                    home.invites.add(invite.getInvitee());
                    break;
                }
            }
        }
    }

    public String worldDisplayName(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null && settings.getDisplayName() != null) {
            return settings.getDisplayName();
        }
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
        for (Claim claim : claims) {
            if (claim.getId() == claimId) return claim;
        }
        return null;
    }

    Claim getClaimAt(Block block) {
        return getClaimAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    Claim getClaimAt(Location location) {
        return getClaimAt(location.getWorld().getName(),
                          location.getBlockX(),
                          location.getBlockZ());
    }

    Claim getClaimAt(String w, int x, int y) {
        if (!homeWorlds.contains(w)) return null;
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        cacheLookups += 1L;
        if (cachedClaimIndex >= 0 && cachedClaimIndex < claims.size()) {
            Claim cachedClaim = claims.get(cachedClaimIndex);
            if (cachedClaim.isInWorld(world) && cachedClaim.getArea().contains(x, y)) {
                cacheHits += 1L;
                if (cachedClaimIndex >= 1) {
                    claims.set(cachedClaimIndex, claims.get(cachedClaimIndex - 1));
                    claims.set(cachedClaimIndex - 1, cachedClaim);
                    cachedClaimIndex -= 1;
                }
                return cachedClaim;
            }
        }
        cacheMisses += 1L;
        for (int i = 0; i < claims.size(); i += 1) {
            Claim claim = claims.get(i);
            if (claim.isInWorld(world) && claim.getArea().contains(x, y)) {
                if (i >= 1) {
                    claims.set(i, claims.get(i - 1));
                    claims.set(i - 1, claim);
                    cachedClaimIndex = i - 1;
                } else {
                    cachedClaimIndex = i;
                }
                return claim;
            }
        }
        return null;
    }

    protected Claim findNearestOwnedClaim(Player player, int radius) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        final String w = mirrorWorlds.containsKey(playerWorld)
            ? mirrorWorlds.get(playerWorld)
            : playerWorld;
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        List<Claim> list = new ArrayList<>();
        int minDist = Integer.MAX_VALUE;
        Claim result = null;
        for (Claim claim : claims) {
            if (claim.isOwner(player) && claim.isInWorld(w) && claim.getArea().isWithin(x, z, radius)) {
                int dist = claim.getArea().distanceToPoint(x, z);
                if (dist < minDist) {
                    result = claim;
                    minDist = dist;
                }
            }
        }
        return result;
    }

    public void highlightClaim(Claim claim, Player player) {
        highlightClaimHelper(claim.getArea(), player.getWorld(), player.getLocation().getBlockY(), (block, bf) -> {
                player.spawnParticle(Particle.BARRIER, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
            });
    }

    public void highlightSubclaim(Subclaim subclaim, Player player) {
        if (!subclaim.isInWorld(player.getWorld())) throw new IllegalArgumentException("Subclaim in wrong world!");
        highlightSubclaim(subclaim.getArea(), player);
    }

    public void highlightSubclaim(Area area, Player player) {
        highlightClaimHelper(area, player.getWorld(), player.getLocation().getBlockY(), (block, face) -> {
                BlockFace move;
                double dx;
                double dz;
                switch (face) {
                case NORTH: dx = 0; dz = 0; move = BlockFace.EAST; break;
                case EAST: dx = 1; dz = 0; move = BlockFace.SOUTH; break;
                case SOUTH: dx = 1; dz = 1; move = BlockFace.WEST; break;
                case WEST: dx = 0; dz = 1; move = BlockFace.NORTH; break;
                default: dx = 0; dz = 0; move = BlockFace.UP; break;
                }
                player.spawnParticle(Particle.END_ROD, block.getLocation().add(dx, 0.125, dz), 1, 0.0, 0.0, 0.0, 0.0);
                dx += move.getModX() * 0.5;
                dz += move.getModZ() * 0.5;
                player.spawnParticle(Particle.END_ROD, block.getLocation().add(dx, 0.125, dz), 1, 0.0, 0.0, 0.0, 0.0);
            });
    }

    /**
     * Inner loop of highlightClaimHelper().
     */
    private void highlightClaimHandleBlock(World world, int x, int y, int z, BlockFace face, BiConsumer<Block, BlockFace> callback) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return;
        Block block = world.getBlockAt(x, y, z);
        while (block.isEmpty() && block.getY() > 0) block = block.getRelative(0, -1, 0);
        while (!block.isEmpty() && block.getY() < 127) block = block.getRelative(0, 1, 0);
        callback.accept(block, face);
    }

    private void highlightClaimHelper(Area area, World world, int playerY, BiConsumer<Block, BlockFace> callback) {
        // Upper X axis
        for (int x = area.ax; x <= area.bx; x += 1) {
            highlightClaimHandleBlock(world, x, playerY, area.ay, BlockFace.NORTH, callback);
        }
        // Lower X axis
        for (int x = area.ax; x <= area.bx; x += 1) {
            highlightClaimHandleBlock(world, x, playerY, area.by, BlockFace.SOUTH, callback);
        }
        // Left Z axis
        for (int z = area.ay; z <= area.by; z += 1) {
            highlightClaimHandleBlock(world, area.ax, playerY, z, BlockFace.WEST, callback);
        }
        // Right Z axis
        for (int z = area.ay; z <= area.by; z += 1) {
            highlightClaimHandleBlock(world, area.bx, playerY, z, BlockFace.EAST, callback);
        }
    }

    // --- Public home and claim finders

    public List<Home> findHomes(UUID owner) {
        List<Home> list = new ArrayList<>();
        for (Home home : homes) {
            if (home.isOwner(owner)) list.add(home);
        }
        return list;
    }

    public Home findHome(UUID owner, String name) {
        for (Home home : homes) {
            if (home.isOwner(owner) && home.isNamed(name)) return home;
        }
        return null;
    }

    public Home findPublicHome(String name) {
        for (Home home : homes) {
            if (name.equalsIgnoreCase(home.getPublicName())) return home;
        }
        return null;
    }

    public Claim findPrimaryClaim(UUID owner) {
        for (Claim claim : claims) {
            if (claim.isOwner(owner)) return claim;
        }
        return null;
    }

    public boolean hasAClaim(UUID owner) {
        for (Claim claim : claims) {
            if (claim.isOwner(owner)) return true;
        }
        return false;
    }

    public List<Claim> findClaims(UUID owner) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.isOwner(owner)) list.add(claim);
        }
        return list;
    }

    public List<Claim> findClaims(Player player) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.isOwner(player)) list.add(claim);
        }
        return list;
    }

    public List<Claim> findClaimsInWorld(UUID owner, String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.isOwner(owner) && claim.isInWorld(world)) list.add(claim);
        }
        return list;
    }

    public List<Claim> findClaimsInWorld(String w) {
        final String world = mirrorWorlds.containsKey(w) ? mirrorWorlds.get(w) : w;
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim.isInWorld(world)) list.add(claim);
        }
        return list;
    }

    public Claim findClaimWithId(int id) {
        for (Claim claim : claims) {
            if (claim.getId() == id) return claim;
        }
        return null;
    }

    public void deleteClaim(Claim claim) {
        int claimId = claim.getId();
        int claimCount = db.find(Claim.SQLRow.class).eq("id", claimId).delete();
        int trustCount = db.find(ClaimTrust.class).eq("claimId", claimId).delete();
        int subclaimCount = db.find(Subclaim.SQLRow.class).eq("claimId", claimId).delete();
        claims.remove(claim);
        cachedClaimIndex = -1;
        getLogger().info("Deleted claim #" + claimId
                         + " claims=" + claimCount
                         + " trusted=" + trustCount
                         + " subclaims=" + subclaimCount);
    }

    // --- Metadata

    <T> Optional<T> getMetadata(Metadatable entity, String key, Class<T> clazz) {
        for (MetadataValue meta : entity.getMetadata(key)) {
            if (meta.getOwningPlugin() == this) return Optional.of(clazz.cast(meta.value()));
        }
        return Optional.empty();
    }

    void setMetadata(Metadatable entity, String key, Object value) {
        entity.setMetadata(key, new FixedMetadataValue(this, value));
    }

    void removeMetadata(Metadatable entity, String key) {
        entity.removeMetadata(key, this);
    }

    boolean doesIgnoreClaims(Player player) {
        return player.hasMetadata(META_IGNORE);
    }

    // --- Dynmap

    void enableDynmap() {
        try {
            dynmapClaims = new DynmapClaims(this);
            dynmapClaims.update();
        } catch (Exception e) {
            getLogger().warning("Cancelling connection with dynmap.");
            e.printStackTrace();
            dynmapClaims = null;
        }
    }

    void disableDynmap() {
        if (dynmapClaims != null) {
            dynmapClaims.disable();
            dynmapClaims = null;
        }
    }

    void warpTo(Player player, final Location loc, Runnable task) {
        final World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        world.getChunkAtAsync(cx, cz, (Consumer<Chunk>) chunk -> {
                if (!player.isValid()) return;
                player.teleport(loc, TeleportCause.COMMAND);
                if (task != null) task.run();
            });
    }
}
