package com.cavetale.home;

import com.cavetale.core.util.Json;
import com.winthier.generic_events.GenericEvents;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Subclaim {
    private Integer id = null;
    private final HomePlugin plugin;
    @Getter private final Claim parent;
    @Getter private String world;
    @Getter private Area area;
    private Tag tag;
    public static final UUID PUBLIC_UUID = new UUID(0, 0);

    /**
     * Base constructor.
     */
    public Subclaim(final HomePlugin plugin, final Claim parent, final String world, final Area area, final Tag tag) {
        this.plugin = plugin;
        this.parent = parent;
        this.world = world;
        this.area = area;
        this.tag = tag;
    }

    /**
     * Constructor to load from database. (Parent claim resolution is
     * external!)
     */
    public Subclaim(final HomePlugin plugin, final Claim parent, final SQLRow row) {
        this(plugin, parent, row.world,
             new Area(row.ax, row.ay, row.bx, row.by),
             Json.deserialize(row.getTag(), Tag.class, Tag::new));
        this.id = row.id;
    }

    /**
     * Constructor for a new subclaim.
     */
    public Subclaim(final HomePlugin plugin, final Claim parent, final World world, final Area area) {
        this(plugin, parent, world.getName(), area, new Tag());
    }

    public enum Trust {
        NONE,
        ACCESS,
        CONTAINER,
        BUILD,
        CO_OWNER("Co-owner"),
        OWNER;

        public final String displayName;

        Trust(final String displayName) {
            this.displayName = displayName;
        }

        Trust() {
            displayName = name().substring(0, 1) + name().substring(1).toLowerCase();
        }

        public boolean entails(Trust other) {
            return ordinal() >= other.ordinal();
        }

        public boolean exceeds(Trust other) {
            return ordinal() > other.ordinal();
        }
    }

    public static final class Tag {
        Map<UUID, Trust> trusted = new HashMap<>();
    }

    @Getter @Setter @Table(name = "subclaims")
    @AllArgsConstructor @NoArgsConstructor
    public static final class SQLRow {
        private @Id Integer id;
        @Column(nullable = false) private int claimId;
        @Column(nullable = false) private String world;
        @Column(nullable = false) private int ax;
        @Column(nullable = false) private int ay;
        @Column(nullable = false) private int bx;
        @Column(nullable = false) private int by;
        @Column(nullable = false, length = 4096) private String tag;
    }

    public String getListInfo() {
        return plugin.worldDisplayName(world) + " " + area;
    }

    public boolean isInWorld(World inWorld) {
        return inWorld.getName().equals(world);
    }

    public SQLRow toSQLRow() {
        return new SQLRow(id, parent.getId(), world,
                          area.ax, area.ay, area.bx, area.by,
                          Json.serialize(tag));
    }

    public void saveToDatabase() {
        SQLRow row = toSQLRow();
        plugin.db.save(row);
        this.id = row.id;
    }

    public List<UUID> getTrustedUuids() {
        return new ArrayList<>(tag.trusted.keySet());
    }

    public Map<Trust, Set<UUID>> getTrustedMap() {
        Map<Trust, Set<UUID>> map = new EnumMap<>(Trust.class);
        for (Map.Entry<UUID, Trust> entry : tag.trusted.entrySet()) {
            UUID uuid = entry.getKey();
            Trust trust = entry.getValue();
            Set<UUID> set = map.computeIfAbsent(trust, t -> new HashSet<>());
            set.add(uuid);
        }
        return map;
    }

    public Trust getTrust(Player player) {
        if (parent.isOwner(player)) return Trust.OWNER;
        return getTrust(player.getUniqueId());
    }

    public Trust getTrust(UUID uuid) {
        if (parent.isOwner(uuid)) return Trust.OWNER;
        Trust trust = tag.trusted.get(uuid);
        if (trust != null) return trust;
        trust = tag.trusted.get(PUBLIC_UUID);
        if (trust != null) return trust;
        return Trust.NONE;
    }

    public void setTrust(UUID uuid, Trust trust) {
        tag.trusted.put(uuid, trust);
    }

    public Trust removeTrust(UUID uuid) {
        return tag.trusted.remove(uuid);
    }

    public static UUID cachedPlayerUuid(String name) {
        if (name.startsWith("*")) return PUBLIC_UUID;
        return GenericEvents.cachedPlayerUuid(name);
    }

    public static String cachedPlayerName(UUID uuid) {
        if (uuid.equals(PUBLIC_UUID)) return "*Everybody*";
        return GenericEvents.cachedPlayerName(uuid);
    }
}
