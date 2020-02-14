package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class HomesCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length == 1 && args[0].equals("help")) return false;
        final UUID playerId = player.getUniqueId();
        if (args.length == 0) return printHomesInfo(player);
        switch (args[0]) {
        case "set":
            return plugin.getSetHomeCommand().onCommand(player, Arrays.copyOfRange(args, 1, args.length));
        case "visit":
            return plugin.getVisitCommand().onCommand(player, Arrays.copyOfRange(args, 1, args.length));
        case "invite":
            if (args.length == 2 || args.length == 3) {
                return plugin.getInviteHomeCommand().onCommand(player, Arrays.copyOfRange(args, 1, args.length));
            }
            break;
        case "uninvite": {
            if (args.length != 2 && args.length != 3) return printHomesInfo(player);
            final String targetName = args[1];
            UUID target = GenericEvents.cachedPlayerUuid(targetName);
            if (target == null) throw new Wrong("Player not found: " + targetName + "!");
            Home home;
            if (args.length >= 3) {
                String arg = args[2];
                home = plugin.findHome(player.getUniqueId(), arg);
                if (home == null) throw new Wrong("Home not found: " + arg + "!");
            } else {
                home = plugin.findHome(player.getUniqueId(), null);
                if (home == null) throw new Wrong("Default home not set.");
            }
            if (!home.invites.contains(target)) throw new Wrong("Player not invited.");
            plugin.getDb().find(HomeInvite.class).eq("home_id", home.getId()).eq("invitee", target).deleteAsync(null);
            home.getInvites().remove(target);
            player.sendMessage(ChatColor.GREEN + targetName + " uninvited.");
            return true;
        }
        case "public":
            if (args.length == 2 || args.length == 3) {
                String homeName = args[1];
                Home home = plugin.findHome(playerId, homeName);
                if (home == null) throw new Wrong("Home not found: " + homeName);
                if (home.getPublicName() != null) throw new Wrong("Home is already public under the alias \"" + home.getPublicName() + "\"");
                String publicName = args.length >= 3 ? args[2] : home.getName();
                if (publicName == null) throw new Wrong("Please supply a public name for this home");
                if (plugin.findPublicHome(publicName) != null) throw new Wrong("A public home by that name already exists. Please supply a different alias.");
                home.setPublicName(publicName);
                plugin.getDb().saveAsync(home, null, "public_name");
                String cmd = "/visit " + publicName;
                ComponentBuilder cb = new ComponentBuilder("")
                    .append("Home made public. Players may visit via ").color(ChatColor.WHITE)
                    .append(cmd).color(ChatColor.GREEN)
                    .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + cmd + "\nCan also be found under /visit.")));
                player.spigot().sendMessage(cb.create());
                return true;
            }
            break;
        case "delete":
            if (args.length == 1 || args.length == 2) {
                String homeName = args.length >= 2 ? args[1] : null;
                Home home = plugin.findHome(playerId, homeName);
                if (home == null) {
                    if (homeName == null) {
                        throw new Wrong("Your primary home is not set");
                    } else {
                        throw new Wrong("You do not have a home named \"" + homeName + "\"");
                    }
                }
                plugin.getDb().find(HomeInvite.class).eq("home_id", home.getId()).delete();
                plugin.getDb().delete(home);
                plugin.getHomes().remove(home);
                if (homeName == null) {
                    player.sendMessage(ChatColor.YELLOW + "Primary home unset. The " + ChatColor.ITALIC + "/home" + ChatColor.YELLOW + " command will take you to your bed spawn or primary claim.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Home \"" + homeName + "\" deleted");
                }
                return true;
            }
            break;
        case "info":
            if (args.length == 1 || args.length == 2) {
                Home home;
                if (args.length == 1) {
                    home = plugin.findHome(playerId, null);
                } else {
                    home = plugin.findHome(playerId, args[1]);
                }
                if (home == null) throw new Wrong("Home not found.");
                player.sendMessage("");
                if (home.getName() == null) {
                    player.spigot().sendMessage(frame(new ComponentBuilder(""), "Primary Home Info").create());
                } else {
                    player.spigot().sendMessage(frame(new ComponentBuilder(""), home.getName() + " Info").create());
                }
                StringBuilder sb = new StringBuilder();
                for (UUID inviteId : home.getInvites()) {
                    sb.append(" ").append(GenericEvents.cachedPlayerName(inviteId));
                }
                player.sendMessage(ChatColor.GRAY + " Location: " + ChatColor.WHITE
                                   + String.format("%s %d,%d,%d",
                                                   plugin.worldDisplayName(home.getWorld()),
                                                   (int) Math.floor(home.getX()),
                                                   (int) Math.floor(home.getY()),
                                                   (int) Math.floor(home.getZ())));
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append(" Invited: " + home.getInvites().size()).color(ChatColor.GRAY);
                for (UUID invitee : home.getInvites()) {
                    cb.append(" ").append(GenericEvents.cachedPlayerName(invitee)).color(ChatColor.WHITE);
                }
                player.spigot().sendMessage(cb.create());
                player.sendMessage(ChatColor.GRAY + " Public: " + ChatColor.WHITE + (home.getPublicName() != null ? "yes" : "no"));
                player.sendMessage("");
                return true;
            }
            break;
        case "invites":
            if (args.length == 1) {
                ComponentBuilder cb = new ComponentBuilder("Your invites:").color(ChatColor.GRAY);
                for (Home home : plugin.getHomes()) {
                    if (home.isInvited(playerId)) {
                        String homename = home.getName() == null ? home.getOwnerName() + ":" : home.getOwnerName() + ":" + home.getName();
                        cb.append(" ");
                        cb.append(homename).color(ChatColor.GREEN)
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home " + homename))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GREEN + "home " + homename + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Use this home invite.")));
                    }
                }
                player.spigot().sendMessage(cb.create());
                return true;
            }
            break;
        default:
            break;
        }
        return printHomesInfo(player);
    }

    public boolean printHomesInfo(Player player) {
        final UUID playerId = player.getUniqueId();
        List<Home> playerHomes = plugin.findHomes(playerId);
        if (playerHomes.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No homes to show");
            return true;
        }
        player.sendMessage("");
        if (playerHomes.size() == 1) {
            player.sendMessage(ChatColor.BLUE + "You have one home");
        } else {
            player.sendMessage(ChatColor.BLUE + "You have " + playerHomes.size() + " homes");
        }
        for (Home home : playerHomes) {
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(" + ").color(ChatColor.BLUE);
            if (home.getName() == null) {
                cb.append("Primary").color(ChatColor.GRAY).italic(true)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/home"))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + "/home\n" + ChatColor.WHITE + ChatColor.ITALIC + "Teleport to your primary home")))
                    .append("").italic(false);
            } else {
                String cmd = "/home " + home.getName();
                cb.append(home.getName()).color(ChatColor.WHITE)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Teleport to your home \"" + home.getName() + "\".")));
            }
            cb.append(" ");
            String infocmd = home.getName() == null ? "/homes info" : "/homes info " + home.getName();
            cb.append("(info)").color(ChatColor.GRAY)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, infocmd))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GRAY + infocmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Get more info on this home.")));
            if (home.getInvites().size() == 1) cb.append(" 1 invite").color(ChatColor.GREEN);
            if (home.getInvites().size() > 1) cb.append(home.getInvites().size() + " invites").color(ChatColor.GREEN);
            if (home.getPublicName() != null) cb.append(" public").reset().color(ChatColor.AQUA);
            player.spigot().sendMessage(cb.create());
        }
        int homeInvites = (int) plugin.getHomes().stream()
            .filter(h -> h.isInvited(playerId))
            .count();
        if (homeInvites > 0) {
            ComponentBuilder cb = new ComponentBuilder(" ");
            if (homeInvites == 1) {
                cb.append("You have one home invite ").color(ChatColor.BLUE);
            } else {
                cb.append("You have " + homeInvites + " home invites ").color(ChatColor.BLUE);
            }
            cb.append("[List]").color(ChatColor.LIGHT_PURPLE)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/homes invites"))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.LIGHT_PURPLE + "/homes invites\n" + ChatColor.WHITE + ChatColor.ITALIC + "List home invites")));
            player.spigot().sendMessage(cb.create());
        }
        final ChatColor buttonColor = ChatColor.GREEN;
        ComponentBuilder cb = new ComponentBuilder("")
            .append("[Set]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sethome "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/sethome [name]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set a home.")))
            .append("  ")
            .append("[Info]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes info "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/homes info " + ChatColor.ITALIC + "<home>\n" + ChatColor.WHITE + ChatColor.ITALIC + "Get home info.")))
            .append("  ")
            .append("[Delete]").color(ChatColor.DARK_RED)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes delete "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.DARK_RED + "/homes delete " + ChatColor.ITALIC + "<home>\n" + ChatColor.WHITE + ChatColor.ITALIC + "Delete home.")));
        player.spigot().sendMessage(cb.create());
        cb = new ComponentBuilder("")
            .append("[Invite]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes invite "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/homes invite " + ChatColor.ITALIC + "<player> [home]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Set a home.")))
            .append("  ")
            .append("[Uninvite]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes uninvite "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/homes uninvite " + ChatColor.ITALIC + "<player> [home]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Uninvite someone.")))
            .append("  ")
            .append("[Public]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/homes public "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/homes public " + ChatColor.ITALIC + "<home> [alias]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Make home public.")))
            .append("  ")
            .append("[Visit]").color(buttonColor)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/visit"))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(buttonColor + "/visit " + ChatColor.ITALIC + "[home]\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit a public home.")));
        player.spigot().sendMessage(cb.create());
        player.sendMessage("");
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        String cmd = args.length == 0 ? "" : args[0];
        if (args.length == 1) {
            return Arrays.asList("set", "invite", "uninvite", "visit", "public", "delete", "info", "invites").stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
        }
        if (args.length == 2 && cmd.equals("set")) return Collections.emptyList();
        if (args.length == 2 && cmd.equals("visit")) {
            return plugin.getHomes().stream().filter(h -> h.getPublicName() != null && h.getPublicName().startsWith(arg)).map(Home::getPublicName).collect(Collectors.toList());
        }
        if (args.length == 2 && cmd.equals("public")) {
            return plugin.getHomes().stream().filter(h -> h.isOwner(player.getUniqueId()) && h.getName() != null && h.getPublicName() == null).map(Home::getName).collect(Collectors.toList());
        }
        if (args.length == 3 && cmd.equals("public")) {
            return Collections.emptyList();
        }
        if (args.length == 2 && cmd.equals("info")) {
            return plugin.getHomes().stream().filter(h -> h.isOwner(player.getUniqueId())).map(h -> h.getPublicName() == null ? "" : h.getPublicName()).collect(Collectors.toList());
        }
        if (args.length == 2 && cmd.equals("delete")) {
            return plugin.getHomes().stream().filter(h -> h.isOwner(player.getUniqueId())).map(h -> h.getPublicName() == null ? "" : h.getPublicName()).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/homes", new String[]{}, "View the homes menu.");
        commandHelp(player, "/homes set", new String[]{"[name]"}, "Set a home.");
        commandHelp(player, "/homes invite", new String[]{"<player>", "[name]"}, "Allow someone to use your home.");
        commandHelp(player, "/homes public", new String[]{"<name>", "[alias]"}, "Make a home public.");
        commandHelp(player, "/homes delete", new String[]{"[name]"}, "Delete a home.");
    }
}
