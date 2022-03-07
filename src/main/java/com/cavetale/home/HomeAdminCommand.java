package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class HomeAdminCommand extends AbstractCommand<HomePlugin> {
    protected HomeAdminCommand(final HomePlugin plugin) {
        super(plugin, "homeadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload Configs and Database")
            .senderCaller(this::reload);
        rootNode.addChild("list").arguments("<player>")
            .description("List Player Homes")
            .completers(PlayerCache.NAME_COMPLETER)
            .senderCaller(this::list);
        rootNode.addChild("ignore").denyTabCompletion()
            .description("Toggle Home/Claim Ignore")
            .playerCaller(this::ignore);
        rootNode.addChild("debug").denyTabCompletion()
            .description("Debug Spam")
            .senderCaller(this::debug);
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.loadFromConfig();
        plugin.loadFromDatabase();
        sender.sendMessage(text("Configuration files and databases reloaded", AQUA));
        return true;
    }

    private static int blk(double c) {
        return (int) Math.floor(c);
    }

    private boolean list(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        PlayerCache player = PlayerCache.forName(name);
        if (player == null) throw new CommandWarn("Player not found: " + name);
        List<Home> homes = plugin.findHomes(player.uuid);
        int count = homes.size();
        sender.sendMessage(text(player.name + " has " + count
                                          + (count == 1 ? " home:" : " homes:"),
                                          YELLOW));
        for (Home home : homes) {
            String brief = "-"
                + "id=" + home.id
                + " name=" + (home.name != null ? home.name : "-")
                + (" loc=" + home.world + ":"
                   + blk(home.x) + "," + blk(home.y) + "," + blk(home.z))
                + " public=" + (home.publicName != null
                                 ? home.publicName : "-");
            sender.sendMessage(text(brief, YELLOW));
        }
        return true;
    }

    private boolean ignore(Player player, String[] args) {
        if (args.length != 0) return false;
        if (plugin.doesIgnoreClaims(player)) {
            plugin.ignoreClaims(player, false);
            player.sendMessage(text("Respecting home and claim permissions", YELLOW));
        } else {
            plugin.ignoreClaims(player, true);
            player.sendMessage(text("Ignoring home and claim permissions", YELLOW));
        }
        return true;
    }

    private boolean debug(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            String worldName = args[0];
            plugin.getClaimCache().debug(sender, worldName);
        } else {
            sender.sendMessage("Local home worlds: " + plugin.localHomeWorlds);
        }
        return true;
    }
}
