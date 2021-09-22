package com.cavetale.home;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

abstract class PlayerCommand implements TabExecutor {

    static final class Wrong extends Exception {
        Wrong(final String msg) {
            super(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected.");
            return true;
        }
        Player player = (Player) sender;
        try {
            if (!onCommand(player, args)) commandHelp(player);
        } catch (Wrong e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
        }
        return true;
    }

    abstract boolean onCommand(Player player, String[] args) throws Wrong;

    abstract void commandHelp(Player player);

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        return onTabComplete((Player) sender, args);
    }

    abstract List<String> onTabComplete(Player player, String[] args);

    // Utilty

    protected List<String> complete(String arg, Stream<String> opt) {
        return opt.filter(o -> o.startsWith(arg)).collect(Collectors.toList());
    }

    protected void fail(String msg) throws Wrong {
        throw new Wrong(msg);
    }

    protected static Component frame(String text) {
        return Component.text().color(NamedTextColor.BLUE)
            .append(Component.text("            ", null, TextDecoration.STRIKETHROUGH))
            .append(Component.text("[ "))
            .append(Component.text(text, NamedTextColor.WHITE))
            .append(Component.text(" ]"))
            .append(Component.text("            ", null, TextDecoration.STRIKETHROUGH))
            .build();
    }

    protected static ComponentBuilder frame(ComponentBuilder cb, String text) {
        ChatColor fc = ChatColor.BLUE;
        return cb.append("            ").color(fc).strikethrough(true)
            .append("[ ").color(fc).strikethrough(false)
            .append(text).color(ChatColor.WHITE)
            .append(" ]").color(fc)
            .append("            ").color(fc).strikethrough(true)
            .append("").strikethrough(false);
    }

    protected void commandHelp(Player player, String cmd, String[] args, String desc) {
        final String carg = " " + ChatColor.GRAY + ChatColor.UNDERLINE;
        String argc = args == null || args.length == 0
            ? ""
            : carg + Arrays.stream(args)
            .collect(Collectors.joining(ChatColor.RESET + carg));
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append(cmd).color(ChatColor.YELLOW);
        cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd));
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(ChatColor.YELLOW
                                                             + cmd + "\n"
                                                             + ChatColor.WHITE
                                                             + desc)));
        cb.append(argc).append(" - ").color(ChatColor.GRAY);
        cb.append(desc).color(ChatColor.WHITE);
        player.spigot().sendMessage(cb.create());
    }
}
