package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.home.sql.SQLHome;
import com.cavetale.home.sql.SQLHomeInvite;
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
        rootNode.addChild("transfer").arguments("<from> <to>")
            .description("Transfer all of a Player's Homes")
            .completers(PlayerCache.NAME_COMPLETER,
                        PlayerCache.NAME_COMPLETER)
            .senderCaller(this::transfer);
        rootNode.addChild("debug").denyTabCompletion()
            .description("Debug Spam")
            .senderCaller(this::debug);
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (args.length != 0) return false;
        plugin.loadFromDatabase();
        sender.sendMessage(text("Database reloaded", AQUA));
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
        List<SQLHome> homes = plugin.getHomes().findOwnedHomes(player.uuid);
        int count = homes.size();
        sender.sendMessage(text(player.name + " has " + count
                                + (count == 1 ? " home:" : " homes:"),
                                YELLOW));
        for (SQLHome home : homes) {
            String brief = "-"
                + "id=" + home.getId()
                + " name=" + (home.getName() != null ? home.getName() : "-")
                + (" loc=" + home.getWorld() + ":"
                   + blk(home.getX()) + "," + blk(home.getY()) + "," + blk(home.getZ()))
                + " public=" + (home.getPublicName() != null
                                ? home.getPublicName() : "-");
            sender.sendMessage(text(brief, YELLOW));
        }
        return true;
    }

    protected void ignore(Player player) {
        if (plugin.doesIgnoreClaims(player)) {
            plugin.ignoreClaims(player, false);
            player.sendMessage(text("Respecting home and claim permissions", YELLOW));
        } else {
            plugin.ignoreClaims(player, true);
            player.sendMessage(text("Ignoring home and claim permissions", YELLOW));
        }
    }

    private boolean debug(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            String worldName = args[0];
            plugin.getClaimCache().debug(sender, worldName);
        } else {
            for (String it : plugin.localHomeWorlds) {
                sender.sendMessage("Local home world: " + it);
            }
            for (var it : plugin.worldList) {
                sender.sendMessage("HomeWorld: " + it);
            }
        }
        return true;
    }

    private boolean transfer(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache from = PlayerCache.forArg(args[0]);
        if (from == null) throw new CommandWarn("Player not found: " + args[0]);
        PlayerCache to = PlayerCache.forArg(args[1]);
        if (to == null) throw new CommandWarn("Player not found: " + args[1]);
        if (from.equals(to)) throw new CommandWarn("Players are identical: " + from.getName());
        int homeCount = 0;
        for (SQLHome home : plugin.getHomes()) {
            if (from.uuid.equals(home.getOwner())) {
                plugin.db.delete(home);
                home.setId(null);
                home.setOwner(to.uuid);
                plugin.db.save(home);
                homeCount += 1;
            }
            if (home.getInvites().remove(from.uuid)) {
                // This will not persist!
                home.getInvites().add(to.uuid);
            }
        }
        List<SQLHomeInvite> inviteRows = plugin.db.find(SQLHomeInvite.class)
            .eq("invitee", from.uuid)
            .findList();
        int inviteCount  = 0;
        if (!inviteRows.isEmpty()) {
            for (SQLHomeInvite inviteRow : inviteRows) {
                inviteCount += plugin.db.insertIgnore(new SQLHomeInvite(inviteRow.getHomeId(), to.uuid));
            }
            plugin.db.delete(inviteRows);
        }
        if (homeCount == 0 && inviteRows.isEmpty()) throw new CommandWarn(from.name + " does not have any homes or invites!");
        plugin.loadFromDatabase();
        sender.sendMessage(text("Transferred homes from " + from.name + " to " + to.name + ":"
                                + " homes=" + homeCount
                                + " invites=" + inviteCount + "/" + inviteRows.size(),
                                AQUA));
        return true;
    }
}
