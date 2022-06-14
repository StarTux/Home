package com.cavetale.home;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.home.sql.SQLHome;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class ConnectListener implements Listener {
    public static final String CHANNEL_CLAIM_UPDATE = "home:claim_update";
    public static final String CHANNEL_HOME_UPDATE = "home:home_update";
    private final HomePlugin plugin;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void broadcastClaimUpdate(Claim claim) {
        Connect.get().broadcastMessage(ServerGroup.current(), CHANNEL_CLAIM_UPDATE, "" + claim.getId());
    }

    public static void broadcastHomeUpdate(SQLHome home) {
        Connect.get().broadcastMessage(ServerGroup.current(), CHANNEL_HOME_UPDATE, "" + home.getId());
    }

    @EventHandler
    private void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getChannel()) {
        case CHANNEL_CLAIM_UPDATE: {
            int claimId = Integer.parseInt(event.getPayload());
            plugin.reloadClaim(claimId);
            plugin.getLogger().info("Received claim update: " + claimId); // debug
            break;
        }
        case CHANNEL_HOME_UPDATE: {
            int homeId = Integer.parseInt(event.getPayload());
            plugin.reloadHome(homeId);
            plugin.getLogger().info("Received home update: " + homeId); // debug
            break;
        }
        default: break;
        }
    }
}
