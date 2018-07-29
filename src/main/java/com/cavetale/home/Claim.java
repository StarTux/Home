package com.cavetale.home;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
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
    final Map<Setting, Object> settings = new EnumMap<>(Setting.class);
    int blocks;

    Claim(HomePlugin plugin) {
        this.plugin = plugin;
    }

    Claim(HomePlugin plugin, UUID owner, String world, Area area) {
        this.plugin = plugin;
        this.owner = owner;
        this.world = world;
        this.area = area;
        this.blocks = area.size();
    }

    enum Setting {
        PVP,
        EXPLOSIONS,
        FIRE,
        AUTOGROW;

        final Object defaultValue;

        Setting() {
            this(false);
        }

        Setting(Object defaultValue) {
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
    static final class SQLRow {
        private @Id Integer id;
        @Column(nullable = false) private UUID owner;
        @Column(nullable = false) private String world;
        @Column(nullable = false) private Integer ax, ay, bx, by;
        @Column(nullable = false) private Integer blocks;
        @Column(nullable = false) private String settings;
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
        Map<String, Object> settingsMap = new LinkedHashMap<>();
        for (Map.Entry<Setting, Object> setting: settings.entrySet()) {
            settingsMap.put(setting.getKey().name().toLowerCase(), setting.getValue());
        }
        row.settings = JSONValue.toJSONString(settingsMap);
        return row;
    }

    void loadSQLRow(SQLRow row) {
        this.id = row.id;
        this.owner = row.owner;
        this.world = row.world;
        this.area = new Area(row.ax, row.ay, row.bx, row.by);
        this.blocks = row.blocks;
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsMap = (Map<String, Object>)JSONValue.parse(row.getSettings());
        settings.clear();
        for (Map.Entry<String, Object> setting: settingsMap.entrySet()) {
            try {
                settings.put(Setting.valueOf(setting.getKey().toUpperCase()), setting.getValue());
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }
    }

    void saveToDatabase() {
        SQLRow row = toSQLRow();
        plugin.getDb().save(row);
        this.id = row.id;
    }

    // Utility

    boolean canBuild(UUID playerId) {
        return owner.equals(playerId) || members.contains(playerId);
    }

    boolean canVisit(UUID playerId) {
        return owner.equals(playerId) || members.contains(playerId) || visitors.contains(playerId);
    }
}
