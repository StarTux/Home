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
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Location;
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
    int blocks;
    long created;
    final Map<Setting, Object> settings = new EnumMap<>(Setting.class);
    int centerX, centerY;
    public static final UUID ADMIN_ID = new UUID(0L, 0L);

    Claim(HomePlugin plugin) {
        this.plugin = plugin;
    }

    Claim(HomePlugin plugin, UUID owner, String world, Area area) {
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
        PUBLIC_INVITE("Anyone can interact with blocks such as doors", false);

        final String key;
        final String displayName;
        final Object defaultValue;

        Setting(String displayName, Object defaultValue) {
            this.key = name().toLowerCase();
            this.displayName = displayName;
            this.defaultValue = defaultValue;
        }
    }

    Object getSetting(Setting setting) {
        Object result = settings.get(setting);
        if (result != null) return result;
        return setting.defaultValue;
    }

    // SQL Interface

    @Data @Table(name = "claims")
    public static final class SQLRow {
        private @Id Integer id;
        @Column(nullable = false) private UUID owner;
        @Column(nullable = false, length = 16) private String world;
        @Column(nullable = false) private Integer ax, ay, bx, by;
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
        Map<String, Object> settingsMap = (Map<String, Object>)JSONValue.parse(row.getSettings());
        settings.clear();
        for (Setting setting : Setting.values()) {
            Object value = settingsMap.get(setting.key);
            if (value != null) settings.put(setting, value);
        }
        @SuppressWarnings("unchecked")
        List<Number> center = (List<Number>)settingsMap.get("center");
        if (center == null) {
            centerX = (area.ax + area.bx) / 2;
            centerY = (area.ay + area.by) / 2;
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

    boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    boolean canBuild(UUID playerId) {
        return isOwner(playerId) || members.contains(playerId);
    }

    boolean canVisit(UUID playerId) {
        return canBuild(playerId) || visitors.contains(playerId);
    }

    boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
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
}
