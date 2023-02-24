package com.cavetale.home;

import com.cavetale.core.perm.Perm;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.util.Json;
import com.cavetale.home.sql.SQLClaim;
import com.cavetale.home.sql.SQLClaimTrust;
import com.cavetale.home.struct.BlockVector;
import com.cavetale.home.struct.Vec2i;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import static com.cavetale.home.ConnectListener.broadcastClaimUpdate;

public final class Claim {
    public static final UUID ADMIN_ID = new UUID(0L, 0L);
    private final HomePlugin plugin;

    @Getter private SQLClaim row;
    @Getter private Area area;
    private final Map<ClaimSetting, Boolean> settings = new EnumMap<>(ClaimSetting.class);
    @Getter private int centerX;
    @Getter private int centerY;

    @Getter private final Map<UUID, SQLClaimTrust> trusted = new HashMap<>();
    @Getter private final List<Subclaim> subclaims = new ArrayList<>();
    @Setter private boolean deleted;

    /**
     * Existing claim constructor.
     * Store the row and fill all caches.
     */
    public Claim(final HomePlugin plugin, final SQLClaim row) {
        this.plugin = plugin;
        this.row = row;
        loadSQLRow();
    }

    /**
     * New claim constructor.
     * Create a row and enter the values.
     */
    public Claim(final HomePlugin plugin, final UUID owner, final String world, final Area area) {
        this.plugin = plugin;
        this.row = new SQLClaim(owner, world, area);
        this.area = area;
        this.centerX = area.centerX();
        this.centerY = area.centerY();
        for (ClaimSetting setting : ClaimSetting.values()) {
            settings.put(setting, setting.defaultValue);
        }
        row.setSettings(serializeSettings());
    }

    private String serializeSettings() {
        Map<String, Object> settingsMap = new LinkedHashMap<>();
        for (Map.Entry<ClaimSetting, Boolean> entry : settings.entrySet()) {
            ClaimSetting setting = entry.getKey();
            boolean value = entry.getValue();
            if (value == setting.defaultValue) continue;
            settingsMap.put(setting.key, value);
        }
        settingsMap.put("center", List.of(centerX, centerY));
        return Json.serialize(settingsMap);
    }

    private void loadSQLRow() {
        this.area = row.getArea();
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsMap = (Map<String, Object>) Json.deserialize(row.getSettings(), Map.class);
        settings.clear();
        for (ClaimSetting setting : ClaimSetting.values()) {
            if (settingsMap.get(setting.key) instanceof Boolean value) {
                settings.put(setting, value != null ? value : setting.defaultValue);
            }
        }
        if (settingsMap.get("center") instanceof List list
            && list.size() == 2
            && list.get(0) instanceof Number x
            && list.get(1) instanceof Number y) {
            centerX = x.intValue();
            centerY = y.intValue();
        } else {
            centerX = area.centerX();
            centerY = area.centerY();
        }
    }

    public void updateSQLRow(SQLClaim newRow) {
        Area newArea = newRow.getArea();
        if (!area.equals(newArea)) {
            loadArea(newArea);
        }
        row = newRow;
        loadSQLRow();
    }

    public void insertIntoDatabase(Consumer<Boolean> callback) {
        plugin.db.insertAsync(row, res -> {
                if (res != 0) broadcastClaimUpdate(this);
                callback.accept(res != 0);
            });
    }

    public int getId() {
        return row.getId();
    }

    public UUID getOwner() {
        return row.getOwner();
    }

    public void setOwner(UUID uuid) {
        row.setOwner(uuid);
        plugin.db.saveAsync(row, res -> broadcastClaimUpdate(this), "owner");
    }

    public String getWorld() {
        return row.getWorld();
    }

    public int getBlocks() {
        return row.getBlocks();
    }

    public void setBlocks(int blocks) {
        if (row.getBlocks() == blocks) return;
        row.setBlocks(blocks);
        plugin.db.updateAsync(row, res -> broadcastClaimUpdate(this), "blocks");
    }

    public String getName() {
        return row.getName();
    }

    public void setName(String name) {
        row.setName(name);
        plugin.db.updateAsync(row, res -> broadcastClaimUpdate(this), "name");
    }

    public Date getCreated() {
        return row.getCreated();
    }

