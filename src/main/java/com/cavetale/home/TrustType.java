package com.cavetale.home;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TrustType {
    BAN("Banned"),
    NONE("N/A"),
    INTERACT("Interact"),
    CONTAINER("Container"),
    BUILD("Build"),
    CO_OWNER("Co-Owner"),
    OWNER("Owner");

    private static final Map<String, TrustType> KEY_MAP = new HashMap<>();
    public final String key = name().toLowerCase();
    public final String displayName;

    static {
        for (TrustType it : TrustType.values()) {
            KEY_MAP.put(it.key, it);
        }
    }

    public static TrustType of(String key) {
        TrustType result = KEY_MAP.get(key);
        return result != null ? result : TrustType.NONE;
    }

    public boolean gte(TrustType other) {
        return ordinal() >= other.ordinal();
    }

    public boolean gt(TrustType other) {
        return ordinal() > other.ordinal();
    }

    public boolean lte(TrustType other) {
        return ordinal() <= other.ordinal();
    }

    public boolean lt(TrustType other) {
        return ordinal() < other.ordinal();
    }

    public boolean isOwner() {
        return gte(OWNER);
    }

    public boolean isCoOwner() {
        return gte(CO_OWNER);
    }

    public boolean canBuild() {
        return gte(BUILD);
    }

    public boolean canInteract() {
        return gte(INTERACT);
    }

    public boolean canOpenContainers() {
        return gte(CONTAINER);
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isBan() {
        return lte(BAN);
    }

    public boolean isTrust() {
        return gt(NONE);
    }

    public boolean is(TrustType other) {
        return this == other;
    }

    public TrustType max(TrustType other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
