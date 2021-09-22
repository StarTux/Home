package com.cavetale.home;

import com.cavetale.core.command.CommandWarn;
import com.cavetale.home.util.Colors;
import com.cavetale.home.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

public final class Session {
    private final HomePlugin plugin;
    private final UUID uuid;
    @Setter private Function<PlayerInteractEvent, Boolean> playerInteractCallback = null;
    private Runnable confirmCallback = null;
    private String confirmMessage;
    private List<Component> storedPages = new ArrayList<>();
    private long notifyCooldown = 0L;
    @Getter @Setter private boolean ignoreClaims = false;
    @Getter @Setter private ClaimGrowSnippet claimGrowSnippet;

    public Session(final HomePlugin plugin, final Player player) {
        this.plugin = plugin;
        this.uuid = player.getUniqueId();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    protected void disable() {
        playerInteractCallback = null;
        confirmCallback = null;
        confirmMessage = null;
    }

    /**
     * @return true if the event was handled by this session in some
     * way, false otherwise. It is up to the callback to cancel the
     * event if required.
     */
    public boolean onPlayerInteract(PlayerInteractEvent event) {
        if (playerInteractCallback == null) return false;
        // callbacks may reset themselves
        try {
            return playerInteractCallback.apply(event);
        } catch (Exception e) {
            e.printStackTrace();
            playerInteractCallback = null;
            return false;
        }
    }

    /**
     * Store a runnable awaiting the confirm or cancel command.
     * @bug only works with the subclaim command
     */
    public void requireConfirmation(String message, Runnable callback) {
        confirmCallback = callback;
        confirmMessage = message;
        Component confirmTooltip = Component.join(Component.newline(),
                                                  Component.text("Confirm", NamedTextColor.GREEN),
                                                  Component.text(message, NamedTextColor.GRAY));
        Component cancelTooltip = Component.join(Component.newline(),
                                                 Component.text("Cancel", NamedTextColor.GREEN),
                                                 Component.text(message, NamedTextColor.GRAY));
        getPlayer().sendMessage(Component.text().color(NamedTextColor.WHITE)
                                .content(message)
                                .append(Component.space())
                                .append(Component.text().content("[Confirm]").color(NamedTextColor.GREEN)
                                        .clickEvent(ClickEvent.runCommand("/subclaim confirm"))
                                        .hoverEvent(HoverEvent.showText(confirmTooltip)))
                                .append(Component.space())
                                .append(Component.text().content("[Cancel]").color(NamedTextColor.RED)
                                        .clickEvent(ClickEvent.runCommand("/subclaim cancel"))
                                        .hoverEvent(HoverEvent.showText(cancelTooltip))));
    }

    /**
     * When a prior confirmation request is cancelled by the player.
     */
    public void cancelCommand() {
        if (confirmCallback == null) return;
        String message = confirmMessage;
        confirmCallback = null;
        confirmMessage = null;
        getPlayer().sendMessage(ChatColor.RED + "Cancelled: " + message);
    }

    /**
     * When a prior confirmation request is confirmed by the player.
     */
    public void confirmCommand() {
        if (confirmCallback == null) return;
        Runnable callback = confirmCallback;
        confirmCallback = null;
        confirmMessage = null;
        try {
            callback.run();
        } catch (CommandWarn warn) {
            getPlayer().sendMessage(ChatColor.RED + warn.getMessage());
        }
    }

    public void setPages(List<Component> pages) {
        storedPages = new ArrayList<>(pages);
    }

    public boolean showStoredPage(Player player, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= storedPages.size()) return false;
        Component page = storedPages.get(pageIndex);
        player.sendMessage(page);
        if (pageIndex == storedPages.size() - 1) return true;
        int remainingPages = storedPages.size() - pageIndex - 1;
        player.sendMessage(Component.text().color(NamedTextColor.GRAY)
                           .content("Showing page " + (pageIndex + 1) + "/" + storedPages.size())
                           .append(Component.space())
                           .append(Component.text("[View Next]", NamedTextColor.GREEN))
                           .clickEvent(ClickEvent.runCommand("/homes page " + (pageIndex + 2)))
                           .hoverEvent(HoverEvent.showText(Component.text("View Next Page", NamedTextColor.GREEN))));
        return true;
    }

    public void notify(Player player, Claim claim) {
        long now = System.currentTimeMillis();
        if (notifyCooldown > now) return;
        notifyCooldown = now + 1000L;
        player.sendActionBar(Msg.builder("This claim belongs to " + claim.getOwnerName()).color(Colors.RED).create());
    }

    public boolean notify(Player player, Component component) {
        long now = System.currentTimeMillis();
        if (notifyCooldown > now) return false;
        notifyCooldown = now + 1000L;
        player.sendActionBar(component);
        return true;
    }

    /**
     * Created when a player enters "/claim grow" but has claim blocks
     * missing.
     * Used when the same player confirms "/claim buy" and is still
     * nearby.
     */
    @Value
    public static final class ClaimGrowSnippet {
        public final String world;
        public final int x;
        public final int z;
        public final int claimId;

        public boolean isNear(Location location) {
            return location.getWorld().getName().equals(world)
                && location.getBlockX() == x
                && location.getBlockZ() == z;
        }
    }
}
