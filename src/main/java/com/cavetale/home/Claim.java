package com.cavetale.home;

import com.cavetale.core.util.Json;
import com.cavetale.home.struct.BlockVector;
import com.cavetale.home.struct.Vec2i;
import com.winthier.perm.Perm;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

@Data
public final class Claim {
    public static final UUID ADMIN_ID = new UUID(0L, 0L);
    protected final HomePlugin plugin;
    protected int id;
    protected UUID owner;
    protected String world;
    protected Area area;
    protected final Map<UUID, ClaimTrust> trusted = new HashMap<>();
    protected final List<Subclaim> subclaims = new ArrayList<>();
    protected int blocks;
    protected long created;
    protected final Map<Setting, Object> settings = new EnumMap<>(Setting.class);
    protected int centerX;
    protected int centerY;
    protected String name;
    protected boolean deleted;

    public Claim(final HomePlugin plugin) {
        this.plugin = plugin;
    }

    public Claim(final HomePlugin plugin, final UUID owner, final String world, final Area area) {
        this.plugin = plugin;
        this.owner = owner;
        this.world = world;
        this.area = area;
        this.blocks = area.size();
        this.created = System.currentTimeMillis();
        this.centerX = (area.ax + area.bx) / 2;
        this.centerY = (area.ay + area.by) / 2;
    }

    public enum Setting {
        PVP("PvP Combat", false),
        EXPLOSIONS("Explosion damage", false),
        FIRE("Fire burns blocks", false),
        AUTOGROW("Claim grows automatically", true),
        PUBLIC("Anyone can build", false),
        PUBLIC_CONTAINER("Anyone can open containers", false),
        PUBLIC_INVITE("Anyone can interact", false),
        SHOW_BORDERS("Show claim borders", true),
        ELYTRA("Allow flight", true),
        ENDER_PEARL("Allow ender teleport", true),
        INHERITANCE("Subclaims inherit claim trust", true),
        // Admin only
        HIDDEN("Hide this claim", false),
        MOB_SPAWNING("Allow mob spawning", true);

        public final String key;
        public final String displayName;
        public final Object defaultValue;

        Setting(final String displayName, final Object defaultValue) {
            this.key = name().toLowerCase();
            this.displayName = displayName;
            this.defaultValue = defaultValue;
        }

        public boolean isAdminOnly() {
            switch (this) {
            case HIDDEN:
            case MOB_SPAWNING:
                return true;
            default:
                return false;
            }
        }
    }

    public Object getSetting(Setting setting) {
        Object result = settings.get(setting);
        return result != null
            ? result
            : setting.defaultValue;
    }

    public boolean getBoolSetting(Setting setting) {
        Object result = settings.get(setting);
        if (result == null) result = setting.defaultValue;
        return result == Boolean.TRUE;
    }

    public void setArea(Area newArea) {
        Area oldArea = this.area;
        this.area = newArea;
        if (oldArea != null) {
            plugin.claimCache.resize(this, oldArea, newArea);
        }
    }

    // SQL Interface

    @Data @Table(name = "claims")
    public static final class SQLRow {
        private @Id Integer id;
        @Column(nullable = false) private UUID owner;
        @Column(nullable = false, length = 16) private String world;
        @Column(nullable = false) private Integer ax;
        @Column(nullable = false) private Integer ay;
        @Column(nullable = false) private Integer bx;
        @Column(nullable = false) private Integer by;
        @Column(nullable = false) private Integer blocks;
        @Column(nullable = false, length = 255) private String settings;
        @Column(nullable = true, length = 255) private String name;
        @Column(nullable = false) private Date created;
    }

    public SQLRow toSQLRow() {
        SQLRow row = new SQLRow();
        if (this.id > 0) row.id = this.id;
        row.owner = this.owner;
        row.world = this.world;
        row.ax = this.area.ax;
        row.ay = this.area.ay;
        row.bx = this.area.bx;
        row.by = this.area.by;
        row.blocks = this.blocks;
        row.created = new Date(created);
        Map<String, Object> settingsMap = new LinkedHashMap<>();
        for (Map.Entry<Setting, Object> setting : settings.entrySet()) {
            settingsMap.put(setting.getKey().name().toLowerCase(), setting.getValue());
        }
        settingsMap.put("center", Arrays.asList(centerX, centerY));
        row.settings = Json.serialize(settingsMap);
        row.name = name;
        return row;
    }

    public void loadSQLRow(SQLRow row) {
        this.id = row.id;
        this.owner = row.owner;
        this.world = row.world;
        this.area = new Area(row.ax, row.ay, row.bx, row.by);
        this.blocks = row.blocks;
        this.created = row.getCreated().getTime();
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsMap = (Map<String, Object>) Json.deserialize(row.getSettings(), Map.class);
        settings.clear();
        for (Setting setting : Setting.values()) {
            Object value = settingsMap.get(setting.key);
            if (value != null) settings.put(setting, value);
        }
        @SuppressWarnings("unchecked")
        List<Number> center = (List<Number>) settingsMap.get("center");
        if (center == null) {
            centerX = area.centerX();
            centerY = area.centerY();
        } else {
            centerX = center.get(0).intValue();
            centerY = center.get(1).intValue();
        }
        name = row.getName();
    }