    private void loadArea(Area newArea) {
        if (area.equals(newArea)) return;
        Area oldArea = area;
        area = newArea;
        plugin.claimCache.resize(this, oldArea, newArea);
    }

    public void setArea(Area newArea) {
        loadArea(newArea);
        row.setArea(area);
        plugin.db.updateAsync(row, res -> broadcastClaimUpdate(this), "ax", "bx", "ay", "by");
    }

    public boolean getSetting(ClaimSetting setting) {
        return settings.getOrDefault(setting, setting.defaultValue);
    }

    public void setSetting(ClaimSetting setting, boolean value) {
        if (getSetting(setting) == value) return;
        settings.put(setting, value);
        row.setSettings(serializeSettings());
        plugin.db.updateAsync(row, res -> broadcastClaimUpdate(this), "settings");
    }

    public static boolean ownsAdminClaims(Player player) {
        return player.hasPermission("home.adminclaims");
    }

    public TrustType getPublicTrust() {
        if (getSetting(ClaimSetting.PUBLIC)) return TrustType.BUILD;
        if (getSetting(ClaimSetting.PUBLIC_CONTAINER)) return TrustType.CONTAINER;
        if (getSetting(ClaimSetting.PUBLIC_INVITE)) return TrustType.INTERACT;
        return TrustType.NONE;
    }

    public TrustType getTrustType(UUID uuid) {
        if (plugin.doesIgnoreClaims(uuid)) return TrustType.OWNER;
        if (getOwner().equals(uuid)) return TrustType.OWNER;
        if (isAdminClaim() && Perm.get().has(uuid, "home.adminclaims")) return TrustType.OWNER;
        SQLClaimTrust entry = trusted.get(uuid);
        TrustType playerTrustType = entry != null ? entry.parseTrustType() : TrustType.NONE;
        if (playerTrustType.isBan()) return playerTrustType;
        return playerTrustType.max(getPublicTrust());
    }

    public TrustType getTrustType(Player player) {
        return getTrustType(player.getUniqueId());
    }

    public boolean setTrustType(UUID uuid, TrustType trustType) {
        if (trustType.isNone()) return removeTrust(uuid);
        SQLClaimTrust oldRow = trusted.get(uuid);
        if (oldRow != null) {
            if (oldRow.parseTrustType() == trustType) return false;
            trusted.remove(uuid);
            plugin.getDb().deleteAsync(oldRow, null);
        }
        SQLClaimTrust newRow = new SQLClaimTrust(this, trustType, uuid);
        trusted.put(uuid, newRow);
        plugin.getDb().insertAsync(newRow, res -> plugin.getConnectListener().broadcastClaimUpdate(this));
        return true;
    }

    public boolean removeTrust(UUID uuid) {
        SQLClaimTrust oldRow = trusted.get(uuid);
        if (oldRow == null) return false;
        trusted.remove(uuid);
        plugin.getDb().deleteAsync(oldRow, res -> plugin.getConnectListener().broadcastClaimUpdate(this));
        return true;
    }

    public TrustType getTrustType(UUID uuid, BlockVector vec) {
        TrustType claimTrustType = getTrustType(uuid);
        if (claimTrustType.isBan()) return TrustType.BAN;
        if (claimTrustType.isCoOwner()) return claimTrustType;
        Subclaim subclaim = getSubclaimAt(vec);
        if (subclaim == null) return claimTrustType;
        return getSetting(ClaimSetting.INHERITANCE)
            ? claimTrustType.max(subclaim.getTrustType(uuid))
            : subclaim.getTrustType(uuid);
    }

    public TrustType getTrustType(UUID uuid, Block block) {
        TrustType claimTrustType = getTrustType(uuid);
        if (claimTrustType.isBan()) return TrustType.BAN;
        if (claimTrustType.isCoOwner()) return claimTrustType;
        Subclaim subclaim = getSubclaimAt(block);
        if (subclaim == null) return claimTrustType;
        return getSetting(ClaimSetting.INHERITANCE)
            ? claimTrustType.max(subclaim.getTrustType(uuid))
            : subclaim.getTrustType(uuid);
    }

    public TrustType getTrustType(Player player, BlockVector vec) {
        return getTrustType(player.getUniqueId(), vec);
    }

    public boolean isPrimaryOwner(UUID uuid) {
        return uuid.equals(getOwner());
    }

    public boolean isOwner(Player player) {
        return getTrustType(player).isOwner();
    }

