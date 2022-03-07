package com.cavetale.home;

import lombok.Data;
import org.bukkit.configuration.ConfigurationSection;

@Data
final class WorldSettings {
    protected int claimMargin = 1024;
    protected int homeMargin = 64;
    protected int wildCooldown = 10;
    protected boolean manageGameMode = true;
    protected int initialClaimSize = 128;
    protected int secondaryClaimSize = 32;
    protected double initialClaimCost = 0.0;
    protected double secondaryClaimCost = 0.0;
    protected double claimBlockCost = 0.1;
    protected long claimAbandonCooldown = 0;

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