    public void saveToDatabase() {
        SQLRow row = toSQLRow();
        plugin.getDb().save(row);
        this.id = row.id;
    }

    // Utility

    public static boolean ownsAdminClaims(Player player) {
        return player.hasPermission("home.adminclaims");
    }

    public TrustType getPublicTrust() {
        if (getBoolSetting(Setting.PUBLIC)) return TrustType.BUILD;
        if (getBoolSetting(Setting.PUBLIC_CONTAINER)) return TrustType.CONTAINER;
        if (getBoolSetting(Setting.PUBLIC_INVITE)) return TrustType.INTERACT;
        return TrustType.NONE;
    }

    public TrustType getTrustType(UUID uuid) {
        if (uuid.equals(owner)) return TrustType.OWNER;
        if (isAdminClaim() && Perm.has(uuid, "home.adminclaims")) return TrustType.OWNER;
        ClaimTrust entry = trusted.get(uuid);
        TrustType playerTrustType = entry != null ? entry.parseTrustType() : TrustType.NONE;
        if (playerTrustType.isBan()) return playerTrustType;
        return playerTrustType.max(getPublicTrust());
    }

    public TrustType getTrustType(Player player) {
        if (plugin.doesIgnoreClaims(player)) return TrustType.OWNER;
        return getTrustType(player.getUniqueId());
    }

    public TrustType getTrustType(UUID uuid, BlockVector vec) {
        TrustType claimTrustType = getTrustType(uuid);
        if (claimTrustType.isBan()) return TrustType.BAN;
        if (claimTrustType.isCoOwner()) return claimTrustType;
        Subclaim subclaim = getSubclaimAt(vec);
        if (subclaim == null) return claimTrustType;
        return getBoolSetting(Setting.INHERITANCE)
            ? claimTrustType.max(subclaim.getTrustType(uuid))
            : subclaim.getTrustType(uuid);
    }

    public TrustType getTrustType(Player player, BlockVector vec) {
        return getTrustType(player.getUniqueId(), vec);
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
        return getBoolSetting(Setting.PVP);
    }

    public boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
    }

    public boolean isAdminClaim() {
        return ADMIN_ID.equals(owner);
    }

    public String getOwnerName() {
        if (ADMIN_ID.equals(owner)) return "admin";
        return PlayerCache.nameForUuid(owner);
    }

    public String getOwnerGenitive() {
        String result = getOwnerName();
        return result.endsWith("s") ? result + "'" : result + "'s";
    }

    public boolean contains(Location location) {
        String lwn = location.getWorld().getName();
        if (!world.equals(lwn) && !world.equals(plugin.getMirrorWorlds().get(lwn))) return false;
        return area.contains(location.getBlockX(), location.getBlockZ());
    }

    public boolean isHidden() {
        return getBoolSetting(Setting.HIDDEN);
    }

    public Subclaim getSubclaim(int subclaimId) {
        for (Subclaim subclaim : subclaims) {
            if (subclaim.getId() == subclaimId) return subclaim;
        }
        return null;
    }

    public List<Subclaim> getSubclaims() {
        return new ArrayList<>(subclaims);
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

    /**
     * Used for subclaim creation or loading at startup.
     */
    public void addSubclaim(Subclaim subclaim) {
        subclaims.add(subclaim);
    }

    public boolean removeSubclaim(Subclaim subclaim) {
        return subclaims.remove(subclaim);
    }

    public ClaimOperationResult growTo(int x, int z) {
        int ax = Math.min(area.ax, x);
        int ay = Math.min(area.ay, z);
        int bx = Math.max(area.bx, x);
        int by = Math.max(area.by, z);
        Area newArea = new Area(ax, ay, bx, by);
        if (getBlocks() < newArea.size()) return ClaimOperationResult.INSUFFICIENT_BLOCKS;
        for (Claim other : plugin.findClaimsInWorld(world)) {
            if (other != this && other.getArea().overlaps(newArea)) {
                return ClaimOperationResult.OVERLAP;
            }
        }
        setArea(newArea);
        return ClaimOperationResult.SUCCESS;
    }

    public List<PlayerCache> listPlayers(Predicate<TrustType> predicate) {
        List<PlayerCache> result = new ArrayList<>();
        for (ClaimTrust row : trusted.values()) {
            if (!predicate.test(row.parseTrustType())) continue;
            PlayerCache playerCache = PlayerCache.forUuid(row.getTrustee());
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
        return !deleted;
    }
}
