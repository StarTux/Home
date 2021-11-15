package com.cavetale.home.claimcache;

import com.cavetale.home.Area;
import com.cavetale.home.Claim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Store all claims in one world for fast spatial lookup.  This
 * contaier is NOT aware of mirror worlds!
 */
public final class ClaimCache {
    @Getter protected final List<Claim> allClaims = new ArrayList<>();
    protected final Map<String, SpatialClaimCache> worlds = new HashMap<>();

    public void add(Claim claim) {
        allClaims.add(claim);
        worlds.computeIfAbsent(claim.getWorld(), w -> new SpatialClaimCache())
            .insert(claim);
    }

    public void remove(Claim claim) {
        allClaims.remove(claim);
        worlds.computeIfAbsent(claim.getWorld(), w -> new SpatialClaimCache())
            .remove(claim);
    }

    public void resize(Claim claim, Area oldArea, Area newArea) {
        worlds.computeIfAbsent(claim.getWorld(), w -> new SpatialClaimCache())
            .update(claim, oldArea, newArea);
    }

    public void clear() {
        allClaims.clear();
        worlds.clear();
    }

    public Claim at(final String world, int x, int z) {
        SpatialClaimCache spatial = worlds.get(world);
        if (spatial == null) return null;
        return spatial.findClaimAt(x, z);
    }

    public List<Claim> within(final String world, Area area) {
        SpatialClaimCache spatial = worlds.get(world);
        if (spatial == null) return List.of();
        return spatial.findClaimsWithin(area);
    }

    public List<Claim> inWorld(final String world) {
        SpatialClaimCache spatial = worlds.get(world);
        if (spatial == null) return List.of();
        return spatial.allClaims;
    }
}
