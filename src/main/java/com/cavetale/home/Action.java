package com.cavetale.home;

enum Action {
    // Build group, more or less
    BUILD,
    BUCKET,
    VEHICLE,
    // Inventory modification
    CONTAINER,
    // Use doors and buttons and such
    INTERACT,
    // Combat; claims allow it if pvp is on, subclaims require ACCESS
    // trust.
    // TODO: Remove
    @Deprecated
    PVP;
}
