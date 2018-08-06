package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    @Column(nullable = false) private Date created;
    @Column(nullable = false, length = 32) private String world;
    @Column(nullable = false) private double x, y, z, pitch, yaw;
    @Column(nullable = true, length = 32) private String publicName;
    final transient List<UUID> invites = new ArrayList<>();

    Home() { }

    Home(UUID owner, Location location, String name) {
        this.owner = owner;
        this.name = name;
        this.created = new Date();
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = (double)location.getPitch();
        this.yaw = (double)location.getYaw();
    }

    // Bukkit

    Location createLocation() {
        World bw = Bukkit.getServer().getWorld(world);
        if (bw == null) return null;
        return new Location(bw, x, y, z, (float)yaw, (float)pitch);
    }

    boolean isOwner(UUID playerId) {
        return this.owner.equals(playerId);
    }

    boolean isInWorld(String worldName) {
        return this.world.equals(worldName);
    }

    // Supports null check for primary homes
    boolean isNamed(String homeName) {
        if ((name == null) != (homeName == null)) return false;
        if (name == null) return true;
        return name.equals(homeName);
    }

    String getOwnerName() {
        if (owner == null) return "N/A";
        String result = GenericEvents.cachedPlayerName(owner);
        if (result != null) return result;
        return "N/A";
    }
}
