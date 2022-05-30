package com.cavetale.home;

import com.cavetale.core.util.Json;
import com.cavetale.home.sql.SQLSubclaim;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Subclaim {
    public static final UUID PUBLIC_UUID = new UUID(0, 0);

    private final HomePlugin plugin;
    @Getter private final Claim parent;
    private final SQLSubclaim row;

    @Getter private Area area;
    private Tag tag;

    /**
     * Constructor to load from database.
     */
    public Subclaim(final HomePlugin plugin, final Claim parent, final SQLSubclaim row) {
        this.plugin = plugin;
        this.parent = parent;
        this.row = row;
        this.area = row.getArea();
        this.tag = Json.deserialize(row.getTag(), Tag.class, Tag::new);
    }

    /**
     * Constructor for a new subclaim.
     */
    public Subclaim(final HomePlugin plugin, final Claim parent, final World world, final Area area) {
        this.plugin = plugin;
        this.parent = parent;
        this.area = area;
        this.row = new SQLSubclaim(parent.getId(), world.getName(), area);
        this.tag = new Tag();
    }

    @Getter
    public static final class Tag {
        Map<UUID, SubclaimTrust> trusted = new HashMap<>();
    }

    public int getId() {
        return row.getId() != null ? row.getId() : 0;
    }

    public String getListInfo() {
        return plugin.worldDisplayName(getWorld()) + " " + area;
    }

    public String getWorld() {
        return row.getWorld();
    }

    public boolean isInWorld(World world) {
        return world.getName().equals(getWorld());
    }

    public List<UUID> getTrustedUuids() {
        return new ArrayList<>(tag.trusted.keySet());
    }

    public Map<SubclaimTrust, Set<UUID>> getTrustedMap() {
        Map<SubclaimTrust, Set<UUID>> map = new EnumMap<>(SubclaimTrust.class);
        for (SubclaimTrust trust : SubclaimTrust.values()) map.put(trust, new HashSet<>());
        for (Map.Entry<UUID, SubclaimTrust> entry : tag.trusted.entrySet()) {
            UUID uuid = entry.getKey();
            SubclaimTrust trust = entry.getValue();
            Set<UUID> set = map.get(trust);
            set.add(uuid);
        }
        return map;
    }

    public SubclaimTrust getTrust(Player player) {
        if (parent.isOwner(player)) return SubclaimTrust.OWNER;
        return getTrust(player.getUniqueId());
    }

    public SubclaimTrust getTrust(UUID uuid) {
        if (parent.isOwner(uuid)) return SubclaimTrust.OWNER;
        SubclaimTrust trust = tag.trusted.get(uuid);
        if (trust != null) return trust;
        trust = tag.trusted.get(PUBLIC_UUID);
        if (trust != null) return trust;
        return SubclaimTrust.NONE;
    }

    private void saveTag() {
        row.setTag(Json.serialize(tag));
        plugin.getDb().updateAsync(row, Set.of("tag"), res -> {
                plugin.getConnectListener().broadcastClaimUpdate(parent);
            });
    }

    public void setTrust(UUID uuid, SubclaimTrust trust) {
        tag.trusted.put(uuid, trust);
        saveTag();
    }

    public SubclaimTrust removeTrust(UUID uuid) {
        SubclaimTrust result = tag.trusted.remove(uuid);
        if (result != null) saveTag();
        return result;
    }

    public static UUID cachedPlayerUuid(String name) {
        if (name.startsWith("*")) return PUBLIC_UUID;
        return PlayerCache.uuidForName(name);
    }

    public static String cachedPlayerName(UUID uuid) {
        if (uuid.equals(PUBLIC_UUID)) return "*Everybody*";
        return PlayerCache.nameForUuid(uuid);
    }

    public TrustType getTrustType(UUID uuid) {
        SubclaimTrust trust = getTrust(uuid);
        switch (trust) {
        case NONE: return TrustType.NONE;
        case ACCESS: return TrustType.INTERACT;
        case CONTAINER: return TrustType.CONTAINER;
        case BUILD: return TrustType.BUILD;
        case CO_OWNER: return TrustType.CO_OWNER;
        case OWNER: return TrustType.OWNER;
        default: throw new IllegalStateException("trust=" + trust);
        }
    }

    public void insertIntoDatabase() {
        plugin.getDb().insertAsync(row, res -> {
                plugin.getConnectListener().broadcastClaimUpdate(parent);
            });
    }

    public void deleteFromDatabase() {
        plugin.getDb().deleteAsync(row, res -> {
                plugin.getConnectListener().broadcastClaimUpdate(parent);
            });
    }
}
