package com.cavetale.home;

import com.cavetale.core.event.player.PluginPlayerQuery;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final HomePlugin plugin;

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.sessions.exit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        plugin.sessions.exit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuery(PluginPlayerQuery query) {
        Player player = query.getPlayer();
        PluginPlayerQuery.Name name = query.getName();
        if (name == PluginPlayerQuery.Name.CLAIM_COUNT) {
            int claimCount = plugin.findClaims(player).size();
            PluginPlayerQuery.Name.CLAIM_COUNT.respond(query, plugin, claimCount);
        } else if (name == PluginPlayerQuery.Name.HOME_COUNT) {
            int homeCount = plugin.findHomes(player.getUniqueId()).size();
            PluginPlayerQuery.Name.HOME_COUNT.respond(query, plugin, homeCount);
        } else if (name == PluginPlayerQuery.Name.PRIMARY_HOME_IS_SET) {
            boolean primaryHomeIsSet = plugin.findHome(player.getUniqueId(), null) != null;
            PluginPlayerQuery.Name.PRIMARY_HOME_IS_SET.respond(query, plugin, primaryHomeIsSet);
        } else if (name == PluginPlayerQuery.Name.INSIDE_OWNED_CLAIM) {
            Claim claim = plugin.getClaimAt(player.getLocation());
            boolean insideTrustedClaim = claim != null ? claim.isOwner(player) : false;
            PluginPlayerQuery.Name.INSIDE_TRUSTED_CLAIM.respond(query, plugin, insideTrustedClaim);
        } else if (name == PluginPlayerQuery.Name.INSIDE_TRUSTED_CLAIM) {
            Claim claim = plugin.getClaimAt(player.getLocation());
            boolean insideTrustedClaim = claim != null ? (!claim.isOwner(player) && claim.getTrustType(player).canBuild()) : false;
            PluginPlayerQuery.Name.INSIDE_TRUSTED_CLAIM.respond(query, plugin, insideTrustedClaim);
        }
    }
}
