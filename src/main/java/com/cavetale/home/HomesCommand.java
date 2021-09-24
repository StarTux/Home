package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.home.struct.BlockVector;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class HomesCommand extends AbstractCommand<HomePlugin> {
    protected final int pageLen = 9;

    protected HomesCommand(final HomePlugin plugin) {
        super(plugin, "homes");
    }

    @Override
    public void onEnable() {
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
            .playerCaller(this::home);
        rootNode.addChild("visit").arguments("[name]")
            .description("Visit a public home")
            .completers(this::completePublicHomes)
            .playerCaller(this::visit);
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

    public boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        final UUID playerId = player.getUniqueId();
        List<Home> playerHomes = plugin.findHomes(playerId);
        if (playerHomes.isEmpty()) {
            throw new CommandWarn("No homes to show");
        }
        if (playerHomes.size() == 1) {
            player.sendMessage(PlayerCommand.frame("You have one home"));
        } else {
            player.sendMessage(PlayerCommand.frame("You have " + playerHomes.size() + " homes"));
        }
        final int pageCount = (playerHomes.size() - 1) / pageLen + 1;
        final List<Component> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            List<Component> lines = new ArrayList<>(pageLen);
            for (int i = 0; i < pageLen; i += 1) {
                int homeIndex = pageIndex * pageLen + i;
                if (homeIndex >= playerHomes.size()) break;
                Home home = playerHomes.get(homeIndex);
                boolean primary = home.getName() == null;
                String cmd = primary ? "/home" : "/home " + home.getName();
                lines.add(Component.text().color(NamedTextColor.WHITE)
                          .append(Component.text(" + ", NamedTextColor.BLUE))
                          .append(primary
                                  ? Component.text("Primary", NamedTextColor.GOLD)
                                  : Component.text(home.getName(), NamedTextColor.WHITE))
                          .clickEvent(ClickEvent.runCommand(cmd))
                          .hoverEvent(HoverEvent.showText(Component.join(Component.newline(),
                                                                         Component.text(cmd, NamedTextColor.GREEN),
                                                                         Component.text("Visit this home", NamedTextColor.GRAY))))
                          .build());
            }
            pages.add(Component.join(Component.newline(), lines));
        }
        if (pages.size() == 1) {
            player.sendMessage(pages.get(0));
        } else {
            plugin.sessions.of(player).setPages(pages);
            plugin.sessions.of(player).showStoredPage(player, 0);
        }
        PluginPlayerEvent.Name.LIST_HOMES.ultimate(plugin, player)
            .detail(Detail.COUNT, playerHomes.size())
            .call();
        return true;
    }

    boolean home(Player player, String[] args) {
        if (args.length > 1) return false;
        UUID playerId = player.getUniqueId();
        if (args.length == 0) {
            // Try to find a set home
            Home home = plugin.findHome(player.getUniqueId(), null);
            if (home != null) {
                BlockVector blockVector = home.createBlockVector();
                Claim claim = plugin.getClaimAt(blockVector);
                if (claim != null && !claim.canBuild(home.getOwner(), blockVector)) {
                    throw new CommandWarn("This home location lacks build permission");
                }
                if (claim != null && claim.getTrustType(player).isBan()) {
                    throw new CommandWarn("You are banned from this claim");
                }
                Location location = home.createLocation();
                if (location == null) {
                    throw new CommandWarn("Primary home could not be found.");
                }
                boolean allowed = PluginPlayerEvent.Name.USE_PRIMARY_HOME.cancellable(plugin, player)
                    .detail(Detail.LOCATION, location)
                    .call();
                if (!allowed) return true;
                plugin.warpTo(player, location, () -> {
                        player.sendMessage(ChatColor.GREEN + "Welcome home :)");
                        player.sendTitle("", ChatColor.GREEN + "Welcome home :)", 10, 20, 10);
                    });
                return true;
            }
            // No home was found, so if the player has no claim in the
            // home world, find a place to build.  We do this here so
            // that an existing bed spawn does not prevent someone
            // from using /home as expected.  Either making a claim or
            // setting a home will have caused this function to exit
            // already.
            if (!plugin.hasAClaim(playerId)) {
                plugin.findPlaceToBuild(player);
                return true;
            }
            // Try the primary claim in the home world.
            List<Claim> playerClaims = plugin
                .findClaimsInWorld(playerId, plugin.getPrimaryHomeWorld());
            // or any claim
            if (playerClaims.isEmpty()) playerClaims = plugin.findClaims(playerId);
            if (!playerClaims.isEmpty()) {
                Claim claim = playerClaims.get(0);
                World bworld = plugin.getServer().getWorld(claim.getWorld());
                Area area = claim.getArea();
                final int x = area.centerX();
                final int z = area.centerY();
                bworld.getChunkAtAsync(x >> 4, z >> 4, (Consumer<Chunk>) c -> {
                        Location location = bworld.getHighestBlockAt(x, z).getLocation().add(0.5, 0.0, 0.5);
                        plugin.warpTo(player, location, () -> {
                                player.sendMessage(ChatColor.GREEN + "Welcome to your claim. :)");
                            });
                        plugin.highlightClaim(claim, player);
                    });
                return true;
            }
            // Give up and default to a random build location, again.
            plugin.findPlaceToBuild(player);
            return true;
        }
        if (args.length == 1) {
            Home home;
            String arg = args[0];
            if (arg.contains(":")) {
                String[] toks = arg.split(":", 2);
                UUID targetId = PlayerCache.uuidForName(toks[0]);
                if (targetId == null) {
                    throw new CommandWarn("Player not found: " + toks[0]);
                }
                home = plugin.findHome(targetId, toks[1].isEmpty() ? null : toks[1]);
                if (home == null) {
                    throw new CommandWarn("Home not found.");
                }
                if (!player.hasMetadata(plugin.META_IGNORE)
                    && !home.isInvited(player.getUniqueId())) {
                    throw new CommandWarn("Home not found.");
                }
            } else {
                home = plugin.findHome(player.getUniqueId(), arg);
            }
            if (home == null) {
                throw new CommandWarn("Home not found: " + arg);
            }
            Location location = home.createLocation();
            if (location == null) {
                throw new CommandWarn("Home \"%s\" could not be found.");
            }
            Claim claim = plugin.getClaimAt(location);
            if (claim != null && !claim.canBuild(home.getOwner(), home.createBlockVector())) {
                throw new CommandWarn("This home location lacks build permission");
            }
            if (home.isOwner(player.getUniqueId())) {
                boolean allowed = PluginPlayerEvent.Name.USE_NAMED_HOME
                    .cancellable(plugin, player)
                    .detail(Detail.NAME, home.getName())
                    .detail(Detail.LOCATION, location)
                    .call();
                if (!allowed) return true;
            } else {
                boolean allowed = PluginPlayerEvent.Name.VISIT_HOME
                    .cancellable(plugin, player)
                    .detail(Detail.OWNER, home.getOwner())
                    .detail(Detail.NAME, home.getName())
                    .detail(Detail.LOCATION, location)
                    .call();
                if (!allowed) return true;
            }
            plugin.warpTo(player, location, () -> {
                    player.sendMessage(ChatColor.GREEN + "Welcome home.");
                    player.sendTitle("", ChatColor.GREEN + "Welcome home.", 10, 20, 10);
                });
            return true;
        }
        return false;
    }

    public boolean set(Player player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        UUID playerId = player.getUniqueId();
        if (!plugin.isHomeWorld(player.getWorld())) {
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
        WorldSettings settings = plugin.getWorldSettings().get(playerWorld);
        for (Home home : plugin.getHomes()) {
            if (home.isOwner(playerId) && home.isInWorld(playerWorld)
                && !home.isNamed(homeName)
                && Math.abs(playerX - (int) home.getX()) < settings.homeMargin
                && Math.abs(playerZ - (int) home.getZ()) < settings.homeMargin) {
                if (home.getName() == null) {
                    throw new CommandWarn("Your primary home is nearby");
                } else {
                    throw new CommandWarn("You have a home named \"" + home.getName() + "\" nearby");
                }
            }
        }
        Home home = plugin.findHome(playerId, homeName);
        if (home == null) {
            home = new Home(playerId, player.getLocation(), homeName);
            plugin.getHomes().add(home);
            home.pack();
            plugin.getDb().insertAsync(home, null);
        } else {
            home.setLocation(player.getLocation());
            home.pack();
            plugin.getDb().updateAsync(home, null);
        }
        if (homeName == null) {
            player.sendMessage(ChatColor.GREEN + "Primary home set");
            PluginPlayerEvent.Name.SET_PRIMARY_HOME.call(plugin, player);
        } else {
            player.sendMessage(ChatColor.GREEN + "Home \"" + homeName + "\" set");
            PluginPlayerEvent.Name.SET_NAMED_HOME.ultimate(plugin, player)
                .detail(Detail.NAME, homeName)
                .call();
        }
        return true;
    }

    public boolean info(Player player, String[] args) {
        if (args.length != 0 && args.length != 1) return false;
        final Home home;
        if (args.length == 0) {
            home = plugin.findHome(player.getUniqueId(), null);
        } else {
            home = plugin.findHome(player.getUniqueId(), args[0]);
        }
        if (home == null) throw new CommandWarn("Home not found.");
        if (home.getName() == null) {
            ComponentBuilder cb = new ComponentBuilder("");
            cb = PlayerCommand.frame(cb, "Primary Home Info");
            player.spigot().sendMessage(cb.create());
        } else {
            ComponentBuilder cb = new ComponentBuilder("");
            cb = PlayerCommand.frame(cb, home.getName() + " Info");
            player.spigot().sendMessage(cb.create());
        }
        StringBuilder sb = new StringBuilder();
        for (UUID inviteId : home.getInvites()) {
            sb.append(" ").append(PlayerCache.nameForUuid(inviteId));
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
            cb.append(" ").append(PlayerCache.nameForUuid(invitee));
            cb.color(ChatColor.WHITE);
        }
        player.spigot().sendMessage(cb.create());
        player.sendMessage(ChatColor.GRAY + " Public: " + ChatColor.WHITE
                           + (home.getPublicName() != null ? "yes" : "no"));
        return true;
    }

    public boolean invites(Player player, String[] args) {
        if (args.length != 0) return false;
        ComponentBuilder cb = new ComponentBuilder("Your invites:");
        cb.color(ChatColor.GRAY);
        UUID playerId = player.getUniqueId();
        for (Home home : plugin.getHomes()) {
            if (home.isInvited(playerId)) {
                String homename = home.getName() == null
                    ? home.getOwnerName() + ":"
                    : home.getOwnerName() + ":" + home.getName();
                cb.append(" ");
                cb.append(homename).color(ChatColor.GREEN);
                cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/home " + homename));
                BaseComponent[] tooltip = TextComponent
                    .fromLegacyText(ChatColor.GREEN + "home " + homename
                                    + "\n" + ChatColor.WHITE + ChatColor.ITALIC
                                    + "Use this home invite.");
                cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, tooltip));
            }
        }
        player.spigot().sendMessage(cb.create());
        return true;
    }

    public boolean invite(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        final UUID playerId = player.getUniqueId();
        String targetName = args[0];
        UUID targetId = PlayerCache.uuidForName(targetName);
        if (targetId == null) {
            throw new CommandWarn("Player not found: " + targetName);
        }
        String homeName = args.length >= 2 ? args[1] : null;
        Home home = this.plugin.findHome(playerId, homeName);
        if (home == null) {
            if (homeName == null) {
                throw new CommandWarn("Your primary home is not set");
            } else {
                throw new CommandWarn("You have no home named " + homeName);
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
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append(player.getName() + " invited you to their primary home: ")
                    .color(ChatColor.WHITE);
                cb.append("[Visit]").color(ChatColor.GREEN);
                cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, cmd));
                BaseComponent[] txt = TextComponent
                    .fromLegacyText(ChatColor.GREEN + cmd + "\n"
                                    + ChatColor.WHITE + ChatColor.ITALIC
                                    + "Visit this home");
                cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
                target.spigot().sendMessage(cb.create());
            } else {
                String cmd = "/home " + player.getName() + ":" + home.getName();
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append(player.getName() + " invited you to their home: ")
                    .color(ChatColor.WHITE);
                cb.append("[" + home.getName() + "]").color(ChatColor.GREEN);
                cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, cmd));
                BaseComponent[] txt = TextComponent
                    .fromLegacyText(ChatColor.GREEN + cmd + "\n"
                                    + ChatColor.WHITE + ChatColor.ITALIC
                                    + "Visit this home");
                cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
                target.spigot().sendMessage(cb.create());
            }
        }
        PluginPlayerEvent.Name.INVITE_HOME.ultimate(plugin, player)
            .detail(Detail.TARGET, targetId)
            .detail(Detail.NAME, home.getName())
            .call();
        return true;
    }

    public boolean uninvite(Player player, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        final String targetName = args[0];
        UUID target = PlayerCache.uuidForName(targetName);
        if (target == null) throw new CommandWarn("Player not found: " + targetName + "!");
        Home home;
        if (args.length >= 2) {
            String arg = args[1];
            home = plugin.findHome(player.getUniqueId(), arg);
            if (home == null) throw new CommandWarn("Home not found: " + arg + "!");
        } else {
            home = plugin.findHome(player.getUniqueId(), null);
            if (home == null) throw new CommandWarn("Default home not set.");
        }
        if (!home.invites.contains(target)) throw new CommandWarn("Player not invited.");
        plugin.getDb().find(HomeInvite.class)
            .eq("home_id", home.getId())
            .eq("invitee", target).deleteAsync(null);
        home.getInvites().remove(target);
        player.sendMessage(ChatColor.GREEN + targetName + " uninvited.");
        PluginPlayerEvent.Name.UNINVITE_HOME.ultimate(plugin, player)
            .detail(Detail.TARGET, target)
            .detail(Detail.NAME, home.getName())
            .call();
        return true;
    }

    public boolean makePublic(Player player, String[] args) {
        if (args.length != 1 && args.length != 2) return false;
        String homeName = args[0];
        Home home = plugin.findHome(player.getUniqueId(), homeName);
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
        if (plugin.findPublicHome(publicName) != null) {
            throw new CommandWarn("A public home by that name already exists."
                            + " Please supply a different alias.");
        }
        home.setPublicName(publicName);
        plugin.getDb().saveAsync(home, null, "public_name");
        String cmd = "/visit " + publicName;
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("Home made public. Players may visit via ").color(ChatColor.WHITE);
        cb.append(cmd).color(ChatColor.GREEN);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, cmd));
        BaseComponent[] tooltip = TextComponent
            .fromLegacyText(ChatColor.GREEN + cmd + "\nCan also be found under /visit.");
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, tooltip));
        player.spigot().sendMessage(cb.create());
        return true;
    }

    public boolean delete(Player player, String[] args) {
        if (args.length != 0 && args.length != 1) return false;
        String homeName = args.length >= 1
            ? args[0]
            : null;
        Home home = plugin.findHome(player.getUniqueId(), homeName);
        if (home == null) {
            if (homeName == null) {
                throw new CommandWarn("Your primary home is not set");
            } else {
                throw new CommandWarn("You do not have a home named \"" + homeName + "\"");
            }
        }
        plugin.getDb().find(HomeInvite.class).eq("home_id", home.getId()).delete();
        plugin.getDb().delete(home);
        plugin.getHomes().remove(home);
        if (homeName == null) {
            player.sendMessage(ChatColor.YELLOW + "Primary home unset. The "
                               + ChatColor.ITALIC + "/home" + ChatColor.YELLOW
                               + " command will take you to your bed spawn"
                               + " or primary claim.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Home \"" + homeName + "\" deleted");
        }
        return true;
    }

    public boolean page(Player player, String[] args) {
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

    public boolean visit(Player player, String[] args) {
        if (args.length == 0) {
            listPublicHomes(player);
            return true;
        }
        Home home = plugin.findPublicHome(args[0]);
        if (home == null) {
            throw new CommandWarn("Public home not found: " + args[0]);
        }
        BlockVector blockVector = home.createBlockVector();
        Claim claim = plugin.getClaimAt(blockVector);
        if (claim != null && !claim.canBuild(home.getOwner(), blockVector)) {
            throw new CommandWarn("The invite is no longer valid in this claim");
        }
        if (claim != null && claim.getTrustType(player).isBan()) {
            throw new CommandWarn("You are banned from this claim");
        }
        final String ownerName = home.getOwnerName();
        final String publicName = home.getPublicName();
        Location location = home.createLocation();
        if (location == null) {
            throw new CommandWarn("Could not take you to this home.");
        }
        boolean allowed = PluginPlayerEvent.Name.VISIT_PUBLIC_HOME
            .cancellable(plugin, player)
            .detail(Detail.NAME, publicName)
            .detail(Detail.OWNER, home.getOwner())
            .detail(Detail.LOCATION, location)
            .call();
        if (!allowed) return true;
        plugin.warpTo(player, location, () -> {
                player.sendMessage(ChatColor.GREEN + "Teleported to "
                                   + ownerName + "'s public home \""
                                   + publicName + "\"");
                home.onVisit(player.getUniqueId());
            });
        return true;
    }

    public void listPublicHomes(Player player) {
        List<Home> publicHomes = plugin.getHomes().stream()
            .filter(h -> h.getPublicName() != null)
            .peek(Home::updateScore)
            .sorted(Home::rank)
            .collect(Collectors.toList());
        if (publicHomes.isEmpty()) {
            throw new CommandWarn("No public homes to show");
        }
        ComponentBuilder cb;
        player.sendMessage(PlayerCommand.frame(publicHomes.size() == 1
                                               ? "One public home"
                                               : publicHomes.size() + " public homes"));
        final int pageCount = (publicHomes.size() - 1) / pageLen + 1;
        List<Component> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            List<Component> lines = new ArrayList<>(pageLen);
            for (int i = 0; i < pageLen; i += 1) {
                int homeIndex = pageIndex * pageLen + i;
                if (homeIndex >= publicHomes.size()) break;
                Home home = publicHomes.get(homeIndex);
                String cmd = "/visit " + home.getPublicName();
                Component tooltip = Component.join(Component.newline(),
                                                   Component.text(cmd, NamedTextColor.BLUE),
                                                   Component.text("Visit this home", NamedTextColor.GRAY));
                lines.add(Component.text().color(NamedTextColor.WHITE)
                          .append(Component.text(" + ", NamedTextColor.AQUA))
                          .append(Component.text(home.getPublicName()))
                          .append(Component.text(" by " + home.getOwnerName(), NamedTextColor.GRAY))
                          .clickEvent(ClickEvent.runCommand(cmd))
                          .hoverEvent(HoverEvent.showText(tooltip))
                          .build());
            }
            pages.add(Component.join(Component.newline(), lines));
        }
        if (pages.size() == 1) {
            player.sendMessage(pages.get(0));
        } else {
            plugin.sessions.of(player).setPages(pages);
            plugin.sessions.of(player).showStoredPage(player, 0);
        }
        PluginPlayerEvent.Name.VIEW_PUBLIC_HOMES.call(plugin, player);
    }

    public List<String> completeOwnedHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return null;
        return plugin.getHomes().stream()
            .filter(h -> h.isOwner(context.player.getUniqueId())
                    && h.getName() != null)
            .map(Home::getName)
            .filter(a -> a.contains(arg))
            .collect(Collectors.toList());
    }

    public List<String> completePublicableHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return null;
        return plugin.getHomes().stream()
            .filter(h -> h.isOwner(context.player.getUniqueId())
                    && h.getName() != null
                    && h.getPublicName() == null)
            .map(Home::getName)
            .filter(a -> a.contains(arg))
            .collect(Collectors.toList());
    }

    public List<String> completePublicHomes(CommandContext context, CommandNode node, String arg) {
        return plugin.getHomes().stream()
            .filter(h -> h.getPublicName() != null
                    && h.getPublicName().startsWith(arg))
            .map(Home::getPublicName)
            .collect(Collectors.toList());
    }

    public List<String> completeUsableHomes(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return null;
        UUID uuid = context.player.getUniqueId();
        List<String> result = new ArrayList<>();
        boolean ignore = plugin.doesIgnoreClaims(context.player);
        for (Home home : plugin.getHomes()) {
            if (home.isOwner(uuid)) {
                if (home.getName() != null && home.getName().startsWith(arg)) {
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
                    if (name.startsWith(arg)) result.add(name);
                }
            }
        }
        return result;
    }
}
