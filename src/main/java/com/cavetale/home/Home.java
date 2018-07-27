package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.World;

@Data
final class Home {
    private final HomePlugin plugin;
    private Integer id;
    private UUID owner;
    private String name;
    private String world;
    private double x, y, z, pitch, yaw;

    Home(HomePlugin plugin) {
        this.plugin = plugin;
    }

    Home(HomePlugin plugin, UUID owner, Location location, String name) {
        this.plugin = plugin;
        this.owner = owner;
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.pitch = (double)location.getPitch();
        this.yaw = (double)location.getYaw();
    }

    // SQL Interface

    @Data @Table(name = "homes")
    static final class SQLRow {
        @Id Integer id;
        @Column(nullable = false) UUID owner;
        @Column(nullable = true) String name;
        @Column(nullable = false) String world;
        @Column(nullable = false) Double x, y, z, pitch, yaw;
    }

    SQLRow toSQLRow() {
        SQLRow row = new SQLRow();
        row.id = this.id;
        row.world = this.world;
        row.x = this.x;
        row.y = this.y;
        row.z = this.z;
        row.pitch = this.pitch;
        row.yaw = this.yaw;
        return row;
    }

    void loadSQLRow(SQLRow row) {
        this.id = row.id;
        this.world = row.world;
        this.x = row.x;
        this.y = row.y;
        this.z = row.z;
        this.pitch = row.pitch;
        this.yaw = row.yaw;
    }

    void saveToDatabase() {
        SQLRow row = toSQLRow();
        plugin.getDb().save(row);
        this.id = row.id;
    }

    // Bukkit

    Location createLocation() {
        World bw = plugin.getServer().getWorld(world);
        if (bw == null) return null;
        return new Location(bw, x, y, z, (float)yaw, (float)pitch);
    }
}
