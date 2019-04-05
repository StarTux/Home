package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class InviteHomeCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2) return false;
        final UUID playerId = player.getUniqueId();
        String targetName = args[0];
        UUID targetId = GenericEvents.cachedPlayerUuid(targetName);
        if (targetId == null) fail("Player not found: " + targetName);
        String homeName = args.length >= 2 ? args[1] : null;
        Home home = this.plugin.findHome(playerId, homeName);
        if (home == null) {
            if (homeName == null) {
                fail("Your primary home is not set");
            } else {
                fail("You have no home named " + homeName);
            }
        }
        if (!home.invites.contains(targetId)) {
            HomeInvite invite = new HomeInvite(home.getId(), targetId);
            this.plugin.getDb().saveAsync(invite, null);
            home.invites.add(targetId);
        }
        player.sendMessage(ChatColor.GREEN + "Invite sent to " + targetName);
        Player target = this.plugin.getServer().getPlayer(targetId);
        if (target != null) {
            if (home.getName() == null) {
                String cmd = "/home " + player.getName() + ":";
                ComponentBuilder cb = new ComponentBuilder("")
                    .append(player.getName() + " invited you to their primary home: ").color(ChatColor.WHITE)
                    .append("[Visit]").color(ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")));
                target.spigot().sendMessage(cb.create());
            } else {
                String cmd = "/home " + player.getName() + ":" + home.getName();
                ComponentBuilder cb = new ComponentBuilder("")
                    .append(player.getName() + " invited you to their home: ").color(ChatColor.WHITE)
                    .append("[" + home.getName() + "]").color(ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")));
                target.spigot().sendMessage(cb.create());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/invitehome ", new String[]{"<player>"}, "Invite to primary home.");
        commandHelp(player, "/invitehome ", new String[]{"<player>", "<name>"}, "Invite to named home.");
    }
}
