package com.cavetale.home;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class VisitCommand extends PlayerCommand {
    private final HomePlugin plugin;

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length == 1 && args[0].equals("help")) return false;
        if (args.length == 0) {
            listPublicHomes(player);
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
        Claim claim = plugin.getClaimAt(location);
        if (claim != null && !claim.canBuild(home.getOwner())) {
            throw new Wrong("The invite is no longer valid in this claim");
        }
        final String ownerName = home.getOwnerName();
        final String publicName = home.getPublicName();
        plugin.warpTo(player, location, () -> {
                player.sendMessage(ChatColor.GREEN + "Teleported to "
                                   + ownerName + "'s public home \""
                                   + publicName + "\"");
                home.onVisit(player.getUniqueId());
            });
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

    public void listPublicHomes(Player player) {
        List<Home> publicHomes = plugin.getHomes().stream()
            .filter(h -> h.getPublicName() != null)
            .peek(Home::updateScore)
            .sorted(Home::rank)
            .collect(Collectors.toList());
        player.sendMessage("");
        ComponentBuilder cb;
        if (publicHomes.size() == 1) {
            cb = frame(new ComponentBuilder(""), "One public home");
            player.spigot().sendMessage(cb.create());
        } else {
            cb = frame(new ComponentBuilder(""), publicHomes.size() + " public homes");
            player.spigot().sendMessage(cb.create());
        }
        int pageLen = 8;
        List<Page> pages = new ArrayList<>(publicHomes.size() / pageLen);
        Page page = new Page();
        pages.add(page);
        for (Home home : publicHomes) {
            String cmd = "/visit " + home.getPublicName();
            cb = new ComponentBuilder("");
            cb.append(" + ").color(ChatColor.AQUA);
            cb.append(home.getPublicName()).color(ChatColor.WHITE);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            Text txt = new Text(ChatColor.BLUE + cmd + "\n" + ChatColor.WHITE + ChatColor.ITALIC + "Visit this home");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append(" by " + home.getOwnerName()).color(ChatColor.GRAY);
            page.addLine(new TextComponent(cb.create()));
            if (page.lineCount() >= pageLen) {
                page = new Page();
                pages.add(page);
            }
        }
        pages.removeIf(Page::isEmpty);
        plugin.sessions.of(player).setPages(pages);
        plugin.sessions.of(player).showStoredPage();
        player.sendMessage("");
    }
}
