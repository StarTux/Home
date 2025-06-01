package com.cavetale.home;

import com.cavetale.core.font.VanillaItems;
import com.cavetale.mytems.Mytems;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.ComponentLike;

@Getter
@RequiredArgsConstructor
public enum TrustType {
    BAN("Banned", Mytems.STEEL_HAMMER, "ban"),
    NONE("N/A", VanillaItems.BARRIER, ""),
    INTERACT("Interact", Mytems.MOUSE_CURSOR, "interact-trust"),
    CONTAINER("Container", VanillaItems.CHEST_MINECART, "container-trust"),
    BUILD("Build", VanillaItems.IRON_PICKAXE, "trust"),
    CO_OWNER("Co-Owner", Mytems.SILVER_VOTE_TROPHY, "co-owner-trust"),
    OWNER("Owner", Mytems.GOLD_VOTE_TROPHY, "owner-trust");

    private static final Map<String, TrustType> KEY_MAP = new HashMap<>();
    public final String key = name().toLowerCase();
    public final String displayName;
    private final ComponentLike icon;
    private final String commandName;

    static {
        for (TrustType it : TrustType.values()) {
            KEY_MAP.put(it.key, it);
        }
    }

    public static TrustType of(String key) {
        TrustType result = KEY_MAP.get(key);
        return result != null ? result : TrustType.NONE;
    }

    public boolean entails(TrustType other) {
        return gte(other);
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
