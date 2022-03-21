package com.cavetale.home.claimcache;

import com.cavetale.home.Area;
import com.cavetale.home.Claim;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bukkit.command.CommandSender;

/**
 * Store all claims in one world for fast spatial lookup.  This
 * contaier is NOT aware of mirror worlds!
 */
public final class ClaimCache {
    @Getter protected final List<Claim> allClaims = new ArrayList<>();
    protected final Map<String, SpatialClaimCache> worlds = new HashMap<>();

    public void initialize(Iterable<String> localWorlds) {
        for (String world : localWorlds) {
            worlds.put(world, new SpatialClaimCache());
        }
    }

    public List<Claim> getAllLocalClaims() {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : allClaims) {
            if (worlds.containsKey(claim.getWorld())) result.add(claim);
        }
        return result;
    }

    public void add(Claim claim) {
        allClaims.add(claim);
        SpatialClaimCache cache = worlds.get(claim.getWorld());
        if (cache != null) cache.insert(claim);
    }

    public void remove(Claim claim) {
        allClaims.remove(claim);
        SpatialClaimCache cache = worlds.get(claim.getWorld());
        if (cache != null) cache.remove(claim);
    }

    public void resize(Claim claim, Area oldArea, Area newArea) {
        SpatialClaimCache cache = worlds.get(claim.getWorld());
        if (cache == null) return;
        cache.update(claim, oldArea, newArea);
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

    public void debug(CommandSender sender, String worldName) {
        SpatialClaimCache spatial = worlds.get(worldName);
        if (spatial == null) {
            sender.sendMessage("No cache: " + worldName);
            return;
        }
        List<SpatialClaimCache.XYSlot> slots = spatial.getAllSlots();
        Collections.sort(slots, (a, b) -> Integer.compare(a.claims.size(), b.claims.size()));
        int len = SpatialClaimCache.CHUNK_SIZE;
        int claimCount = 0;
        for (SpatialClaimCache.XYSlot slot : slots) {
            claimCount += slot.claims.size();
            sender.sendMessage("Slot " + slot.x + "," + slot.y
                               + " (" + (slot.x * len) + "," + (slot.y * len) + ")"
                               + ": " + slot.claims.size() + " claims");
        }
        sender.sendMessage("Total " + slots.size() + " slots, " + claimCount + " claims");
    }
}
