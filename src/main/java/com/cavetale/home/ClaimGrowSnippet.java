package com.cavetale.home;

import lombok.Value;
import org.bukkit.Location;

/**
 * Created when a player enters "/claim grow" but has claim blocks
 * missing.
 *
 * Used when the same player confirms "/claim buy" and is still at the
 * same spot.  Since the player location is memorized, this is safe to
 * keep around.
 */
@Value
public final class ClaimGrowSnippet {
    private static final int TOLERANCE = 8;
    private final String world;
    private final int x;
    private final int z;
    private final Area newArea;
    private final int claimId;

    public ClaimGrowSnippet(final Location location, final Claim claim, final Area newArea) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.z = location.getBlockZ();
        this.newArea = newArea;
        this.claimId = claim.getId();
    }

    public boolean isNear(Location location) {
        return location.getWorld().getName().equals(world)
            && Math.abs(location.getBlockX() - x) <= TOLERANCE
            && Math.abs(location.getBlockZ() - z) <= TOLERANCE;
    }
}
