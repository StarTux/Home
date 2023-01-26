package com.cavetale.home;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.perm.Perm;
import com.cavetale.home.claimcache.ClaimCache;
import com.cavetale.home.sql.SQLClaim;
import com.cavetale.home.sql.SQLClaimTrust;
import com.cavetale.home.sql.SQLHome;
import com.cavetale.home.sql.SQLHomeInvite;
import com.cavetale.home.sql.SQLHomeWorld;
import com.cavetale.home.sql.SQLSubclaim;
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
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
    protected final Homes homes = new Homes();
    protected final ClaimCache claimCache = new ClaimCache();
    protected final Sessions sessions = new Sessions(this);
    protected final EventListener eventListener = new EventListener(this);
    private MagicMapListener magicMapListener;
    protected final ConnectListener connectListener = new ConnectListener(this);
    // Utilty
    protected long ticks;
    protected Random random = ThreadLocalRandom.current();
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
        db.registerTables(List.of(SQLHomeWorld.class,
                                  SQLClaim.class,
                                  SQLClaimTrust.class,
                                  SQLSubclaim.class,
                                  SQLHome.class,
                                  SQLHomeInvite.class));
        db.createAllTables();
        loadFromDatabase();
        if (localHomeWorlds.isEmpty()) {
            getLogger().info("Local home worlds is empty");
        }
        if (!localHomeWorlds.isEmpty()) {
            new ClaimListener(this).enable();
        }
        sessions.enable();
        eventListener.enable();
        connectListener.enable();
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
        if (getServer().getPluginManager().isPluginEnabled("MagicMap")) {
            magicMapListener = new MagicMapListener(this).enable();
        }
        Bukkit.getScheduler().runTaskTimer(this, this::updateFreeSpace, 0L, 20L * 60L);
    }

    @Override
    public void onDisable() {
        claimCache.clear();
        homes.clear();
        db.waitForAsyncTask();
        db.close();
    }

    protected void findPlaceToBuild(RemotePlayer player) {
        if (player.isPlayer()) {
            // If the player is online, find the world and server with
            // the most free space.
            db.find(SQLHomeWorld.class)
                .eq("wild", true)
                .findListAsync(rows -> findPlaceToBuildCallback(player, rows));
        } else {
            // Otherwise, we assume they were sent from a different
            // server so we only consider the local worlds.
            List<SQLHomeWorld> rows = new ArrayList<>(localHomeWorlds.size());
            for (SQLHomeWorld it : worldList) {
                if (it.isWild() && it.isOnThisServer()) rows.add(it);
            }
            findPlaceToBuildCallback(player, rows);
        }
    }

    /**
     * This is called with a viable list of wild enabled home worlds.
     *
     * If the player is remote, they will all be local worlds because
     * the sending server will already have done their due diligence,
     * see below.
     *
     * If the player is online, this will be a fresh list from the
     * database so we can redirect the command to other servers if
     * needed.
     */
    private void findPlaceToBuildCallback(RemotePlayer player, List<SQLHomeWorld> rows) {
        if (rows.isEmpty()) {
            getLogger().severe("No home worlds configured!");
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator.", RED));
            return;
        }
        final SQLHomeWorld homeWorld;
        if (rows.size() == 1) {
            homeWorld = rows.get(0);
        } else {
            // Pick the most suitable world; favoring the lowest population
            Map<String, Integer> serverPlayerCount = new HashMap<>();
            for (SQLHomeWorld row : rows) {
                serverPlayerCount.put(row.getServer(), 0);
            }
            int totalPlayerCount = 0; // on all home servers
            for (RemotePlayer remote : Connect.get().getRemotePlayers()) {
                String serverName = remote.getOriginServerName();
                if (!serverPlayerCount.containsKey(serverName)) continue;
                serverPlayerCount.put(serverName, serverPlayerCount.get(serverName) + 1);
                totalPlayerCount += 1;
            }
            List<SQLHomeWorld> weightedRows = new ArrayList<>();
            for (SQLHomeWorld row : rows) {
                int weight = Math.max(1, totalPlayerCount - serverPlayerCount.get(row.getServer()));
                for (int i = 0; i < weight; i += 1) {
                    weightedRows.add(row);
                }
            }
            homeWorld = weightedRows.get(random.nextInt(weightedRows.size()));
            getLogger().info("[FindPlaceToBuild]"
                             + " total:" + totalPlayerCount
                             + " map:" + serverPlayerCount
                             + " result:" + homeWorld.getWorld() + "/" + homeWorld.getServer());
        }
        // Send to server
        if (!homeWorld.isOnThisServer() && player.isPlayer()) {
            Connect.get().dispatchRemoteCommand(player.getPlayer(), "wild", homeWorld.getServer());
            return;
        }
        // Send to world
        String worldName = homeWorld.getWorld();
        World bworld = getServer().getWorld(worldName);
        if (bworld == null) {
            getLogger().warning("Home world not found: " + worldName);
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator.", RED));
            return;
        }
        WildTask wildTask = new WildTask(this, bworld, player);
        wildTask.withCooldown();
        if (player.isPlayer()) {
            PluginPlayerEvent.Name.USE_WILD.call(this, player.getPlayer());
        }
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
        return ClaimOperationResult.SUCCESS;
    }

    protected void loadFromDatabase() {
        worldList = db.find(SQLHomeWorld.class).findList();
        localHomeWorlds = new ArrayList<>();
        for (SQLHomeWorld it : worldList) {
            if (it.isOnThisServer()) {
                localHomeWorlds.add(it.getWorld());
            }
        }
        claimCache.clear();
        claimCache.initialize(localHomeWorlds);
        homes.clear();
        for (SQLClaim row : db.find(SQLClaim.class).findList()) {
            Claim claim = new Claim(this, row);
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
                Collections.sort(overlappingClaims, (l, r) -> Integer.compare(r.getBlocks(), l.getBlocks()));
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
        for (SQLClaimTrust trust : db.find(SQLClaimTrust.class).findList()) {
            Claim claim = getClaimById(trust.getClaimId());
            if (claim == null) {
                getLogger().warning("Trust without claim: " + trust);
                db.deleteAsync(trust, null);
                continue;
            }
            if (trust.parseTrustType().isNone()) {
                getLogger().warning("Empty trust: " + trust);
                continue;
            }
            claim.getTrusted().put(trust.getTrustee(), trust);
        }
        for (SQLSubclaim row : db.find(SQLSubclaim.class).findList()) {
            Claim claim = getClaimById(row.getClaimId());
            if (claim == null) {
                getLogger().warning("Subclaim without parent claim: id=" + row.getId() + " claim_id=" + row.getClaimId());
                db.deleteAsync(row, null);
                continue;
            }
            Subclaim subclaim = new Subclaim(this, claim, row);
            claim.getSubclaims().add(subclaim);
        }
        for (SQLHome home : db.find(SQLHome.class).findList()) {
            home.unpack();
            homes.add(home);
        }
        for (SQLHomeInvite invite : db.find(SQLHomeInvite.class).findList()) {
            SQLHome home = homes.findById(invite.getHomeId());
            if (home == null) {
                getLogger().warning("Home invite without home id: " + invite);
                continue;
            }
            home.getInvites().add(invite.getInvitee());
        }
    }

    protected void reloadClaim(int claimId) {
        final Claim oldClaim = getClaimById(claimId);
        db.scheduleAsyncTask(() -> {
                final SQLClaim row = db.find(SQLClaim.class)
                    .eq("id", claimId)
                    .findUnique();
                if (row == null) {
                    if (oldClaim != null) {
                        Bukkit.getScheduler().runTask(this, () -> {
                                claimCache.remove(oldClaim);
                            });
                    }
                    return;
                }
                final List<SQLClaimTrust> claimTrustList = db.find(SQLClaimTrust.class)
                    .eq("claimId", claimId)
                    .findList();
                final List<SQLSubclaim> subclaimList = db.find(SQLSubclaim.class)
                    .eq("claimId", claimId)
                    .findList();
                Bukkit.getScheduler().runTask(this, () -> {
                        Claim claim;
                        if (oldClaim != null) {
                            claim = oldClaim;
                            claim.updateSQLRow(row);
                        } else {
                            claim = new Claim(this, row);
                            claimCache.add(claim);
                        }
                        claim.getTrusted().clear();
                        claim.getSubclaims().clear();
                        for (SQLClaimTrust claimTrustRow : claimTrustList) {
                            claim.getTrusted().put(claimTrustRow.getTrustee(), claimTrustRow);
                        }
                        for (SQLSubclaim subclaimRow : subclaimList) {
                            claim.getSubclaims().add(new Subclaim(this, claim, subclaimRow));
                        }
                    });
            });
    }

    protected void reloadHome(int homeId) {
        SQLHome oldHome = homes.findById(homeId);
        db.scheduleAsyncTask(() -> {
                SQLHome newHome = db.find(SQLHome.class)
                    .eq("id", homeId)
                    .findUnique();
                List<SQLHomeInvite> inviteList = db.find(SQLHomeInvite.class)
                    .eq("homeId", homeId)
                    .findList();
                Bukkit.getScheduler().runTask(this, () -> {
                        if (oldHome != null) homes.remove(oldHome);
                        if (newHome == null) return;
                        newHome.unpack();
                        for (SQLHomeInvite inviteRow : inviteList) {
                            newHome.getInvites().add(inviteRow.getInvitee());
                        }
                        homes.add(newHome);
                    });
            });
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

    public Claim findPrimaryClaim(UUID owner) {
        List<Claim> playerClaims = findClaims(owner);
        return playerClaims.isEmpty() ? null : playerClaims.get(0);
    }

    public boolean hasAClaim(UUID owner) {
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.isPrimaryOwner(owner)) return true;
        }
        return false;
    }

    public List<Claim> findClaims(UUID owner) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.isPrimaryOwner(owner)) list.add(claim);
        }
        Collections.sort(list, (a, b) -> b.getCreated().compareTo(a.getCreated()));
        return list;
    }

    public List<Claim> findClaims(UUID player, TrustType trust) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claimCache.getAllClaims()) {
            if (claim.getTrustType(player).gte(trust)) list.add(claim);
        }
        Collections.sort(list, (a, b) -> b.getCreated().compareTo(a.getCreated()));
        return list;
    }

    public List<Claim> findClaims(Player player) {
        return findClaims(player.getUniqueId());
    }

    public List<Claim> findClaimsInWorld(String w) {
        return claimCache.inWorld(mirrorWorlds.getOrDefault(w, w));
    }

    public void deleteClaim(Claim claim) {
        claimCache.remove(claim);
        claim.setDeleted(true);
        final int claimId = claim.getId();
        db.scheduleAsyncTask(() -> {
                int trustCount = db.find(SQLClaimTrust.class).eq("claimId", claimId).delete();
                int subclaimCount = db.find(SQLSubclaim.class).eq("claimId", claimId).delete();
                int claimCount = db.find(SQLClaim.class).eq("id", claimId).delete();
                getLogger().info("Deleted claim #" + claimId
                                 + " claims=" + claimCount
                                 + " trusted=" + trustCount
                                 + " subclaims=" + subclaimCount);
                Bukkit.getScheduler().runTask(this, () -> connectListener.broadcastClaimUpdate(claim));
            });
    }

    public void deleteHome(SQLHome home) {
        homes.remove(home);
        db.find(SQLHomeInvite.class)
            .eq("home_id", home.getId())
            .deleteAsync(null);
        db.deleteAsync(home, null);
        connectListener.broadcastHomeUpdate(home);
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

    private static final String HOME_IGNORE_PERM = "home.ignore";

    public void ignoreClaims(Player player, boolean value) {
        if (value) {
            Perm.get().set(player.getUniqueId(), HOME_IGNORE_PERM, true);
        } else {
            Perm.get().unset(player.getUniqueId(), HOME_IGNORE_PERM);
        }
    }

    public boolean doesIgnoreClaims(Player player) {
        return player.hasPermission(HOME_IGNORE_PERM);
    }

    public boolean doesIgnoreClaims(UUID uuid) {
        return Perm.get().has(uuid, HOME_IGNORE_PERM);
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

    public SQLHomeWorld findHomeWorld(String worldName) {
        for (SQLHomeWorld it : worldList) {
            if (worldName.equals(it.getWorld())) return it;
        }
        return null;
    }

    private void updateFreeSpace() {
        Map<String, SQLHomeWorld> map = new HashMap<>();
        for (SQLHomeWorld it : worldList) {
            if (!it.isOnThisServer()) continue;
            World world = Bukkit.getWorld(it.getWorld());
            if (world == null) continue;
            double size = world.getWorldBorder().getSize();
            it.setFree((long) (size * size));
            it.setClaims(0);
            map.put(it.getWorld(), it);
        }
        for (Claim claim : claimCache.getAllLocalClaims()) {
            SQLHomeWorld row = map.get(claim.getWorld());
            if (row == null) continue;
            row.setFree(row.getFree() - (long) claim.getArea().size());
            row.setClaims(row.getClaims() + 1);
        }
        for (SQLHomeWorld row : map.values()) {
            db.updateAsync(row, null, "claims", "free");
        }
    }
}
