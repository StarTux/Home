package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.json.simple.JSONValue;

@Data
final class Claim {
    final HomePlugin plugin;
    Integer id;
    UUID owner;
    String world;
    Area area;
    final List<UUID> members = new ArrayList<>(); // Can build
    final List<UUID> visitors = new ArrayList<>(); // Can visit
    final List<Subclaim> subclaims = new ArrayList<>();
    int blocks;
    long created;
    final Map<Setting, Object> settings = new EnumMap<>(Setting.class);
    int centerX;
    int centerY;
    public static final UUID ADMIN_ID = new UUID(0L, 0L);

    Claim(final HomePlugin plugin) {
        this.plugin = plugin;
    }

    Claim(final HomePlugin plugin, final UUID owner, final String world, final Area area) {
        this.plugin = plugin;
        this.owner = owner;
        this.world = world;
        this.area = area;
        this.blocks = area.size();
        this.created = System.currentTimeMillis();
        this.centerX = (area.ax + area.bx) / 2;
        this.centerY = (area.ay + area.by) / 2;
    }

    enum Setting {
        PVP("PvP Combat", false),
        EXPLOSIONS("Explosion Damage", false),
        FIRE("Fire Burns Blocks", false),
        AUTOGROW("Claim Grows Automatically", true),
        PUBLIC("Anyone can build", false),
        PUBLIC_INVITE("Anyone can interact with blocks such as doors", false),
        SHOW_BORDERS("Show claim borders as you enter or leave", true),
        // Admin only
        HIDDEN("Hide this claim", false),
        ELYTRA("Allow elytra flight", false),
        ENDER_PEARL("Allow ender pearls", false),
        MOB_SPAWNING("Allow mob spawning", true);

        final String key;
        final String displayName;
        final Object defaultValue;

        Setting(final String displayName, final Object defaultValue) {
            this.key = name().toLowerCase();
            this.displayName = displayName;
            this.defaultValue = defaultValue;
        }

        boolean isAdminOnly() {
            switch (this) {
            case HIDDEN:
            case ELYTRA:
            case ENDER_PEARL:
            case MOB_SPAWNING:
                return true;
            default:
                return false;
            }
        }
    }

    Object getSetting(Setting setting) {
        Object result = settings.get(setting);
        return result != null
            ? result
            : setting.defaultValue;
    }

    boolean getBoolSetting(Setting setting) {
        Object result = settings.get(setting);
        if (result == null) result = setting.defaultValue;
        return result == Boolean.TRUE;
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
        @Column(nullable = false) private Date created;
    }

    SQLRow toSQLRow() {
        SQLRow row = new SQLRow();
        row.id = this.id;
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
        row.settings = JSONValue.toJSONString(settingsMap);
        return row;
    }

    void loadSQLRow(SQLRow row) {
        this.id = row.id;
        this.owner = row.owner;
        this.world = row.world;
        this.area = new Area(row.ax, row.ay, row.bx, row.by);
        this.blocks = row.blocks;
        this.created = row.getCreated().getTime();
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsMap = (Map<String, Object>) JSONValue.parse(row.getSettings());
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
    }

    void saveToDatabase() {
        SQLRow row = toSQLRow();
        plugin.getDb().save(row);
        this.id = row.id;
    }

    // Utility

    static boolean ownsAdminClaims(Player player) {
        return player.hasPermission("home.adminclaims");
    }

    boolean isOwner(Player player) {
        if (plugin.doesIgnoreClaims(player)) return true;
        if (isAdminClaim() && ownsAdminClaims(player)) return true;
        return isOwner(player.getUniqueId());
    }

    boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    boolean canBuild(Player player) {
        if (plugin.doesIgnoreClaims(player)) return true;
        if (isAdminClaim() && ownsAdminClaims(player)) return true;
        return canBuild(player.getUniqueId());
    }

    boolean canBuild(UUID playerId) {
        return isOwner(playerId) || members.contains(playerId);
    }

    boolean canVisit(Player player) {
        if (plugin.doesIgnoreClaims(player)) return true;
        if (isOwner(player)) return true;
        if (!isAdminClaim() && getBoolSetting(Setting.PUBLIC_INVITE)) {
            return true;
        }
        return canVisit(player.getUniqueId());
    }

    boolean canVisit(UUID playerId) {
        return canBuild(playerId) || visitors.contains(playerId);
    }

    boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
    }

    boolean isAdminClaim() {
        return ADMIN_ID.equals(owner);
    }

    String getOwnerName() {
        if (ADMIN_ID.equals(owner)) return "admin";
        return GenericEvents.cachedPlayerName(owner);
    }

    boolean contains(Location location) {
        String lwn = location.getWorld().getName();
        if (!world.equals(lwn) && !world.equals(plugin.getMirrorWorlds().get(lwn))) return false;
        return area.contains(location.getBlockX(), location.getBlockZ());
    }

    boolean isHidden() {
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
        return subclaims.stream()
            .filter(s -> s.getWorld().equals(inWorld))
            .collect(Collectors.toList());
    }

    public Subclaim getSubclaimAt(Location loc) {
        return getSubclaimAt(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
    }

    public Subclaim getSubclaimAt(Block block) {
        return getSubclaimAt(block.getWorld().getName(), block.getX(), block.getZ());
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
}
