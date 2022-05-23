package com.cavetale.home;

import com.cavetale.core.connect.Connect;
import com.cavetale.home.claimcache.ClaimCache;
import com.cavetale.home.sql.SQLHomeWorld;
import com.cavetale.home.struct.BlockVector;
import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Collections;
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
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter
public final class HomePlugin extends JavaPlugin {
    @Getter private static HomePlugin instance;
    // Globals
    protected static final String META_BUY = "home.buyclaimblocks";
    protected static final String META_ABANDON = "home.abandonclaim";
    protected static final String META_NEWCLAIM = "home.newclaim";
    private final boolean deleteOverlappingClaims = false;
    // Database
    protected SQLDatabase db;
    // Mirror Worlds, Legacy!
    protected final Map<String, String> mirrorWorlds = Map.of("home_nether", "home");
    // Homes
    protected List<SQLHomeWorld> worldList = List.of();
    protected List<String> localHomeWorlds = List.of();
    protected final List<Home> homes = new ArrayList<>();
    protected final ClaimCache claimCache = new ClaimCache();
    protected final Sessions sessions = new Sessions(this);
    protected final EventListener eventListener = new EventListener(this);
    private MagicMapListener magicMapListener;
    private ClaimListener claimListener;
    // Utilty
    protected long ticks;
    protected Random random = ThreadLocalRandom.current();
    // Interface
    private DynmapClaims dynmapClaims;
    // Commands
    protected final HomeAdminCommand homeAdminCommand = new HomeAdminCommand(this);
    protected final ClaimAdminCommand claimAdminCommand = new ClaimAdminCommand(this);
    protected final ClaimCommand claimCommand = new ClaimCommand(this);
    protected final HomesCommand homesCommand = new HomesCommand(this);
    protected final HomeCommand homeCommand = new HomeCommand(this);
    protected final ListHomesCommand listHomesCommand = new ListHomesCommand(this);
    protected final VisitCommand visitCommand = new VisitCommand(this);
    protected final SetHomeCommand setHomeCommand = new SetHomeCommand(this);
    protected final BuildCommand buildCommand = new BuildCommand(this);
    protected final InviteHomeCommand inviteHomeCommand = new InviteHomeCommand(this);
    protected final UnInviteHomeCommand unInviteHomeCommand = new UnInviteHomeCommand(this);
    protected final SubclaimCommand subclaimCommand = new SubclaimCommand(this);

    @Override
    public void onEnable() {
        instance = this;
        db = new SQLDatabase(this);
        db.registerTables(Claim.SQLRow.class,
                          Subclaim.SQLRow.class,
                          ClaimTrust.class,
                          Home.class,
                          HomeInvite.class,
                          SQLHomeWorld.class);
        db.createAllTables();
        claimListener = new ClaimListener(this).enable();
        eventListener.enable();
        homeAdminCommand.enable();
        claimAdminCommand.enable();
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
        loadFromDatabase();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
        enableDynmap();
        if (getServer().getPluginManager().isPluginEnabled("MagicMap")) {
            magicMapListener = new MagicMapListener(this).enable();
        }
    }

    @Override
    public void onDisable() {
        claimCache.clear();
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
            if (!(isLocalHomeWorld(world))) continue;
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

    protected void findPlaceToBuild(Player player) {
        List<SQLHomeWorld> rows = new ArrayList<>(worldList);
        if (rows.isEmpty()) {
            getLogger().severe("No home worlds configred!");
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator.", RED));
            return;
        }
        rows.sort((a, b) -> Integer.compare(b.getWildPriority(), a.getWildPriority()));
        // Eventually: Make preference!
        int max = rows.get(0).getWildPriority();
        rows.removeIf(it -> it.getWildPriority() < max);
        if (rows.size() > 1) {
            Collections.shuffle(rows);
        }
        String worldName = rows.get(0).getWorld();
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator.", RED));
            return;
        }
        WildTask wildTask = new WildTask(this, bworld, player);
        wildTask.withCooldown();
    }

    protected ClaimOperationResult autoGrowClaim(Claim claim) {
        Area area = claim.getArea();
        Area newArea = new Area(area.ax - 1, area.ay - 1, area.bx + 1, area.by + 1);
        if (newArea.size() > claim.getBlocks()) return ClaimOperationResult.INSUFFICIENT_BLOCKS;
        String claimWorld = claim.getWorld();
        for (Claim other : claimCache.within(claim.getWorld(), newArea)) {
            if (other != claim) return ClaimOperationResult.OVERLAP;
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        return ClaimOperationResult.SUCCESS;
    }

    protected void loadFromDatabase() {
        worldList = db.find(SQLHomeWorld.class).findList();
        localHomeWorlds = new ArrayList<>();
        for (SQLHomeWorld it : worldList) {
            if (it.getServer().equals(Connect.get().getServerName())) {
                localHomeWorlds.add(it.getWorld());
            }
        }
        claimCache.clear();
        claimCache.initialize(localHomeWorlds);
        homes.clear();
        for (Claim.SQLRow row : db.find(Claim.SQLRow.class).findList()) {
            Claim claim = new Claim(this);
            claim.loadSQLRow(row);
            claimCache.add(claim);
        }
        if (deleteOverlappingClaims) {
            List<Claim> claims = claimCache.getAllClaims();
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
            Claim claim = getClaimById(trust.claimId);
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
        SQLHomeWorld row = findHomeWorld(worldName);
        if (row != null && row.getDisplayName() != null) return row.getDisplayName();
        if (worldName.endsWith("_nether")) return "nether";
        if (worldName.endsWith("_the_end")) return "end";
        return "overworld";
    }

    public boolean isLocalHomeWorld(World world) {
        return localHomeWorlds.contains(world.getName());
    }

    public boolean isLocalHomeWorld(String worldName) {
        return localHomeWorlds.contains(worldName);
    }

    public Claim getClaimById(int claimId) {
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.getId() == claimId) return claim;
        }
        return null;
    }

