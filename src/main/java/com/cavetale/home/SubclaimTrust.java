package com.cavetale.home;

public enum SubclaimTrust {
    NONE,
    ACCESS,
    CONTAINER,
    BUILD,
    CO_OWNER("Co-owner"),
    OWNER;

    public final String key;
    public final String displayName;

    SubclaimTrust(final String displayName) {
        this.key = name().toLowerCase();
        this.displayName = displayName;
    }

    SubclaimTrust() {
        this.key = name().toLowerCase();
        this.displayName = name().substring(0, 1) + name().substring(1).toLowerCase();
    }

    public boolean entails(SubclaimTrust other) {
        return ordinal() >= other.ordinal();
    }

    public boolean exceeds(SubclaimTrust other) {
        return ordinal() > other.ordinal();
    }
}
