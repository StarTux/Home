package com.cavetale.home;

import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerQuery;
import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
    @Getter @Setter private ClaimGrowSnippet claimGrowSnippet;
    private Claim currentClaim;
    private int ticks;
    @Setter private int sidebarTicks;

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
        Component confirmTooltip = Component.join(JoinConfiguration.separator(Component.newline()),
                                                  Component.text("Confirm", NamedTextColor.GREEN),
                                                  Component.text(message, NamedTextColor.GRAY));
        Component cancelTooltip = Component.join(JoinConfiguration.separator(Component.newline()),
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
        getPlayer().sendMessage(Component.text("Cancelled: " + message, NamedTextColor.RED));
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
            getPlayer().sendMessage(Component.text(warn.getMessage(), NamedTextColor.RED));
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
        player.sendActionBar(Component.text("This claim belongs to " + claim.getOwnerName(),
                                            TextColor.color(0xFF0000)));
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

    /**
     * Send no fly zone warnings if necessary.
     * Player must be in home world!
     * Ticks must be checked beforehand!
     */
    private void tickNoFlyZone(Player player) {
        boolean flying = player.isGliding() || PluginPlayerQuery.Name.IS_FLYING.call(plugin, player, false);
        if (!flying) return;
        Location location = player.getLocation();
        String worldName = location.getWorld().getName();
        final String w = plugin.mirrorWorlds.getOrDefault(worldName, worldName);
        final int x = location.getBlockX();
        final int z = location.getBlockZ();
        final int range = 64;
        Area area = new Area(x - range, z - range, x + range, z + range);
        for (Claim claim : plugin.getClaimCache().within(w, area)) {
            if (claim.getSetting(ClaimSetting.ELYTRA)) continue;
            Title title = Title.title(Component.text("WARNING", NamedTextColor.RED, TextDecoration.BOLD),
                                      Component.text("Approaching No-Fly Zone!", NamedTextColor.RED, TextDecoration.BOLD),
                                      Title.Times.times(Duration.ZERO, Duration.ofMillis(550), Duration.ZERO));
            player.showTitle(title);
            player.playSound(player.getEyeLocation(),
                             Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER,
                             1.0f, 2.0f);
            break;
        }
    }

    protected void tick(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        final World world = player.getWorld();
        if (!plugin.isLocalHomeWorld(world)) {
            currentClaim = null;
            ticks = 0;
            sidebarTicks = 0;
            return;
        }
        final Location location = player.getLocation();
        final Claim oldClaim = currentClaim;
        final Claim newClaim;
        if (currentClaim != null && currentClaim.isValid() && currentClaim.contains(location)) {
            newClaim = currentClaim;
        } else {
            newClaim = plugin.getClaimAt(location);
        }
        final boolean kicked;
        if (newClaim != null) {
            if (newClaim.getTrustType(player).isBan()) {
                newClaim.kick(player);
                Component msg = Component.text("You cannot enter this claim!", TextColor.color(0xFF0000));
                player.sendActionBar(msg);
                player.sendMessage(msg);
                plugin.highlightClaim(newClaim, player);
                currentClaim = plugin.getClaimAt(location);
                kicked = true;
            } else {
                currentClaim = newClaim;
                kicked = false;
            }
        } else {
            currentClaim = null;
            kicked = false;
        }
        if (kicked) {
            sidebarTicks = 0;
        }
        if (!kicked && oldClaim != currentClaim) {
            notifyClaimChange(player, oldClaim, currentClaim);
            sidebarTicks = 0;
        }
        if (!kicked && currentClaim != null) {
            triggerClaimActions(player);
        }
        if (!kicked && ticks % 10 == 0) {
            tickNoFlyZone(player);
        }
        ticks += 1;
    }

    private void triggerClaimActions(Player player) {
        if (currentClaim.isOwner(player)) {
            if (currentClaim.getSetting(ClaimSetting.AUTOGROW)
                && currentClaim.getBlocks() > currentClaim.getArea().size()
                && (ticks % 100) == 0
                && plugin.autoGrowClaim(currentClaim).isSuccessful()) {
                plugin.highlightClaim(currentClaim, player);
            }
        }
        if (player.isGliding() && !currentClaim.getSetting(ClaimSetting.ELYTRA)) {
            player.setGliding(false);
            Component msg = Component.text("You cannot fly in this claim!", TextColor.color(0xFF0000));
            if (notify(player, msg)) {
                player.sendMessage(msg);
                plugin.highlightClaim(currentClaim, player);
            }
        }
    }

    private void notifyClaimChange(Player player, Claim oldClaim, Claim newClaim) {
        if (newClaim == null && oldClaim != null) {
            if (oldClaim.getSetting(ClaimSetting.HIDDEN)) return;
            String name = oldClaim.getName();
            String namePart = name != null ? " " + name : "";
            Component message = oldClaim.isOwner(player)
                ? Component.text("Leaving your claim" + namePart, NamedTextColor.GRAY)
                : Component.text("Leaving " + oldClaim.getOwnerGenitive() + " claim" + namePart, NamedTextColor.GRAY);
            player.sendActionBar(message);
            if (oldClaim.getSetting(ClaimSetting.SHOW_BORDERS)) {
                plugin.highlightClaim(oldClaim, player);
            }
            return;
        } else if (newClaim != null) {
            if (newClaim.getSetting(ClaimSetting.HIDDEN)) return;
            String name = newClaim.getName();
            String namePart = name != null ? " " + name : "";
            Component message = newClaim.isOwner(player)
                ? Component.text("Entering your claim" + namePart, NamedTextColor.GRAY)
                : Component.text("Entering " + newClaim.getOwnerGenitive() + " claim" + namePart, NamedTextColor.GRAY);
            player.sendActionBar(message);
            if (newClaim.getSetting(ClaimSetting.SHOW_BORDERS)) {
                plugin.highlightClaim(newClaim, player);
            }
        }
    }

    protected void onPlayerSidebar(Player player, PlayerSidebarEvent event) {
        if (ticks == 0) return;
        if (sidebarTicks > 300) return;
        sidebarTicks += 1;
        List<Component> lines = new ArrayList<>();
        if (currentClaim == null) {
            lines.add(Component.text().color(NamedTextColor.AQUA)
                      .append(Component.text("Area not "))
                      .append(Component.text("/claim", NamedTextColor.YELLOW))
                      .append(Component.text("ed"))
                      .build());
        } else {
            lines.add(Component.text().color(NamedTextColor.AQUA)
                      .append(Component.text("Current "))
                      .append(Component.text("/claim", NamedTextColor.YELLOW))
                      .append(Component.text(":"))
                      .build());
            boolean canBuild = currentClaim.getTrustType(player).canBuild();
            Component ownerName = Component.text((currentClaim.isOwner(player)
                                                  ? "Your"
                                                  : currentClaim.getOwnerGenitive()));
            Component claimName = currentClaim.getName() != null
                ? Component.text(currentClaim.getName(), null, TextDecoration.ITALIC)
                : Component.text("Claim");
            lines.add(Component.text().color(canBuild ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                      .append(Component.space())
                      .append(ownerName)
                      .append(Component.space())
                      .append(claimName)
                      .build());
        }
        event.add(plugin, Priority.DEFAULT, lines);
    }
}
