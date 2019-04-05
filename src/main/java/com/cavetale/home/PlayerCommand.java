package com.cavetale.home;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

abstract class PlayerCommand implements TabExecutor {
    protected static final String C_CMD = "" + ChatColor.YELLOW;
    protected static final String C_ARG = "" + ChatColor.GRAY + ChatColor.UNDERLINE;
    protected static final String C_DASH = "" + ChatColor.DARK_GRAY + " - ";
    protected static final String C_DESC = "" + ChatColor.WHITE + ChatColor.ITALIC;
    protected static final String C_R = "" + ChatColor.RESET;

    static final class CommandException extends Exception {
        CommandException(String msg) {
            super(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected.");
            return true;
        }
        Player player = (Player)sender;
        try {
            if (!onCommand(player, args)) commandHelp(player);
        } catch (CommandException e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
        }
        return true;
    }

    abstract boolean onCommand(Player player, String[] args) throws CommandException;

    abstract void commandHelp(Player player);

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        return onTabComplete((Player)sender, args);
    }

    abstract List<String> onTabComplete(Player player, String[] args);

    // Utilty

    protected List<String> complete(String arg, Stream<String> opt) {
        return opt.filter(o -> o.startsWith(arg)).collect(Collectors.toList());
    }

    protected void fail(String msg) throws CommandException {
        throw new CommandException(msg);
    }

    protected ComponentBuilder frame(ComponentBuilder cb, String text) {
        ChatColor fc = ChatColor.BLUE;
        return cb.append("            ").color(fc).strikethrough(true)
            .append("[ ").color(fc).strikethrough(false)
            .append(text).color(ChatColor.WHITE)
            .append(" ]").color(fc)
            .append("            ").color(fc).strikethrough(true)
            .append("").strikethrough(false);
    }

    protected void commandHelp(Player player, String cmd, String[] args, String desc) {
        player.sendMessage(C_CMD + cmd + " " + C_ARG
                           + Arrays.stream(args).collect(Collectors.joining(C_R + " " + C_ARG))
                           + C_DASH + C_DESC + desc);
    }
}
