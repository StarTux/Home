package com.cavetale.home;

import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.player.PluginPlayerQuery;
import com.cavetale.home.sql.SQLHomeWorld;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class Session {
    private static final int MAX_NOTIFY = 100;
    private final HomePlugin plugin;
    private final UUID uuid;
    @Setter private Function<PlayerInteractEvent, Boolean> playerInteractCallback = null;
    private Runnable confirmCallback = null;
    private String confirmMessage;
    private List<Component> storedPages = new ArrayList<>();
    private long notifyCooldown = 0L;
    @Getter @Setter private ClaimGrowSnippet claimGrowSnippet;
    private SQLHomeWorld currentHomeWorld;
    private Claim currentClaim;
    private int ticks;
    private int notifyTicks;
    @Getter @Setter private ClaimToolSession claimTool;

    public Session(final HomePlugin plugin, final Player player) {
        this.plugin = plugin;
        this.uuid = player.getUniqueId();
        final World world = player.getWorld();
        if (plugin.isLocalHomeWorld(world)) {
            currentHomeWorld = plugin.findHomeWorld(world.getName());
            currentClaim = plugin.getClaimAt(player.getLocation());
        }
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
     * Click a block with the claim tool.
     *
     * @return true if the event was handled by this session in some
     *   way, false otherwise. It is up to the callback to cancel the
     *   event if required.
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
        Component confirmTooltip = join(separator(newline()),
                                        text("Confirm", GREEN),
                                        text(message, GRAY));
        Component cancelTooltip = join(separator(newline()),
                                       text("Cancel", GREEN),
                                       text(message, GRAY));
        getPlayer().sendMessage(text().color(WHITE)
                                .content(message)
                                .append(space())
                                .append(text().content("[Confirm]").color(GREEN)
                                        .clickEvent(ClickEvent.runCommand("/subclaim confirm"))
                                        .hoverEvent(HoverEvent.showText(confirmTooltip)))
                                .append(space())
                                .append(text().content("[Cancel]").color(RED)
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
        getPlayer().sendMessage(text("Cancelled: " + message, RED));
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
            getPlayer().sendMessage(text(warn.getMessage(), RED));
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
        player.sendMessage(text().color(GRAY)
                           .content("Showing page " + (pageIndex + 1) + "/" + storedPages.size())
                           .append(space())
                           .append(text("[View Next]", GREEN))
                           .clickEvent(ClickEvent.runCommand("/homes page " + (pageIndex + 2)))
                           .hoverEvent(HoverEvent.showText(text("View Next Page", GREEN))));
        return true;
    }

    public void notify(Player player, Claim claim) {
        long now = System.currentTimeMillis();
        if (notifyCooldown > now) return;
        notifyCooldown = now + 1000L;
        player.sendActionBar(text("This claim belongs to " + claim.getOwnerName(), RED));
    }

    public boolean notify(Player player, Component component) {
        long now = System.currentTimeMillis();
        if (notifyCooldown > now) return false;
        notifyCooldown = now + 1000L;
        player.sendActionBar(component);
        return true;
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
            Title title = Title.title(text("WARNING", RED, BOLD),
                                      text("Approaching No-Fly Zone!", RED, BOLD),
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
            currentHomeWorld = null;
            currentClaim = null;
            ticks = 0;
            notifyTicks = 0;
            return;
        }
        currentHomeWorld = plugin.findHomeWorld(world.getName());
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
                Component msg = text("You cannot enter this claim!", TextColor.color(0xFF0000));
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
            notifyTicks = MAX_NOTIFY;
        }
        if (!kicked && oldClaim != currentClaim) {
            notifyClaimChange(player, oldClaim, currentClaim);
            notifyTicks = MAX_NOTIFY;
        }
        if (!kicked && currentClaim != null) {
            triggerClaimActions(player);
        }
        if (!kicked && ticks % 10 == 0) {
            tickNoFlyZone(player);
        }
        if (notifyTicks > 0) notifyTicks -= 1;
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
            Component msg = text("You cannot fly in this claim!", TextColor.color(0xFF0000));
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
                ? text("Leaving your claim" + namePart, GRAY)
                : text("Leaving " + oldClaim.getOwnerGenitive() + " claim" + namePart, GRAY);
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
                ? text("Entering your claim" + namePart, GRAY)
                : text("Entering " + newClaim.getOwnerGenitive() + " claim" + namePart, GRAY);
            player.sendActionBar(message);
            if (newClaim.getSetting(ClaimSetting.SHOW_BORDERS)) {
                plugin.highlightClaim(newClaim, player);
            }
        }
    }

    protected void onPlayerHud(Player player, PlayerHudEvent event) {
        if (currentHomeWorld == null) return;
        List<Component> footer = new ArrayList<>();
        if (currentClaim != null) {
            TrustType trust = currentClaim.getTrustType(player);
            String claimName = currentClaim.getName() != null
                ? currentClaim.getName()
                : currentClaim.getOwnerName();
            TextColor claimColor = trust.canBuild() ? BLUE : (trust.canInteract() ? AQUA : RED);
            Component claimLine = join(noSeparators(), text(tiny("claim "), GRAY), text(claimName, claimColor));
            footer.add(claimLine);
            if (notifyTicks > 0) {
                BossBar.Color bossColor = trust.canBuild() ? BossBar.Color.BLUE : BossBar.Color.RED;
                float progress = (float) notifyTicks / (float) MAX_NOTIFY;
                event.bossbar(PlayerHudPriority.LOWEST, claimLine, bossColor, BossBar.Overlay.PROGRESS, Set.of(), progress);
            }
        } else {
            footer.add(join(noSeparators(), text(tiny("claim "), GRAY), text(tiny("none"), DARK_GRAY)));
        }
        event.footer(PlayerHudPriority.DEFAULT, footer);
    }
}
