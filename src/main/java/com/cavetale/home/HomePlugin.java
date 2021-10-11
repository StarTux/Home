package com.cavetale.home;

import com.cavetale.home.struct.BlockVector;
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
import java.util.logging.Level;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
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
    static final String META_BUY = "home.buyclaimblocks";
    static final String META_ABANDON = "home.abandonclaim";
    static final String META_NEWCLAIM = "home.newclaim";
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
    protected long ticks;
    Random random = ThreadLocalRandom.current();
    // Interface
    private DynmapClaims dynmapClaims;
    // Commands
    final HomeAdminCommand homeAdminCommand = new HomeAdminCommand(this);
    final ClaimCommand claimCommand = new ClaimCommand(this);
    final HomesCommand homesCommand = new HomesCommand(this);
    final HomeCommand homeCommand = new HomeCommand(this);
    final ListHomesCommand listHomesCommand = new ListHomesCommand(this);
    final VisitCommand visitCommand = new VisitCommand(this);
    final SetHomeCommand setHomeCommand = new SetHomeCommand(this);
    final BuildCommand buildCommand = new BuildCommand(this);
    final InviteHomeCommand inviteHomeCommand = new InviteHomeCommand(this);
    final UnInviteHomeCommand unInviteHomeCommand = new UnInviteHomeCommand(this);
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
        claimCommand.enable();
        buildCommand.enable();
        homesCommand.enable();
        setHomeCommand.enable();
        homeCommand.enable();
        listHomesCommand.enable();
        visitCommand.enable();
        inviteHomeCommand.enable();
        unInviteHomeCommand.enable();
        subclaimCommand.enable();
        loadFromConfig();
        loadFromDatabase();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
        enableDynmap();
        if (getServer().getPluginManager().isPluginEnabled("MagicMap")) {
            magicMapListener = new MagicMapListener(this).enable();
        }
    }

    @Override
    public void onDisable() {
        cachedClaimIndex = -1;
        claims.clear();
        homes.clear();
        db.waitForAsyncTask();
        db.close();
        disableDynmap();
    }

    private void onTick() {
        for (Player player : getServer().getOnlinePlayers()) {
            sessions.of(player).tick(player);
        }
        for (World world : getServer().getWorlds()) {
            if (!(isHomeWorld(world))) continue;
            tickHomeWorld(world);
        }
        ticks += 1;
    }

    private void tickHomeWorld(World world) {
        if (world.getEnvironment() == World.Environment.NORMAL && !world.isDayTime()) {
            int total = 0;
            int sleeping = 0;
            for (Player player : world.getPlayers()) {
                if (player.isSleepingIgnored()) continue;
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                total += 1;
                if (player.isDeeplySleeping()) {
                    sleeping += 1;
                }
            }
            int half = (total - 1) / 2 + 1;
            if (total > 0 && sleeping > 0 && sleeping >= half) {
                getLogger().info("Skipping night in " + world.getName());
                world.setTime(0L);
                for (Player player : world.getPlayers()) {
                    if (player.isSleeping()) player.wakeup(false);
                }
            }
        }
    }

    void findPlaceToBuild(Player player) {
        // Determine center and border
        String worldName = primaryHomeWorld; // Set up for future expansion
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator.",
                                              NamedTextColor.RED));
            return;
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
                Claim a = claims.get(i);
                if (deleteClaims.contains(a)) continue;
                List<Claim> overlappingClaims = new ArrayList<>();
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
            Claim claim = findClaimWithId(trust.claimId);
            if (claim == null) {
                getLogger().warning("Trust without claim: " + trust);
                db.deleteAsync(trust, null);
                continue;
            }
            if (trust.parseTrustType().isNone()) {
                getLogger().warning("Empty trust: " + trust);
                continue;
            }
            claim.trusted.put(trust.getTrustee(), trust);
        }
        for (Subclaim.SQLRow row : db.find(Subclaim.SQLRow.class).findList()) {
            Claim claim = getClaimById(row.getClaimId());
            if (claim == null) {
                getLogger().warning("Subclaim without parent claim: id=" + row.getId() + " claim_id=" + row.getClaimId());
                db.deleteAsync(row, null);
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

    Claim getClaimAt(BlockVector blockVector) {
        return getClaimAt(blockVector.world, blockVector.x, blockVector.z);
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
        claim.setDeleted(true);
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

    public void ignoreClaims(Player player, boolean value) {
        sessions.of(player).setIgnoreClaims(value);
    }

    public boolean doesIgnoreClaims(Player player) {
        return sessions.of(player).isIgnoreClaims();
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

    public boolean isPvPAllowed(BlockVector blockVector) {
        if (!homeWorlds.contains(blockVector.world)) return true;
        Claim claim = getClaimAt(blockVector);
        return claim != null
            ? claim.isPvPAllowed(blockVector)
            : false;
    }

    protected void enableDynmap() {
        if (!getServer().getPluginManager().isPluginEnabled("dynmap")) return;
        try {
            dynmapClaims = new DynmapClaims(this).enable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Enabling dynmap", e);
            dynmapClaims = null;
        }
    }

    protected void disableDynmap() {
        if (dynmapClaims == null) return;
        dynmapClaims.disable();
        dynmapClaims = null;
    }
}
