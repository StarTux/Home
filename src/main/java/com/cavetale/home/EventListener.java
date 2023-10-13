package com.cavetale.home;

import com.cavetale.core.back.Back;
import com.cavetale.core.event.player.PluginPlayerQuery;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

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
    protected void onPlayerQuery(PluginPlayerQuery query) {
        Player player = query.getPlayer();
        PluginPlayerQuery.Name name = query.getName();
        if (name == PluginPlayerQuery.Name.CLAIM_COUNT) {
            int claimCount = plugin.findClaims(player).size();
            PluginPlayerQuery.Name.CLAIM_COUNT.respond(query, plugin, claimCount);
        } else if (name == PluginPlayerQuery.Name.HOME_COUNT) {
            int homeCount = plugin.getHomes().findOwnedHomes(player.getUniqueId()).size();
            PluginPlayerQuery.Name.HOME_COUNT.respond(query, plugin, homeCount);
        } else if (name == PluginPlayerQuery.Name.PRIMARY_HOME_IS_SET) {
            boolean primaryHomeIsSet = plugin.getHomes().findPrimaryHome(player.getUniqueId()) != null;
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

    @EventHandler
    private void onPlayerQuitBack(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.isDead()) return;
        if (!player.hasPermission("home.back")) return;
        if (!plugin.isLocalHomeWorld(player.getWorld())) return;
        Back.setBackLocation(player, plugin, player.getLocation(), "Home world logout");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerTeleportBack(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.isDead()) return;
        if (!player.hasPermission("home.back")) return;
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())) return;
        boolean from = plugin.isLocalHomeWorld(event.getFrom().getWorld());
        boolean to = plugin.isLocalHomeWorld(event.getTo().getWorld());
        if (from && !to) {
            // Warp out of the home worlds
            Back.setBackLocation(player, plugin, player.getLocation(), "Home world teleport");
        }
    }

    @EventHandler
    private void onPlayerDeathBack(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.isDead()) return;
        if (!player.hasPermission("home.back")) return;
        if (!plugin.isLocalHomeWorld(player.getWorld())) return;
        Back.resetBackLocation(player);
    }
}