    public boolean isOwner(UUID player) {
        return getTrustType(player).isOwner();
    }

    public boolean canBuild(Player player, BlockVector vec) {
        return getTrustType(player.getUniqueId(), vec).canBuild();
    }

    public boolean canBuild(UUID uuid, BlockVector vec) {
        return getTrustType(uuid, vec).canBuild();
    }

    public boolean isPvPAllowed(BlockVector vec) {
        return getSetting(ClaimSetting.PVP);
    }

    public boolean isInWorld(String worldName) {
        return getWorld().equals(worldName);
    }

    public boolean isAdminClaim() {
        return getOwner().equals(ADMIN_ID);
    }

    public String getOwnerName() {
        if (isAdminClaim()) return "admin";
        return PlayerCache.nameForUuid(getOwner());
    }

    public String getOwnerGenitive() {
        String result = getOwnerName();
        return result.endsWith("s") ? result + "'" : result + "'s";
    }

    public boolean contains(Location location) {
        String lwn = location.getWorld().getName();
        if (!getWorld().equals(lwn) && !getWorld().equals(plugin.getMirrorWorlds().get(lwn))) return false;
        return area.contains(location.getBlockX(), location.getBlockZ());
    }

    public boolean isHidden() {
        return getSetting(ClaimSetting.HIDDEN);
    }

    public Subclaim getSubclaim(int subclaimId) {
        for (Subclaim subclaim : subclaims) {
            if (subclaim.getId() == subclaimId) return subclaim;
        }
        return null;
    }

    public List<Subclaim> getSubclaims(World inWorld) {
        return getSubclaims(inWorld.getName());
    }

    public List<Subclaim> getSubclaims(String inWorld) {
        List<Subclaim> list = new ArrayList<>();
        for (Subclaim subclaim : subclaims) {
            if (subclaim.getWorld().equals(inWorld)) list.add(subclaim);
        }
        return list;
    }

    public Subclaim getSubclaimAt(Location loc) {
        return getSubclaimAt(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
    }

    public Subclaim getSubclaimAt(Block block) {
        return getSubclaimAt(block.getWorld().getName(), block.getX(), block.getZ());
    }

    public Subclaim getSubclaimAt(BlockVector at) {
        return getSubclaimAt(at.world, at.x, at.z);
    }

    public Subclaim getSubclaimAt(String inWorld, int x, int z) {
        for (Subclaim subclaim : subclaims) {
            if (!subclaim.getWorld().equals(inWorld)) continue;
            if (subclaim.getArea().contains(x, z)) return subclaim;
        }
        return null;
    }

    public boolean removeSubclaim(Subclaim subclaim) {
        return subclaims.remove(subclaim);
    }

    public ClaimOperationResult growTo(int x, int z) {
        Area newArea = area.growTo(x, z);
        if (getBlocks() < newArea.size()) return ClaimOperationResult.INSUFFICIENT_BLOCKS;
        for (Claim other : plugin.findClaimsInWorld(getWorld())) {
            if (other != this && other.getArea().overlaps(newArea)) {
                return ClaimOperationResult.OVERLAP;
            }
        }
        setArea(newArea);
        return ClaimOperationResult.SUCCESS;
    }

    public List<PlayerCache> listPlayers(Predicate<TrustType> predicate) {
        List<PlayerCache> result = new ArrayList<>();
        for (SQLClaimTrust trustedRow : trusted.values()) {
            if (!predicate.test(trustedRow.parseTrustType())) continue;
            PlayerCache playerCache = PlayerCache.forUuid(trustedRow.getTrustee());
            if (playerCache == null) continue;
            result.add(playerCache);
        }
        return result;
    }

    public void kick(Player player) {
        World w = player.getWorld();
        if (!isInWorld(w.getName())) return;
        Vec2i vec = area.getNearestOutside(Vec2i.of(player.getLocation()));
        w.getChunkAtAsync(vec.x >> 4, vec.y >> 4, (Consumer<Chunk>) chunk -> {
                Location target = w.getHighestBlockAt(vec.x, vec.y).getLocation().add(0.5, 1.0, 0.5);
                target.setDirection(player.getLocation().getDirection());
                player.teleport(target, TeleportCause.PLUGIN);
            });
    }

    public boolean isValid() {
        return getId() > 0 && !deleted;
    }
}
