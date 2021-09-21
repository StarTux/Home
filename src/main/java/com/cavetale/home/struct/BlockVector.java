package com.cavetale.home.struct;

import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class BlockVector {
    public final String world;
    public final int x;
    public final int y;
    public final int z;

    public static BlockVector of(final String world, final int x, final int y, final int z) {
        return new BlockVector(world, x, y, z);
    }

    public static BlockVector of(Block block) {
        return new BlockVector(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockVector of(Location location) {
        return new BlockVector(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Block toBlock() {
        World w = Bukkit.getWorld(world);
        return w.getBlockAt(x, y, z);
    }
}
