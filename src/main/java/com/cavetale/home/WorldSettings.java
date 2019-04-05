package com.cavetale.home;

import lombok.Data;
import org.bukkit.configuration.ConfigurationSection;

@Data
final class WorldSettings {
    int claimMargin = 1024;
    int homeMargin = 64;
    int wildCooldown = 10;
    boolean manageGameMode = true;
    int initialClaimSize = 128;
    int secondaryClaimSize = 32;
    double initialClaimCost = 0.0;
    double secondaryClaimCost = 0.0;
    double claimBlockCost = 0.1;
    long claimAbandonCooldown = 0;

    void load(ConfigurationSection config) {
        claimMargin = config.getInt("ClaimMargin", claimMargin);
        homeMargin = config.getInt("HomeMargin", homeMargin);
        wildCooldown = config.getInt("WildCooldown", wildCooldown);
        claimBlockCost = config.getDouble("ClaimBlockCost", claimBlockCost);
        manageGameMode = config.getBoolean("ManageGameMode", manageGameMode);
        initialClaimSize = config.getInt("InitialClaimSize", initialClaimSize);
        secondaryClaimSize = config.getInt("SecondaryClaimSize", secondaryClaimSize);
        initialClaimCost = config.getDouble("InitialClaimCost", initialClaimCost);
        secondaryClaimCost = config.getDouble("SecondaryClaimCost", secondaryClaimCost);
        claimAbandonCooldown = config.getLong("ClaimAbandonCooldown", claimAbandonCooldown);
    }
}
