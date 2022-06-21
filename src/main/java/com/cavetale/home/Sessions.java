package com.cavetale.home;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class Sessions {
    private final HomePlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    protected void enable() {
        if (!plugin.localHomeWorlds.isEmpty()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }
    }

    public Session of(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), u -> new Session(plugin, player));
    }

    public Session enter(Player player) {
        return of(player);
    }

    public Session exit(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) session.disable();
        return session;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            of(player).tick(player);
        }
    }
}
