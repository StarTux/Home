package com.cavetale.home;

import lombok.Data;
import org.bukkit.block.BlockFace;

@Data
public final class ClaimToolSession {
    private final int claimId;
    private final BlockFace clickedCorner;

    public Claim getClaim() {
        return HomePlugin.getInstance().getClaimById(claimId);
    }
}
