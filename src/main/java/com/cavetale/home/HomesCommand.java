package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.home.sql.SQLHome;
import com.cavetale.home.sql.SQLHomeInvite;
import com.cavetale.home.sql.SQLHomeWorld;
import com.cavetale.home.struct.BlockVector;
import com.winthier.playercache.PlayerCache;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class HomesCommand extends AbstractCommand<HomePlugin> {
    protected final int pageLen = 9;

    protected HomesCommand(final HomePlugin plugin) {
        super(plugin, "homes");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("list").denyTabCompletion()
            .description("List homes")
            .denyTabCompletion()
            .playerCaller(this::list);
        rootNode.addChild("set").arguments("[home]")
            .description("Set a home")
            .denyTabCompletion()
            .playerCaller(this::set);
        rootNode.addChild("home").arguments("[home]")
            .description("Visit your home")
            .completers(this::completeUsableHomes)
            .remotePlayerCaller(this::home);
        rootNode.addChild("visit").arguments("[name]")
            .description("Visit a public home")
            .completers(this::completePublicHomes)
            .remotePlayerCaller(this::visit);
        rootNode.addChild("invite").arguments("<player> [home]")
            .description("Invite someone")
            .completers(CommandArgCompleter.NULL, this::completeOwnedHomes)
            .playerCaller(this::invite);
        rootNode.addChild("uninvite").arguments("<player> [home]")
            .description("Retract invitation")
            .completers(CommandArgCompleter.NULL, this::completeOwnedHomes)
            .playerCaller(this::uninvite);
        rootNode.addChild("public").arguments("<home> [alias]")
            .description("Make home public")
            .completers(this::completePublicableHomes)
            .playerCaller(this::makePublic);
        rootNode.addChild("delete").arguments("<home>")
            .description("Delete home")
            .completers(this::completeOwnedHomes)
            .playerCaller(this::delete);
        rootNode.addChild("info").arguments("<home>")
            .description("View home info")
            .completers(this::completeOwnedHomes)
            .playerCaller(this::info);
        rootNode.addChild("invites").denyTabCompletion()
            .description("List invites")
            .playerCaller(this::invites);
        rootNode.addChild("page").arguments("<index>").hidden(true)
            .denyTabCompletion()
            .description("View page")
            .playerCaller(this::page);
    }

    protected boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        final UUID uuid = player.getUniqueId();
        List<SQLHome> playerHomes = plugin.homes.findOwnedHomes(uuid);
        if (playerHomes.isEmpty()) {
            throw new CommandWarn("No homes to show");
        }
        if (playerHomes.size() == 1) {
            player.sendMessage(Util.frame("You have one home"));
        } else {
            player.sendMessage(Util.frame("You have " + playerHomes.size() + " homes"));
        }
        final int pageCount = (playerHomes.size() - 1) / pageLen + 1;
        final List<Component> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            List<Component> lines = new ArrayList<>(pageLen);
            for (int i = 0; i < pageLen; i += 1) {
                int homeIndex = pageIndex * pageLen + i;
                if (homeIndex >= playerHomes.size()) break;
                SQLHome home = playerHomes.get(homeIndex);
                String cmd = home.isPrimary() ? "/home" : "/home " + home.getName();
                lines.add(text().color(WHITE)
                          .append(text(" + ", BLUE))
                          .append(home.isPrimary()
                                  ? text("Primary", GOLD)
                                  : text(home.getName(), WHITE))
                          .clickEvent(runCommand(cmd))
                          .hoverEvent(showText(join(separator(newline()),
                                                    text(cmd, GREEN),
                                                    text("Visit this home", GRAY))))
                          .build());
            }
            pages.add(join(separator(newline()), lines));
        }
        if (pages.size() == 1) {
            player.sendMessage(pages.get(0));
        } else {
            plugin.sessions.of(player).setPages(pages);
            plugin.sessions.of(player).showStoredPage(player, 0);
        }
        PluginPlayerEvent.Name.LIST_HOMES.make(plugin, player)
            .detail(Detail.COUNT, playerHomes.size())
            .callEvent();
        return true;
    }

    protected boolean home(RemotePlayer player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 0) {
            home0(player);
        } else {
            home1(player, args[0]);
        }
        return true;
    }

    private void home0(RemotePlayer player) {
        final UUID uuid = player.getUniqueId();
        // Try to find a set home
        SQLHome home = plugin.getHomes().findPrimaryHome(player.getUniqueId());
        if (home != null) {
            SQLHomeWorld homeWorld = plugin.findHomeWorld(home.getWorld());
            if (homeWorld != null && !homeWorld.isOnThisServer() && player.isPlayer()) {
                Connect.get().dispatchRemoteCommand(player.getPlayer(), "home", homeWorld.getServer());
                return;
            }
            BlockVector blockVector = home.createBlockVector();
            Claim claim = plugin.getClaimAt(blockVector);
            if (claim != null && !claim.canBuild(home.getOwner(), blockVector)) {
                throw new CommandWarn("This home location lacks build permission");
            }
            if (claim != null && claim.getTrustType(uuid).isBan()) {
                throw new CommandWarn("You are banned from this claim");
            }
            Location location = home.createLocation();
            if (location == null) {
                throw new CommandWarn("Primary home could not be found.");
            }
            player.bring(plugin, location, player2 -> {
                    if (player2 == null) return;
                    PluginPlayerEvent.Name.USE_PRIMARY_HOME.make(plugin, player2)
                        .detail(Detail.LOCATION, location)
                        .callEvent();
                    player2.sendMessage(text("Welcome home :)", GREEN));
                    player2.showTitle(Title.title(empty(),
                                                  text("Welcome home :)", GREEN),
                                                  Title.Times.times(Duration.ofMillis(500),
                                                                    Duration.ofSeconds(1),
                                                                    Duration.ofMillis(500))));
                });
            return;
        }
        // or any claim
        List<Claim> playerClaims = plugin.findClaims(uuid);
        if (!playerClaims.isEmpty()) {
            Claim claim = playerClaims.get(0);
            SQLHomeWorld homeWorld = plugin.findHomeWorld(claim.getWorld());
            if (homeWorld != null && !homeWorld.isOnThisServer() && player.isPlayer()) {
                Connect.get().dispatchRemoteCommand(player.getPlayer(), "home", homeWorld.getServer());
                return;
            }
            World bworld = plugin.getServer().getWorld(claim.getWorld());
            Area area = claim.getArea();
            final int x = area.centerX();
            final int z = area.centerY();
            bworld.getChunkAtAsync(x >> 4, z >> 4, (Consumer<Chunk>) c -> {
                    Location location = bworld.getHighestBlockAt(x, z).getLocation().add(0.5, 0.0, 0.5);
                    player.bring(plugin, location, player2 -> {
                            if (player2 == null) return;
                            player2.sendMessage(text("Welcome to your claim. :)",
                                                     GREEN));
                        });
                });
            return;
        }
        // Give up and default to a random build location, again.
        if (player.isPlayer()) {
            plugin.findPlaceToBuild(player);
        }
    }

    private void home1(RemotePlayer player, String arg) {
        final UUID uuid = player.getUniqueId();
        SQLHome home;
        if (arg.contains(":")) {
            String[] toks = arg.split(":", 2);
            String ownerName = toks[0];
            String homeName = !toks[1].isEmpty() ? toks[1] : null;
            PlayerCache target = PlayerCache.forName(ownerName);
            if (target == null) {
                throw new CommandWarn("Player not found: " + ownerName);
            }
            home = plugin.getHomes().findOwnedHome(target.uuid, homeName);
            if (home == null) {
                throw new CommandWarn(homeName != null
                                      ? "Home not found: " + homeName + "!"
                                      : "Home not found!");
            }
            if (!plugin.doesIgnoreClaims(uuid) && !home.isInvited(uuid)) {
                throw new CommandWarn(homeName != null
                                      ? "Home not found: " + homeName + "!"
                                      : "Home not found!");
            }
        } else {
            home = plugin.getHomes().findOwnedHome(uuid, arg);
        }
        if (home == null) {
            throw new CommandWarn("Home not found: " + arg);
        }
        SQLHomeWorld homeWorld = plugin.findHomeWorld(home.getWorld());
        if (homeWorld != null && !homeWorld.isOnThisServer() && player.isPlayer()) {
            Connect.get().dispatchRemoteCommand(player.getPlayer(), "home " + arg, homeWorld.getServer());
            return;
        }
        Location location = home.createLocation();
        if (location == null) {
            throw new CommandWarn("Home could not be found.");
        }
        Claim claim = plugin.getClaimAt(location);
        if (claim != null && !claim.canBuild(home.getOwner(), home.createBlockVector())) {
            throw new CommandWarn("This home location lacks build permission");
        }
        player.bring(plugin, location, player2 -> {
                if (player2 == null) return;
                player2.sendMessage(text("Welcome home", GREEN));
                player2.showTitle(Title.title(empty(),
                                              text("Welcome home", GREEN),
                                              Title.Times.times(Duration.ofMillis(500),
                                                                Duration.ofSeconds(1),
                                                                Duration.ofMillis(500))));
                if (home.isOwner(uuid)) {
                    PluginPlayerEvent.Name.USE_NAMED_HOME
                        .make(plugin, player2)
                        .detail(Detail.NAME, home.getName())
                        .detail(Detail.LOCATION, location)
                        .callEvent();
                } else {
                    PluginPlayerEvent.Name.VISIT_HOME
                        .make(plugin, player2)
                        .detail(Detail.OWNER, home.getOwner())
                        .detail(Detail.NAME, home.getName())
                        .detail(Detail.LOCATION, location)
                        .callEvent();
                }
            });
    }

    protected boolean set(Player player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        UUID uuid = player.getUniqueId();
        if (!plugin.isLocalHomeWorld(player.getWorld())) {
            throw new CommandWarn("You cannot set homes in this world");
        }
        BlockVector blockVector = BlockVector.of(player.getLocation());
        Claim claim = plugin.getClaimAt(blockVector);
        if (claim != null && !claim.canBuild(player, blockVector)) {
            throw new CommandWarn("You cannot set homes in this claim");
        }
        String playerWorld = player.getWorld().getName();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();
        String homeName = args.length == 0 ? null : args[0];
        for (SQLHome home : plugin.getHomes().findOwnedHomes(uuid)) {
            if (home.isInWorld(playerWorld)
                && !home.isNamed(homeName)
                && Math.abs(playerX - (int) home.getX()) < Globals.HOME_MARGIN
                && Math.abs(playerZ - (int) home.getZ()) < Globals.HOME_MARGIN) {
                if (home.getName() == null) {
                    throw new CommandWarn("Your primary home is nearby");
                } else {
                    throw new CommandWarn("You have a home named \"" + home.getName() + "\" nearby");
                }
            }
        }
        SQLHome home = plugin.getHomes().findOwnedHome(uuid, homeName);
        if (home == null) {
            home = new SQLHome(uuid, player.getLocation(), homeName);
            plugin.getHomes().add(home);
            home.saveToDatabase();
        } else {
            home.setLocation(player.getLocation());
            home.saveToDatabase();
        }
        if (homeName == null) {
            player.sendMessage(text("Primary home set", GREEN));
            PluginPlayerEvent.Name.SET_PRIMARY_HOME.call(plugin, player);
        } else {
            player.sendMessage(text("Home \"" + homeName + "\" set", GREEN));
            PluginPlayerEvent.Name.SET_NAMED_HOME.make(plugin, player)
                .detail(Detail.NAME, homeName)
                .callEvent();
        }
        return true;
    }

    protected boolean info(Player player, String[] args) {
        if (args.length != 0 && args.length != 1) return false;
        final SQLHome home;
        if (args.length == 0) {
            home = plugin.getHomes().findPrimaryHome(player.getUniqueId());
        } else {
            home = plugin.getHomes().findOwnedHome(player.getUniqueId(), args[0]);
        }
        if (home == null) throw new CommandWarn("Home not found.");
        if (home.getName() == null) {
            player.sendMessage(Util.frame("Primary Home Info"));
        } else {
            player.sendMessage(Util.frame(home.getName() + " Info"));
        }
        StringBuilder sb = new StringBuilder();
        for (UUID inviteId : home.getInvites()) {
            sb.append(" ").append(PlayerCache.nameForUuid(inviteId));
        }
        player.sendMessage(text().color(WHITE)
                           .append(text(" Location: ", GRAY))
                           .append(text(String.format("%s %d,%d,%d",
                                                      plugin.worldDisplayName(home.getWorld()),
                                                      (int) Math.floor(home.getX()),
                                                      (int) Math.floor(home.getY()),
                                                      (int) Math.floor(home.getZ())))));
        TextComponent.Builder message = text().color(WHITE)
            .append(text(" Invited: " + home.getInvites().size(), GRAY));
        for (UUID invitee : home.getInvites()) {
            message.append(text(" " + PlayerCache.nameForUuid(invitee)));
        }
        player.sendMessage(message);
        player.sendMessage(text().color(WHITE)
                           .append(text(" Public: ", GRAY))
                           .append(text(home.getPublicName() != null ? "yes" : "no")));
        return true;
    }

    protected boolean invites(Player player, String[] args) {
        if (args.length != 0) return false;
        TextComponent.Builder message = text().color(WHITE)
            .append(text("Your invites: ", GRAY));
        UUID uuid = player.getUniqueId();
        for (SQLHome home : plugin.getHomes()) {
            if (home.isInvited(uuid)) {
                String homename = home.getName() == null
                    ? home.getOwnerName() + ":"
                    : home.getOwnerName() + ":" + home.getName();
                message.append(space());
                Component tooltip = join(separator(newline()),
                                         text("/home " + homename, GREEN),
                                         text("Use this home invite", GRAY));
                message.append(text(homename, GREEN)
                               .clickEvent(runCommand("/home " + homename))
                               .hoverEvent(showText(tooltip)));
            }
        }
        player.sendMessage(message);
        return true;
    }

    protected boolean invite(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        final UUID uuid = player.getUniqueId();
        String targetName = args[0];
        UUID targetId = PlayerCache.uuidForName(targetName);
        if (targetId == null) {
            throw new CommandWarn("Player not found: " + targetName);
        }
        if (player.getUniqueId().equals(targetId)) {
            throw new CommandWarn("You cannot invite yourself!");
        }
        String homeName = args.length >= 2 ? args[1] : null;
        SQLHome home = plugin.getHomes().findOwnedHome(uuid, homeName);
        if (home == null) {
            if (homeName == null) {
                throw new CommandWarn("Your primary home is not set");
            } else {
                throw new CommandWarn("You have no home named " + homeName);
            }
        }
        if (!home.getInvites().contains(targetId)) {
            SQLHomeInvite invite = new SQLHomeInvite(home.getId(), targetId);
            plugin.getDb().saveAsync(invite, r -> {
                    plugin.getConnectListener().broadcastHomeUpdate(home);
                });
            home.getInvites().add(targetId);
        }
        player.sendMessage(text("Invite sent to " + targetName, GREEN));
        RemotePlayer target = Connect.get().getRemotePlayer(targetId);
        if (target != null) {
            if (home.getName() == null) {
                String cmd = "/home " + player.getName() + ":";
                Component tooltip = join(separator(newline()),
                                         text(cmd, GREEN),
                                         text("Visit this home", GRAY));
                target.sendMessage(text(player.getName() + " invited you to their primary home ")
                                   .append(text("[Visit]", GREEN))
                                   .clickEvent(runCommand(cmd))
                                   .hoverEvent(showText(tooltip)));
            } else {
                String cmd = "/home " + player.getName() + ":" + home.getName();
                Component tooltip = join(separator(newline()),
                                         text(cmd, GREEN),
                                         text("Visit this home", GRAY));
                target.sendMessage(text(player.getName() + " invited you to their home " + home.getName() + " ")
                                   .append(text("[Visit]", GREEN))
                                   .clickEvent(runCommand(cmd))
                                   .hoverEvent(showText(tooltip)));
            }
        }
        PluginPlayerEvent.Name.INVITE_HOME.make(plugin, player)
            .detail(Detail.TARGET, targetId)
            .detail(Detail.NAME, home.getName())
            .callEvent();
        return true;
    }

    protected boolean uninvite(Player player, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        final String targetName = args[0];
        UUID target = PlayerCache.uuidForName(targetName);
        if (target == null) throw new CommandWarn("Player not found: " + targetName + "!");
        SQLHome home;
        if (args.length >= 2) {
            String arg = args[1];
            home = plugin.getHomes().findOwnedHome(player.getUniqueId(), arg);
            if (home == null) throw new CommandWarn("Home not found: " + arg + "!");
        } else {
            home = plugin.getHomes().findPrimaryHome(player.getUniqueId());
            if (home == null) throw new CommandWarn("Default home not set.");
        }
        if (!home.getInvites().contains(target)) throw new CommandWarn("Player not invited.");
        plugin.getDb().find(SQLHomeInvite.class)
            .eq("home_id", home.getId())
            .eq("invitee", target).deleteAsync(r -> {
                    plugin.getConnectListener().broadcastHomeUpdate(home);
                });
        home.getInvites().remove(target);
        player.sendMessage(text(targetName + " was uninvited", GREEN));
        PluginPlayerEvent.Name.UNINVITE_HOME.make(plugin, player)
            .detail(Detail.TARGET, target)
            .detail(Detail.NAME, home.getName())
            .callEvent();
        return true;
    }

    protected boolean makePublic(Player player, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        String homeName = args[0];
        SQLHome home = plugin.getHomes().findOwnedHome(player.getUniqueId(), homeName);
        if (home == null) throw new CommandWarn("Home not found: " + homeName);
        if (home.getPublicName() != null) {
            throw new CommandWarn("Home is already public under the alias \""
                                  + home.getPublicName() + "\"");
        }
        String publicName = args.length >= 2
            ? args[1]
            : home.getName();
        if (publicName == null) {
            throw new CommandWarn("Please supply a public name for this home");
        }
        if (plugin.getHomes().findPublicHome(publicName) != null) {
            throw new CommandWarn("A public home by that name already exists."
                                  + " Please supply a different alias.");
        }
        home.setPublicName(publicName);
        plugin.getDb().updateAsync(home, Set.of("public_name"), res -> {
                if (res == 0) {
                    home.setPublicName(null);
                    player.sendMessage(text("A public home by that name already exists."
                                            + " Please supply a different alias.", RED));
                    return;
                }
                plugin.getHomes().add(home);
                plugin.getConnectListener().broadcastHomeUpdate(home);
                String cmd = "/visit " + publicName;
                Component tooltip = join(separator(newline()),
                                         text(cmd, GREEN),
                                         text("Find all public homes", GRAY),
                                         text("via ", GRAY)
                                         .append(text("/visit", WHITE)));
                ComponentLike message = text()
                    .content("Home made public. Players may visit via ")
                    .append(text(cmd, GREEN))
                    .clickEvent(suggestCommand(cmd))
                    .hoverEvent(showText(tooltip));
                player.sendMessage(message);
            });
        return true;
    }

    protected boolean delete(Player player, String[] args) {
        if (args.length != 0 && args.length != 1) return false;
        String homeName = args.length >= 1
            ? args[0]
            : null;
        SQLHome home = plugin.getHomes().findOwnedHome(player.getUniqueId(), homeName);
        if (home == null) {
            if (homeName == null) {
                throw new CommandWarn("Your primary home is not set");
            } else {
                throw new CommandWarn("You do not have a home named \"" + homeName + "\"");
            }
        }
        plugin.deleteHome(home);
        if (homeName == null) {
            player.sendMessage(text("Your primary home was unset", GREEN));
        } else {
            player.sendMessage(text("Home \"" + homeName + "\" deleted", GREEN));
        }
        PluginPlayerEvent.Name.DELETE_HOME.make(plugin, player)
            .detail(Detail.NAME, homeName)
            .callEvent();
        return true;
    }

    protected boolean page(Player player, String[] args) {
        if (args.length != 1) return false;
        int page;
        try {
            page = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Not a number: " + args[0]);
        }
        if (!plugin.sessions.of(player).showStoredPage(player, page - 1)) {
            throw new CommandWarn("Page does not exist: " + page);
        }
        return true;
    }

    protected boolean visit(RemotePlayer player, String[] args) {
        if (args.length == 0) {
            if (player.isPlayer()) {
                listPublicHomes(player.getPlayer());
            }
            return true;
        }
        String homeName = String.join(" ", args);
        SQLHome home = plugin.getHomes().findPublicHome(homeName);
        if (home == null) {
            throw new CommandWarn("Public home not found: " + homeName);
        }
        SQLHomeWorld homeWorld = plugin.findHomeWorld(home.getWorld());
        if (homeWorld != null && !homeWorld.isOnThisServer() && player.isPlayer()) {
            Connect.get().dispatchRemoteCommand(player.getPlayer(), "visit " + homeName, homeWorld.getServer());
            return true;
        }
        BlockVector blockVector = home.createBlockVector();
        Claim claim = plugin.getClaimAt(blockVector);
        if (claim != null && !claim.canBuild(home.getOwner(), blockVector)) {
            throw new CommandWarn("The invite is no longer valid in this claim");
        }
        if (claim != null && claim.getTrustType(player.getUniqueId()).isBan()) {
            throw new CommandWarn("You are banned from this claim");
        }
        final String ownerName = home.getOwnerName();
        final String publicName = home.getPublicName();
        Location location = home.createLocation();
        if (location == null) {
            throw new CommandWarn("Could not take you to this home.");
        }
        player.bring(plugin, location, player2 -> {
                home.onVisit(player2.getUniqueId());
                player2.sendMessage(text("Teleported to "
                                         + ownerName + "'s public home \""
                                         + publicName + "\"",
                                         GREEN));
                PluginPlayerEvent.Name.VISIT_PUBLIC_HOME
                    .make(plugin, player2)
                    .detail(Detail.NAME, publicName)
                    .detail(Detail.OWNER, home.getOwner())
                    .detail(Detail.LOCATION, location)
                    .callEvent();
            });
        return true;
    }

    protected void listPublicHomes(Player player) {
        List<SQLHome> publicHomes = plugin.getHomes().getPublicHomes();
        if (publicHomes.isEmpty()) {
            throw new CommandWarn("No public homes to show");
        }
        for (SQLHome home : publicHomes) {
            home.updateScore();
        }
        publicHomes.sort(SQLHome.RANK_COMPARATOR);
        player.sendMessage(Util.frame(publicHomes.size() == 1
                                      ? "One public home"
                                      : publicHomes.size() + " public homes"));
        final int pageCount = (publicHomes.size() - 1) / pageLen + 1;
        List<Component> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            List<Component> lines = new ArrayList<>(pageLen);
            for (int i = 0; i < pageLen; i += 1) {
                int homeIndex = pageIndex * pageLen + i;
                if (homeIndex >= publicHomes.size()) break;
                SQLHome home = publicHomes.get(homeIndex);
                String cmd = "/visit " + home.getPublicName();
                Component tooltip = join(separator(newline()),
                                         text(cmd, BLUE),
                                         text("Visit this home", GRAY));
                lines.add(text().color(WHITE)
                          .append(text(" + ", AQUA))
                          .append(text(home.getPublicName()))
                          .append(text(" by " + home.getOwnerName(), GRAY))
                          .clickEvent(runCommand(cmd))
                          .hoverEvent(showText(tooltip))
                          .build());
            }
            pages.add(join(separator(newline()), lines));
        }
        if (pages.size() == 1) {
            player.sendMessage(pages.get(0));
        } else {
            plugin.sessions.of(player).setPages(pages);
            plugin.sessions.of(player).showStoredPage(player, 0);
        }
        PluginPlayerEvent.Name.VIEW_PUBLIC_HOMES.call(plugin, player);
    }

    protected List<String> completeOwnedHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return List.of();
        List<String> result = new ArrayList<>();
        String lower = arg.toLowerCase();
        for (SQLHome home : plugin.getHomes().findOwnedHomes(context.player.getUniqueId())) {
            if (home.isPrimary()) continue;
            if (lower.isEmpty() || home.getName().toLowerCase().contains(lower)) {
                result.add(home.getName());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    protected List<String> completePublicableHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return List.of();
        List<String> result = new ArrayList<>();
        String lower = arg.toLowerCase();
        for (SQLHome home : plugin.getHomes().findOwnedHomes(context.player.getUniqueId())) {
            if (home.isPrimary() || home.isPublic()) continue;
            if (lower.isEmpty() || home.getName().toLowerCase().contains(lower)) {
                result.add(home.getName());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    protected List<String> completePublicHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return List.of();
        List<String> result = new ArrayList<>();
        String lower = arg.toLowerCase();
        for (SQLHome home : plugin.getHomes().getPublicHomes()) {
            if (lower.isEmpty() || home.getPublicName().toLowerCase().contains(lower)) {
                result.add(home.getPublicName());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    protected List<String> completeUsableHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return List.of();
        UUID uuid = context.player.getUniqueId();
        List<String> result = new ArrayList<>();
        boolean ignore = plugin.doesIgnoreClaims(context.player);
        String lower = arg.toLowerCase();
        for (SQLHome home : plugin.getHomes()) {
            if (home.isOwner(uuid)) {
                if (home.getName() != null && home.getName().toLowerCase().contains(lower)) {
                    result.add(home.getName());
                }
            } else {
                if (ignore || home.isInvited(uuid)) {
                    String name;
                    if (home.getName() == null) {
                        name = home.getOwnerName() + ":";
                    } else {
                        name = home.getOwnerName() + ":" + home.getName();
                    }
                    if (name.toLowerCase().contains(lower)) result.add(name);
                }
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
