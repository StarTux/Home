package com.cavetale.home;

import com.cavetale.core.command.CommandWarn;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

@RequiredArgsConstructor
public final class Session {
    private final HomePlugin plugin;
    private final Player player;
    @Setter private Function<PlayerInteractEvent, Boolean> playerInteractCallback = null;
    private Runnable confirmCallback = null;
    private String confirmMessage;
    private List<Page> storedPages = new ArrayList<>();

    void disable() {
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
        ComponentBuilder cb = new ComponentBuilder(message).color(ChatColor.WHITE);
        cb.append(" ").reset();
        cb.append("[Confirm]").color(ChatColor.GREEN)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/subclaim confirm"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                  .fromLegacyText(ChatColor.GREEN + "Confirm" + "\n" + ChatColor.RESET + message)));
        cb.append(" ").reset();
        cb.append("[Cancel]").color(ChatColor.RED)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/subclaim cancel"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                  .fromLegacyText(ChatColor.GREEN + "Cancel" + "\n" + ChatColor.RESET + message)));
        player.sendMessage(cb.create());
    }

    /**
     * When a prior confirmation request is cancelled by the player.
     */
    public void cancelCommand() {
        if (confirmCallback == null) return;
        String message = confirmMessage;
        confirmCallback = null;
        confirmMessage = null;
        player.sendMessage(ChatColor.RED + "Cancelled: " + message);
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
            player.sendMessage(ChatColor.RED + warn.getMessage());
        }
    }

    public void setPages(List<Page> pages) {
        storedPages = new ArrayList<>(pages);
    }

    public void showStoredPage() {
        if (storedPages.isEmpty()) return;
        Page first = storedPages.remove(0);
        first.send(player);
        if (!storedPages.isEmpty()) {
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(storedPages.size() == 1
                      ? "There is 1 more page"
                      : "There are " + storedPages.size() + " more pages")
                .color(ChatColor.WHITE);
            cb.append(" ").reset();
            cb.append("[View Next]")
                .color(ChatColor.GREEN)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/homes page next"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + "View Next Page")));
            player.sendMessage(cb.create());
        }
    }
}