    public Claim getClaimAt(Block block) {
        return getClaimAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    public Claim getClaimAt(Location location) {
        return getClaimAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public Claim getClaimAt(BlockVector blockVector) {
        return getClaimAt(blockVector.world, blockVector.x, blockVector.z);
    }

    public Claim getClaimAt(String w, int x, int y) {
        if (!localHomeWorlds.contains(w)) return null;
        w = mirrorWorlds.getOrDefault(w, w);
        return claimCache.at(w, x, y);
    }

    protected Claim findNearestOwnedClaim(Player player, int radius) {
        Location playerLocation = player.getLocation();
        String playerWorld = playerLocation.getWorld().getName();
        final String w = mirrorWorlds.getOrDefault(playerWorld, playerWorld);
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        int minDist = Integer.MAX_VALUE;
        Claim result = null;
        Area area = new Area(x - radius, z - radius, x + radius, z + radius);
        for (Claim claim : claimCache.within(w, area)) {
            if (!claim.isOwner(player)) continue;
            int dist = claim.getArea().distanceToPoint(x, z);
            if (dist >= minDist) continue;
            result = claim;
            minDist = dist;
        }
        return result;
    }

    public void highlightClaim(Claim claim, Player player) {
        highlightClaimHelper(claim.getArea(), player.getWorld(), player.getLocation().getBlockY(), (block, bf) -> {
                player.spawnParticle(Particle.BLOCK_MARKER, block.getLocation().add(0.5, 0.5, 0.5),
                                     1, 0.0, 0.0, 0.0, 0.0, Material.BARRIER.createBlockData());
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
        List<Claim> playerClaims = findClaims(owner);
        return playerClaims.isEmpty() ? null : playerClaims.get(0);
    }

    public boolean hasAClaim(UUID owner) {
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.isOwner(owner)) return true;
        }
        return false;
    }

    public List<Claim> findClaims(UUID owner) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.isOwner(owner)) list.add(claim);
        }
        Collections.sort(list, (a, b) -> Long.compare(b.created, a.created));
        return list;
    }

    public List<Claim> findClaims(Player player) {
        return findClaims(player.getUniqueId());
    }

    public List<Claim> findClaimsInWorld(UUID owner, String w) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claimCache.inWorld(mirrorWorlds.getOrDefault(w, w))) {
            if (claim.isOwner(owner)) {
                list.add(claim);
            }
        }
        return list;
    }

    public List<Claim> findClaimsInWorld(String w) {
        return claimCache.inWorld(mirrorWorlds.getOrDefault(w, w));
    }

    public void deleteClaim(Claim claim) {
        int claimId = claim.getId();
        int claimCount = db.find(Claim.SQLRow.class).eq("id", claimId).delete();
        int trustCount = db.find(ClaimTrust.class).eq("claimId", claimId).delete();
        int subclaimCount = db.find(Subclaim.SQLRow.class).eq("claimId", claimId).delete();
        claimCache.remove(claim);
        claim.setDeleted(true);
        getLogger().info("Deleted claim #" + claimId
                         + " claims=" + claimCount
                         + " trusted=" + trustCount
                         + " subclaims=" + subclaimCount);
    }

    protected <T> Optional<T> getMetadata(Metadatable entity, String key, Class<T> clazz) {
        for (MetadataValue meta : entity.getMetadata(key)) {
            if (meta.getOwningPlugin() == this) return Optional.of(clazz.cast(meta.value()));
        }
        return Optional.empty();
    }

    protected void setMetadata(Metadatable entity, String key, Object value) {
        entity.setMetadata(key, new FixedMetadataValue(this, value));
    }

    protected void removeMetadata(Metadatable entity, String key) {
        entity.removeMetadata(key, this);
    }

    public void ignoreClaims(Player player, boolean value) {
        sessions.of(player).setIgnoreClaims(value);
    }

    public boolean doesIgnoreClaims(Player player) {
        return sessions.of(player).isIgnoreClaims();
    }

    protected void warpTo(Player player, final Location loc, Runnable task) {
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
        if (!localHomeWorlds.contains(blockVector.world)) return true;
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

    public SQLHomeWorld findHomeWorld(String worldName) {
        for (SQLHomeWorld it : worldList) {
            if (worldName.equals(it.getWorld())) return it;
        }
        return null;
    }
}
