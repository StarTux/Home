package com.cavetale.home;

import com.cavetale.core.util.Json;
import com.cavetale.home.struct.BlockVector;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
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
public final class Home implements SQLRow {
    @Id
    protected Integer id;
    @Column(nullable = false)
    protected UUID owner;
    @Column(nullable = true, length = 32)
    protected String name;
    @Column(nullable = false)
    protected Date created;
    @Column(nullable = false, length = 32)
    protected String world;
    @Column(nullable = false)
    protected double x;
    @Column(nullable = false)
    protected double y;
    @Column(nullable = false)
    protected double z;
    @Column(nullable = false)
    protected double pitch;
    @Column(nullable = false)
    protected double yaw;
    @Column(nullable = true, length = 32)
    protected String publicName;
    @Column(nullable = false)
    protected int score;
    @Column(nullable = true, length = 4096)
    protected String json;
    protected final transient List<UUID> invites = new ArrayList<>();
    protected transient Tag tag = new Tag();
    protected transient BlockVector blockVector;

    public static final class Tag {
        Map<UUID, Long> visited = new HashMap<>(); // User=>Epoch
        long lastUpdated = 0L;
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
        this.blockVector = null;
    }

    public Location createLocation() {
        World bw = Bukkit.getServer().getWorld(world);
        if (bw == null) return null;
        return new Location(bw, x, y, z, (float) yaw, (float) pitch);
    }

    public BlockVector createBlockVector() {
        if (blockVector == null) {
            blockVector = BlockVector.of(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
        return blockVector;
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

    public String getNameNotNull() {
        return name != null ? name : "";
    }

    public String getOwnerName() {
        if (owner == null) return "N/A";
        String result = PlayerCache.nameForUuid(owner);
        if (result != null) return result;
        return "N/A";
    }

    public void unpack() {
        tag = Json.deserialize(json, Tag.class, Tag::new);
    }

    public void pack() {
        json = Json.serialize(tag);
    }

    public void onVisit(UUID visitor) {
        tag.visited.put(visitor, Instant.now().getEpochSecond());
        score = tag.visited.size();
        pack();
        HomePlugin.getInstance().getDb().updateAsync(this, null, "json", "score");
    }

    public void updateScore() {
        if (tag == null) return;
        long now = Instant.now().getEpochSecond();
        long cutoff = now - (60L * 60L * 24L);
        if (tag.lastUpdated >= cutoff) return;
        long cutoff2 = now - (60L * 60L * 24L * 30L);
        tag.visited.values().removeIf(d -> d < cutoff2);
        score = tag.visited.size();
        tag.lastUpdated = now;
        pack();
        HomePlugin.getInstance().getDb().updateAsync(this, null, "json", "score");
    }

    public static int rank(Home a, Home b) {
        // Highest score
        int v = Integer.compare(b.score, a.score);
        if (v != 0) return v;
        // Earliest
        return Long.compare(a.created.getTime(), b.created.getTime());
    }
}
