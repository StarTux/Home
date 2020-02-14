package com.cavetale.home;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class VisitCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length == 1 && args[0].equals("help")) return false;
        ComponentBuilder cb;
        BaseComponent[] txt;
        if (args.length == 0) {
            List<Home> publicHomes = plugin.getHomes().stream()
                .filter(h -> h.getPublicName() != null)
                .collect(Collectors.toList());
            player.sendMessage("");
            if (publicHomes.size() == 1) {
                cb = frame(new ComponentBuilder(""), "One public home");
                player.spigot().sendMessage(cb.create());
            } else {
                cb = frame(new ComponentBuilder(""), publicHomes.size() + " public homes");
                player.spigot().sendMessage(cb.create());
            }
            for (Home home : publicHomes) {
                String cmd = "/visit " + home.getPublicName();
                cb = new ComponentBuilder("");
                cb.append(" + ").color(ChatColor.AQUA);
                cb.append(home.getPublicName()).color(ChatColor.WHITE);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                txt = TextComponent
                    .fromLegacyText(ChatColor.BLUE + cmd + "\n"
                                    + ChatColor.WHITE + ChatColor.ITALIC
                                    + "Visit this home");
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
                cb.append(" by " + home.getOwnerName()).color(ChatColor.GRAY);
                player.spigot().sendMessage(cb.create());
            }
            player.sendMessage("");
            return true;
        }
        Home home = plugin.findPublicHome(args[0]);
        if (home == null) {
            throw new Wrong("Public home not found: " + args[0]);
        }
        Location location = home.createLocation();
        if (location == null) {
            throw new Wrong("Could not take you to this home.");
        }
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to "
                           + home.getOwnerName() + "'s public home \""
                           + home.getPublicName() + "\"");
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        String cmd = args.length == 0 ? "" : args[0];
        if (args.length == 1) {
            return plugin.getHomes().stream()
                .filter(h -> h.getPublicName() != null
                        && h.getPublicName().startsWith(arg))
                .map(Home::getPublicName).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/visit", new String[]{}, "Public home menu.");
        commandHelp(player, "/visit", new String[]{"[name]"}, "Visit public home.");
    }
}
