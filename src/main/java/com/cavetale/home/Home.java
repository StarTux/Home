package com.cavetale.home;

import com.cavetale.core.util.Json;
import com.winthier.generic_events.GenericEvents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Data
@Table(name = "homes",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"owner", "name"}),
                            @UniqueConstraint(columnNames = {"public_name"})})
public final class Home {
    @Id Integer id;
    @Column(nullable = false) UUID owner;
    @Column(nullable = true, length = 32) String name;
    @Column(nullable = false) Date created;
    @Column(nullable = false, length = 32) String world;
    @Column(nullable = false) double x;
    @Column(nullable = false) double y;
    @Column(nullable = false) double z;
    @Column(nullable = false) double pitch;
    @Column(nullable = false) double yaw;
    @Column(nullable = true, length = 32) String publicName;
    @Column(nullable = true, length = 4096) String json;
    final transient List<UUID> invites = new ArrayList<>();
    transient Tag tag = new Tag();

    public static final class Tag {
        Map<UUID, Long> visited = new HashMap<>(); // User=>Epoch
        int visitScore = 0;
    }

    /**
     * SQL constructor.
     */
    public Home() { }

    /**
     * Constructor for new homes.
     */
    Home(final UUID owner, final Location location, final String name) {
        this.owner = owner;
        this.name = name;
        this.created = new Date();
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = (double) location.getPitch();
        this.yaw = (double) location.getYaw();
    }

    // Bukkit

    void setLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = (double) location.getPitch();
        this.yaw = (double) location.getYaw();
    }

    public Location createLocation() {
        World bw = Bukkit.getServer().getWorld(world);
        if (bw == null) return null;
        return new Location(bw, x, y, z, (float) yaw, (float) pitch);
    }

    boolean isOwner(UUID playerId) {
        return this.owner.equals(playerId);
    }

    boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
    }

    boolean isInvited(UUID playerId) {
        return invites.contains(playerId);
    }

    // Supports null check for primary homes
    boolean isNamed(String homeName) {
        if ((name == null) != (homeName == null)) return false;
        if (name == null) return true;
        return name.equalsIgnoreCase(homeName);
    }

    public String getOwnerName() {
        if (owner == null) return "N/A";
        String result = GenericEvents.cachedPlayerName(owner);
        if (result != null) return result;
        return "N/A";
    }

    public void unpack() {
        tag = Json.deserialize(json, Tag.class, Tag::new);
    }

    public void pack() {
        json = Json.serialize(tag);
    }

    public void onVisit(HomePlugin plugin, UUID visitor) {
        tag.visited.put(visitor, Instant.now().getEpochSecond());
        tag.visitScore = tag.visited.size();
        pack();
        plugin.db.saveAsync(this, null);
    }

    public int getVisitScore() {
        if (tag == null) return 0;
        return tag.visitScore;
    }

    public static int rank(Home a, Home b) {
        // Highest score
        int v = Integer.compare(b.getVisitScore(), a.getVisitScore());
        if (v != 0) return v;
        // Earliest
        return Long.compare(a.created.getTime(), b.created.getTime());
    }
}
