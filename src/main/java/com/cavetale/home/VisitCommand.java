package com.cavetale.home;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
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
    public boolean onCommand(Player player, String[] args) throws CommandException {
        if (args.length == 0) {
            List<Home> publicHomes = plugin.getHomes().stream().filter(h -> h.getPublicName() != null).collect(Collectors.toList());
            player.sendMessage("");
            if (publicHomes.size() == 1) {
                player.spigot().sendMessage(frame(new ComponentBuilder(""), "One public home").create());
            } else {
                player.spigot().sendMessage(frame(new ComponentBuilder(""), publicHomes.size() + " public homes").create());
            }
            for (Home home : publicHomes) {
                String cmd = "/visit " + home.getPublicName();
                ComponentBuilder cb = new ComponentBuilder("")
                    .append(" + ").color(ChatColor.AQUA)
                    .append(home.getPublicName()).color(ChatColor.WHITE)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.BLUE + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home")))
                    .append(" by " + home.getOwnerName()).color(ChatColor.GRAY);
                player.spigot().sendMessage(cb.create());
            }
            player.sendMessage("");
            return true;
        }
        Home home = plugin.findPublicHome(args[0]);
        if (home == null) {
            throw new CommandException("Public home not found: " + args[0]);
        }
        Location location = home.createLocation();
        if (location == null) {
            throw new CommandException("Could not take you to this home.");
        }
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + home.getOwnerName() + "'s public home \"" + home.getPublicName() + "\"");
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        String cmd = args.length == 0 ? "" : args[0];
        if (args.length == 1) {
            return plugin.getHomes().stream().filter(h -> h.getPublicName() != null && h.getPublicName().startsWith(arg)).map(Home::getPublicName).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/visit", new String[]{}, "Public home menu.");
        commandHelp(player, "/visit", new String[]{"[name]"}, "Visit public home.");
    }
}
