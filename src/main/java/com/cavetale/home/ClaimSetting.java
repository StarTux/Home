package com.cavetale.home;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ClaimSetting {
    PVP("PvP Combat", false),
    EXPLOSIONS("Explosion damage", false),
    FIRE("Fire burns blocks", false),
    AUTOGROW("Claim grows automatically", true),
    PUBLIC("Anyone can build", false),
    PUBLIC_CONTAINER("Anyone can open containers", false),
    PUBLIC_INVITE("Anyone can interact", false),
    SHOW_BORDERS("Show claim borders", true),
    ELYTRA("Allow flight", true),
    ENDER_PEARL("Allow ender teleport", true),
    INHERITANCE("Subclaims inherit claim trust", true),
    // Admin only
    HIDDEN("Hide this claim", false),
    MOB_SPAWNING("Allow mob spawning", true);

    public final String key = name().toLowerCase();
    public final String displayName;
    public final boolean defaultValue;

    public boolean isAdminOnly() {
        switch (this) {
        case HIDDEN:
        case MOB_SPAWNING:
            return true;
        default:
            return false;
        }
    }
}
