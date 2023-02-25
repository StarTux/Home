package com.cavetale.home.sql;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.util.Json;
import com.cavetale.home.HomePlugin;
import com.cavetale.home.struct.BlockVector;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow.UniqueKey;
import com.winthier.sql.SQLRow;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Data
@NotNull
@Name("homes")
@UniqueKey({"owner", "name"})
public final class SQLHome implements SQLRow {
    @Id private Integer id;

    @Keyed private UUID owner;

    @Keyed @Nullable @VarChar(32) private String name;

    private Date created;

    @VarChar(32) private String world;

    private double x;

    private double y;

    private double z;

    private double pitch;

    private double yaw;

    @Unique @Nullable @VarChar(32) private String publicName;

    private int score;

    @Nullable @Text private String json;

    private final transient Set<UUID> invites = new HashSet<>();
    private transient Tag tag = new Tag();
    private transient BlockVector blockVector;

    public static final Comparator<SQLHome> NAME_COMPARATOR = (a, b) -> {
        if (a.name == null) return -1;
        if (b.name == null) return 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
    };

    public static final Comparator<SQLHome> RANK_COMPARATOR = (SQLHome a, SQLHome b) -> {
        int v = Integer.compare(b.score, a.score);
        return v != 0
        ? v
        : Long.compare(a.created.getTime(), b.created.getTime());
    };

    public static final class Tag {
        Map<UUID, Long> visited = new HashMap<>(); // User=>Epoch
    }

    /**
     * SQL constructor.
     */
    public SQLHome() { }

    /**
     * Constructor for new homes.
     */
    public SQLHome(final UUID owner, final Location location, final String name) {
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

    public void setLocation(Location location) {
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

    public boolean isOwner(UUID playerId) {
        return this.owner.equals(playerId);
    }

    public boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
    }

    public boolean isInvited(UUID playerId) {
        return invites.contains(playerId);
    }

    // Supports null check for primary homes
    public boolean isNamed(String homeName) {
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

    private boolean updateScoreHelper() {
        if (tag.visited.isEmpty()) return false;
        final long cutoff = Instant.now().getEpochSecond() - Duration.ofDays(30).toSeconds();
        if (!tag.visited.values().removeIf(d -> d < cutoff)) return false;
        score = tag.visited.size();
        return true;
    }

    public void onVisit(UUID visitor) {
        updateScoreHelper();
        tag.visited.put(visitor, Instant.now().getEpochSecond());
        score = tag.visited.size();
        pack();
        HomePlugin.getInstance().getDb().updateAsync(this, Set.of("json", "score"), res -> {
                HomePlugin.getInstance().getConnectListener().broadcastHomeUpdate(this);
            });
    }

    public void updateScore() {
        if (!updateScoreHelper()) return;
        pack();
        HomePlugin.getInstance().getDb().updateAsync(this, Set.of("json", "score"), res -> {
                HomePlugin.getInstance().getConnectListener().broadcastHomeUpdate(this);
            });
    }

    public boolean isPrimary() {
        return name == null;
    }

    public boolean isPublic() {
        return publicName != null;
    }

    public void saveToDatabase() {
        pack();
        if (id == null) {
            HomePlugin.getInstance().getDb().insertAsync(this, res -> {
                    HomePlugin.getInstance().getConnectListener().broadcastHomeUpdate(this);
                    HomePlugin.getInstance().getHomes().add(this);
                });
        } else {
            HomePlugin.getInstance().getDb().updateAsync(this, res -> {
                    HomePlugin.getInstance().getConnectListener().broadcastHomeUpdate(this);
                });
        }
    }
}
