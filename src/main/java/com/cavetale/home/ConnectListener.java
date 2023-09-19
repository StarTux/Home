package com.cavetale.home;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.core.util.Json;
import com.cavetale.home.sql.SQLHome;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class ConnectListener implements Listener {
    public static final String CHANNEL_CLAIM_UPDATE = "home:claim_update";
    public static final String CHANNEL_HOME_UPDATE = "home:home_update";
    public static final String CHANNEL_HOME_TELEPORT = "home:teleport";
    private final HomePlugin plugin;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Value
    private static class PlayerHomePacket {
        private UUID player;
        private int homeId;
    }

    public static void broadcastClaimUpdate(Claim claim) {
        Connect.get().broadcastMessage(ServerGroup.current(), CHANNEL_CLAIM_UPDATE, "" + claim.getId());
    }

    public static void broadcastHomeUpdate(SQLHome home) {
        Connect.get().broadcastMessage(ServerGroup.current(), CHANNEL_HOME_UPDATE, "" + home.getId());
    }

    public static void teleport(UUID uuid, SQLHome home) {
        Connect.get().broadcastMessage(ServerGroup.current(), CHANNEL_HOME_TELEPORT, Json.serialize(new PlayerHomePacket(uuid, home.getId())));
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
        case CHANNEL_HOME_TELEPORT: {
            PlayerHomePacket packet = event.getPayload(PlayerHomePacket.class);
            if (packet == null) return;
            RemotePlayer player = Connect.get().getRemotePlayer(packet.player);
            if (player == null) return;
            SQLHome home = plugin.getHomes().findById(packet.homeId);
            Location location = home.createLocation();
            if (location == null) return;
            player.bring(plugin, location, player2 -> { });
            break;
        }
        default: break;
        }
    }
}
