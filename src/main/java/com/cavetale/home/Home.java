package com.cavetale.home;

import java.util.ArrayList;
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
       uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "name"}))
public final class Home {
    @Id Integer id;
    @Column(nullable = false) UUID owner;
    @Column(nullable = true, length = 32) String name;
    @Column(nullable = false) private String world;
    @Column(nullable = false) private double x, y, z, pitch, yaw;
    final transient List<HomeInvite> invites = new ArrayList<>();

    Home() { }

    Home(UUID owner, Location location, String name) {
        this.owner = owner;
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
}
