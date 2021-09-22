package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.home.struct.BlockVector;
import com.cavetale.money.Money;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class ClaimCommand extends AbstractCommand<HomePlugin> {
    private final int pageLen = 9;

    protected ClaimCommand(final HomePlugin plugin) {
        super(plugin, "claim");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("info").denyTabCompletion()
            .description("Show claim info")
            .playerCaller(this::info);
        rootNode.addChild("new").denyTabCompletion()
            .alias("create")
            .description("Make a new claim")
            .playerCaller(this::newClaim);
        // Query
        rootNode.addChild("list").denyTabCompletion()
            .description("List your claims")
            .playerCaller(this::list);
        rootNode.addChild("listinvites").denyTabCompletion()
            .description("List claims you're invited to")
            .playerCaller(this::listInvites);
        rootNode.addChild("port").denyTabCompletion()
            .description("Port to your claim")
            .playerCaller(this::port);
        rootNode.addChild("buy").arguments("<amount>")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .alias("buyclaimblocks")
            .description("Buy more claim blocks")
            .playerCaller(this::buy);
        // Click
        rootNode.addChild("confirm").denyTabCompletion()
            .description("Confirm a previous command")
            .hidden(true)
            .playerCaller(this::confirm);
        rootNode.addChild("cancel").denyTabCompletion()
            .description("Cancel a previous command")
            .hidden(true)
            .playerCaller(this::cancel);
        // Trust
        rootNode.addChild("trust").arguments("<player>")
            .description("Trust someone to build")
            .completers(CommandArgCompleter.NULL)
            .playerCaller((p, a) -> claimTrust(p, TrustType.BUILD, a));
        rootNode.addChild("interact-trust").arguments("<player>")
            .description("Trust someone to interact with blocks")
            .completers(CommandArgCompleter.NULL)
            .playerCaller((p, a) -> claimTrust(p, TrustType.INTERACT, a));
        rootNode.addChild("container-trust").arguments("<player>")
            .description("Trust someone to open containers")
            .completers(CommandArgCompleter.NULL)
            .playerCaller((p, a) -> claimTrust(p, TrustType.CONTAINER, a));
        rootNode.addChild("co-owner-trust").arguments("<player>")
            .description("Make someone a co-owner")
            .completers(CommandArgCompleter.NULL)
            .playerCaller((p, a) -> claimTrust(p, TrustType.CO_OWNER, a));
        rootNode.addChild("owner-trust").arguments("<player>")
            .description("Make someone an owner")
            .completers(CommandArgCompleter.NULL)
            .playerCaller((p, a) -> claimTrust(p, TrustType.OWNER, a));
        rootNode.addChild("untrust").arguments("<player>")
            .description("Revoke trust")
            .completers(this::completeTrustedPlayerNames)
            .playerCaller(this::untrust);
        // Settings
        rootNode.addChild("rename").denyTabCompletion()
            .description("Name this claim")
            .playerCaller(this::rename);
        rootNode.addChild("set").denyTabCompletion()
            .description("Access claim settings")
            .playerCaller(this::set);
        rootNode.addChild("grow").denyTabCompletion()
            .description("Grow claim to your location")
            .playerCaller(this::grow);
        rootNode.addChild("shrink").denyTabCompletion()
            .description("Shrink claim to your location")
            .playerCaller(this::shrink);
        rootNode.addChild("abandon").denyTabCompletion()
            .description("Delete this claim forever")
            .playerCaller(this::abandon);
        // Moderation
        rootNode.addChild("ban").arguments("<player>")
            .description("Ban someone from this claim")
            .completers(CommandArgCompleter.NULL)
            .playerCaller(this::ban);
        rootNode.addChild("unban").arguments("<player>")
            .description("List a ban")
            .completers(this::completeBannedPlayerNames)
            .playerCaller(this::unban);
        rootNode.addChild("kick").arguments("<player>")
            .description("Kick someone from your claim")
            .completers(CommandArgCompleter.NULL)
            .playerCaller(this::kick);
    }

    @Value
    private static class BuyClaimBlocks {
        int amount;
        double price;
        int claimId;
        String token;
    }

    @Value
    private static class NewClaimMeta {
        String world;
        int x;
        int z;
        Area area;
        double price;
        String token;
    }

    private boolean info(Player player, String[] args) {
        if (args.length > 1) return false;
        Claim claim;
        if (args.length == 0) {
            claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                throw new CommandWarn("Stand in the claim you want info on");
            }
        } else {
            int claimId;
            try {
                claimId = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                return true;
            }
            claim = plugin.findClaimWithId(claimId);
            if (claim == null) return true;
        }
        player.sendMessage("");
        player.sendMessage(PlayerCommand.frame("Claim Info"));
        player.sendMessage(makeClaimInfo(player, claim));
        if (claim.getTrustType(player).canBuild()) {
            player.sendMessage(Component.text().content("Teleport ").color(NamedTextColor.GRAY)
                               .append(Component.text("[Port]", NamedTextColor.BLUE))
                               .hoverEvent(HoverEvent.showText(Component.text("Port to this claim", NamedTextColor.BLUE)))
                               .clickEvent(ClickEvent.runCommand("/claim port " + claim.getId())));
        }
        player.sendMessage("");
        plugin.highlightClaim(claim, player);
        for (Subclaim subclaim : claim.getSubclaims(player.getWorld())) {
            plugin.highlightSubclaim(subclaim, player);
        }
        PluginPlayerEvent.Name.VIEW_CLAIM_INFO.call(plugin, player);
        return true;
    }

    private boolean newClaim(Player player, String[] args) {
        if (args.length != 0) return false;
        boolean isHomeWorld = false;
        boolean isHomeEndWorld = false;
        World playerWorld = player.getWorld();
        String playerWorldName = playerWorld.getName();
        if (!plugin.getHomeWorlds().contains(playerWorldName)) {
            throw new CommandWarn("You cannot make claims in this world");
        }
        if (plugin.getMirrorWorlds().containsKey(playerWorldName)) {
            playerWorldName = plugin.getMirrorWorlds().get(playerWorldName);
        }
        // Check for other claims
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockZ();
        UUID playerId = player.getUniqueId();
        // Check for claim collisions
        WorldSettings settings = plugin.getWorldSettings().get(playerWorldName);
        int claimSize;
        double claimCost;
        List<Claim> playerClaims = plugin.findClaimsInWorld(playerId, playerWorldName);
        if (playerClaims.isEmpty()) {
            claimSize = settings.initialClaimSize;
            claimCost = settings.initialClaimCost;
        } else {
            claimSize = settings.secondaryClaimSize;
            claimCost = settings.secondaryClaimCost;
        }
        if (Money.get(playerId) < claimCost) {
            throw new CommandWarn("You cannot afford " + Money.format(claimCost) + "!");
        }
        final int rad = claimSize / 2;
        final int tol = (rad * 2 == claimSize) ? 1 : 0;
        Area area = new Area(x - rad + tol, y - rad + tol, x + rad, y + rad);
        for (Claim claimInWorld : plugin.findClaimsInWorld(playerWorldName)) {
            // This whole check is repeated in the confirm command
            if (claimInWorld.getArea().overlaps(area)) {
                throw new CommandWarn("Your claim would overlap an existing claim.");
            }
        }
        NewClaimMeta ncmeta = new NewClaimMeta(playerWorldName, x, y, area, claimCost, ""
                                               + ThreadLocalRandom.current().nextInt(9999));
        plugin.setMetadata(player, plugin.META_NEWCLAIM, ncmeta);
        player.sendMessage("");
        player.spigot().sendMessage(PlayerCommand.frame(new ComponentBuilder(""), "New Claim").create());
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.AQUA + "You are about to create a claim of "
                  + ChatColor.WHITE + area.width() + "x" + area.height() + " blocks"
                  + ChatColor.AQUA + " around your current location.");
        if (claimCost >= 0.01) {
            sb.append(" " + ChatColor.AQUA + "This will cost you " + ChatColor.WHITE
                      + Money.format(claimCost) + ChatColor.AQUA + ".");
        }
        sb.append(" " + ChatColor.AQUA + "Your new claim will have "
                  + ChatColor.WHITE + area.size() + ChatColor.AQUA + " claim blocks.");
        if (settings.claimBlockCost >= 0.01) {
            sb.append(" " + ChatColor.AQUA + "You can buy additional claim blocks for "
                      + ChatColor.WHITE + Money.format(settings.claimBlockCost)
                      + ChatColor.AQUA + " per block.");
        }
        sb.append(ChatColor.AQUA + " You can abandon the claim after a cooldown of "
                  + ChatColor.WHITE + settings.claimAbandonCooldown + " minutes"
                  + ChatColor.AQUA + ". Claim blocks will " + ChatColor.WHITE + "not"
                  + ChatColor.AQUA + " be refunded.");
        player.sendMessage(sb.toString());
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("Proceed?  ").color(ChatColor.WHITE);
        cb.append("[Confirm]").color(ChatColor.GREEN);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim confirm " + ncmeta.token));
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                         .fromLegacyText(ChatColor.GREEN + "Confirm this purchase")));
        cb.append("  ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                         .fromLegacyText(ChatColor.RED + "Cancel this purchase")));
        player.spigot().sendMessage(cb.create());
        player.sendMessage("");
        return true;
    }

    private boolean port(final Player player, String[] args) {
        if (args.length > 1) return false;
        final Claim claim;
        if (args.length == 1) {
            int claimId;
            try {
                claimId = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                return true;
            }
            claim = plugin.findClaimWithId(claimId);
            if (claim == null) return true;
            if (!claim.isOwner(player) && claim.isHidden()) return true;
            if (!claim.getTrustType(player).canBuild()) return true;
        } else {
            claim = plugin.findPrimaryClaim(player.getUniqueId());
            if (claim == null) {
                throw new CommandWarn("You don't have a claim yet.");
            }
        }
        final World world = plugin.getServer().getWorld(claim.getWorld());
        if (world == null) return true;
        final int x = claim.centerX;
        final int z = claim.centerY;
        world.getChunkAtAsync(x >> 4, z >> 4, chunk -> {
                if (!player.isValid()) return;
                final Location target;
                if (world.getEnvironment() == World.Environment.NETHER) {
                    Block block = world.getBlockAt(x, 1, z);
                    while (!block.isEmpty() || !block.getRelative(0, 1, 0).isEmpty() || !block.getRelative(0, -1, 0).getType().isSolid()) {
                        block = block.getRelative(0, 1, 0);
                    }
                    target = block.getLocation().add(0.5, 0.0, 0.5);
                } else {
                    target = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1.0, 0.5);
                }
                Location ploc = player.getLocation();
                target.setYaw(ploc.getYaw());
                target.setPitch(ploc.getPitch());
                player.teleport(target, TeleportCause.COMMAND);
                player.sendMessage(ChatColor.BLUE + "Teleporting to claim.");
            });
        return true;
    }

    private boolean buy(Player player, String[] args) {
        if (args.length != 1) return false;
        final UUID playerId = player.getUniqueId();
        int buyClaimBlocks;
        try {
            buyClaimBlocks = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            buyClaimBlocks = -1;
        }
        if (buyClaimBlocks <= 0) {
            throw new CommandWarn("Invalid claim blocks amount: " + args[0]);
        }
        Claim claim;
        Session.ClaimGrowSnippet snippet = plugin.sessions.of(player).getClaimGrowSnippet();
        if (snippet != null && snippet.isNear(player.getLocation())) {
            claim = plugin.findClaimWithId(snippet.claimId);
        } else {
            claim = plugin.findNearestOwnedClaim(player, 512);
        }
        if (claim == null) {
            throw new CommandWarn("There is no claim here!");
        }
        if (!claim.isOwner(player)) {
            throw new CommandWarn("You do not own this claim!");
        }
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        double price = (double) buyClaimBlocks * settings.claimBlockCost;
        String priceFormat = Money.format(price);
        if (Money.get(playerId) < price) {
            throw new CommandWarn("You do not have " + priceFormat + " to buy "
                                  + buyClaimBlocks + " claim blocks");
        }
        BuyClaimBlocks meta = new BuyClaimBlocks(buyClaimBlocks, price, claim.getId(), ""
                                                 + ThreadLocalRandom.current().nextInt(9999));
        plugin.setMetadata(player, plugin.META_BUY, meta);
        player.sendMessage(ChatColor.WHITE + "Buying " + ChatColor.GREEN + buyClaimBlocks
                           + ChatColor.WHITE + " for " + ChatColor.GREEN + priceFormat
                           + ChatColor.WHITE + ".");
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("Confirm this purchase: ").color(ChatColor.GRAY);
        cb.append("[Confirm]").color(ChatColor.GREEN);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim confirm " + meta.token));
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                         .fromLegacyText(ChatColor.GREEN + "Confirm\nBuy "
                                                                         + buyClaimBlocks + " for " + priceFormat + ".")));
        cb.append("  ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                         .fromLegacyText(ChatColor.RED + "Cancel purchase.")));
        player.spigot().sendMessage(cb.create());
        return true;
    }

    private boolean confirm(Player player, String[] args) {
        if (args.length != 1) return true;
        final UUID playerId = player.getUniqueId();
        // BuyClaimBlocks confirm
        BuyClaimBlocks meta = plugin.getMetadata(player, plugin.META_BUY, BuyClaimBlocks.class)
            .orElse(null);
        if (meta != null) {
            plugin.removeMetadata(player, plugin.META_BUY);
            Claim claim = plugin.getClaimById(meta.claimId);
            if (claim == null) return true;
            if (!args[0].equals(meta.token)) {
                throw new CommandWarn("Purchase expired");
            }
            if (!Money.take(playerId, meta.price, plugin, "Buy " + meta.amount + " claim blocks")) {
                throw new CommandWarn("You cannot afford " + Money.format(meta.price));
            }
            claim.setBlocks(claim.getBlocks() + meta.amount);
            Session.ClaimGrowSnippet snippet = plugin.sessions.of(player).getClaimGrowSnippet();
            plugin.sessions.of(player).setClaimGrowSnippet(null);
            Location location = player.getLocation();
            if (snippet != null && snippet.isNear(location) && claim.growTo(location.getBlockX(), location.getBlockZ()).isSuccessful()) {
                player.sendMessage(ChatColor.WHITE + "Added " + meta.amount
                                   + " and grew to your location!");
                plugin.highlightClaim(claim, player);
            } else if (claim.getBoolSetting(Claim.Setting.AUTOGROW)) {
                player.sendMessage(ChatColor.WHITE + "Added " + meta.amount
                                   + " blocks to this claim. It will grow automatically.");
            } else {
                player.sendMessage(ChatColor.WHITE + "Added " + meta.amount
                                   + " blocks to this claim."
                                   + " Grow it manually or enable \"autogrow\" in the settings.");
            }
            claim.saveToDatabase();
            PluginPlayerEvent.Name.BUY_CLAIM_BLOCKS.ultimate(plugin, player)
                .detail(Detail.COUNT, meta.amount)
                .detail(Detail.MONEY, meta.price)
                .call();
            return true;
        }
        // AbandonClaim confirm
        int claimId = plugin.getMetadata(player, plugin.META_ABANDON, Integer.class).orElse(-1);
        if (claimId >= 0) {
            plugin.removeMetadata(player, plugin.META_ABANDON);
            Claim claim = plugin.findClaimWithId(claimId);
            if (claim == null || !claim.isOwner(player) || !args[0].equals("" + claimId)) {
                throw new CommandWarn("Claim removal expired");
            }
            plugin.deleteClaim(claim);
            player.sendMessage(ChatColor.YELLOW + "Claim removed");
            return true;
        }
        // NewClaim confirm
        NewClaimMeta ncmeta = plugin.getMetadata(player, plugin.META_NEWCLAIM, NewClaimMeta.class)
            .orElse(null);
        if (ncmeta != null) {
            plugin.removeMetadata(player, plugin.META_NEWCLAIM);
            if (!args[0].equals(ncmeta.token)) return true;
            if (!plugin.getHomeWorlds().contains(ncmeta.world)) return true;
            WorldSettings settings = plugin.getWorldSettings().get(ncmeta.world);
            for (Claim claimInWorld : plugin.findClaimsInWorld(ncmeta.world)) {
                // This whole check is a repeat from the new claim command.
                if (claimInWorld.getArea().overlaps(ncmeta.area)) {
                    throw new CommandWarn("Your claim would overlap an existing claim.");
                }
            }
            if (ncmeta.price >= 0.01 && !Money.take(playerId, ncmeta.price, plugin, "Make new claim in " + plugin.worldDisplayName(ncmeta.world))) {
                throw new CommandWarn("You cannot afford " + Money.format(ncmeta.price) + "!");
            }
            Claim claim = new Claim(plugin, playerId, ncmeta.world, ncmeta.area);
            claim.saveToDatabase();
            plugin.getClaims().add(claim);
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append("Claim created!  ").color(ChatColor.WHITE);
            cb.append("[View]").color(ChatColor.GREEN);
            cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim"));
            cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                             .fromLegacyText(ChatColor.GREEN + "/claim\n"
                                                                             + ChatColor.WHITE + ChatColor.ITALIC
                                                                             + "The command to access all your claims.")));
            player.spigot().sendMessage(cb.create());
            plugin.highlightClaim(claim, player);
            PluginPlayerEvent.Name.CREATE_CLAIM.call(plugin, player);
            return true;
        }
        return true;
    }

    private boolean cancel(Player player, String[] args) {
        if (args.length != 0) return false;
        if (player.hasMetadata(plugin.META_BUY)
            || player.hasMetadata(plugin.META_ABANDON)
            || player.hasMetadata(plugin.META_NEWCLAIM)) {
            plugin.removeMetadata(player, plugin.META_BUY);
            plugin.removeMetadata(player, plugin.META_ABANDON);
            plugin.removeMetadata(player, plugin.META_NEWCLAIM);
            player.sendMessage(ChatColor.GREEN + "Cancelled");
        }
        return true;
    }

    private boolean claimTrust(Player player, TrustType trustType, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        if (claim == null) {
            throw new CommandWarn("There is no claim here");
        }
        String targetName = args[0];
        PlayerCache target = PlayerCache.forName(targetName);
        if (target == null) {
            throw new CommandWarn("Player not found: " + targetName);
        }
        if (target.uuid.equals((UUID) claim.getOwner())) {
            throw new CommandWarn(target.name + " owns this claim!");
        }
        if (!claim.isOwner(player) && trustType.gte(claim.getTrustType(player))) {
            throw new CommandWarn("You cannot give " + trustType.displayName + " trust in this claim!");
        }
        ClaimTrust claimTrust = claim.getTrusted().get(target.uuid);
        if (claimTrust != null) {
            if (claimTrust.parseTrustType().gte(trustType)) {
                throw new CommandWarn(target.name + " already has " + claimTrust.parseTrustType().displayName
                                      + " trust in this claim");
            } else if (claimTrust.parseTrustType().isBan()) {
                throw new CommandWarn(target.name + " is banned from this claim!");
            }
            claimTrust.setTrustType(trustType);
            plugin.getDb().updateAsync(claimTrust, null);
        } else {
            claimTrust = new ClaimTrust(claim, trustType, target.uuid);
            plugin.getDb().insertAsync(claimTrust, null);
            claim.getTrusted().put(target.uuid, claimTrust);
        }
        player.sendMessage(Component.text(target.name + " now has " + trustType.displayName
                                          + " trust in this claim.", NamedTextColor.GREEN));
        PluginPlayerEvent.Name.CLAIM_TRUST.ultimate(plugin, player)
            .detail(Detail.TARGET, target.uuid)
            .detail(Detail.NAME, trustType.key)
            .call();
        return true;
    }

    private boolean untrust(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache playerCache = requirePlayerCache(args[0]);
        ClaimTrust claimTrust = claim.getTrusted().get(playerCache.uuid);
        if (claimTrust == null || !claimTrust.parseTrustType().isTrust()) {
            throw new CommandWarn(playerCache.name + " is not trusted here!");
        }
        if (claimTrust.parseTrustType().gt(claim.getTrustType(player))) {
            throw new CommandWarn(playerCache.name + " outranks you in this claim!");
        }
        claim.getTrusted().remove(playerCache.uuid);
        plugin.db.delete(claimTrust);
        player.sendMessage(Component.text("Removed " + claimTrust.parseTrustType().displayName
                                          + " trust for " + playerCache.name, NamedTextColor.GREEN));
        PluginPlayerEvent.Name.CLAIM_UNTRUST.ultimate(plugin, player)
            .detail(Detail.TARGET, playerCache.uuid)
            .call();
        return true;
    }

    private boolean rename(Player player, String[] args) {
        if (args.length == 0) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        String name = String.join(" ", args);
        if (name.length() > 24) throw new CommandWarn("Name too long!");
        claim.setName(name);
        claim.saveToDatabase();
        player.sendMessage(Component.text("Claim renamed to " + name, NamedTextColor.YELLOW));
        return true;
    }

    private boolean set(Player player, String[] args) {
        if (args.length != 0 && args.length != 2) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new CommandWarn("Stand in the claim you wish to edit");
        }
        if (!claim.isOwner(player)) {
            throw new CommandWarn("Only the claim owner can do this");
        }
        if (args.length == 0) {
            showClaimSettings(claim, player);
            PluginPlayerEvent.Name.VIEW_CLAIM_SETTINGS.call(plugin, player);
            return true;
        }
        Claim.Setting setting;
        try {
            setting = Claim.Setting.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown claim setting: " + args[0]);
        }
        if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) {
            throw new CommandWarn("Unknown claim setting: " + args[0]);
        }
        final Boolean value;
        switch (args[1]) {
        case "on": case "true": case "enabled": value = true; break;
        case "off": case "false": case "disabled": value = false; break;
        default:
            throw new CommandWarn("Unknown settings value: " + args[1]);
        }
        if (!value.equals(claim.getSetting(setting))) {
            if (value.equals(setting.defaultValue)) {
                claim.getSettings().remove(setting);
            } else {
                claim.getSettings().put(setting, value);
            }
        }
        claim.saveToDatabase();
        showClaimSettings(claim, player);
        PluginPlayerEvent.Name.CHANGE_CLAIM_SETTING.ultimate(plugin, player)
            .detail(Detail.NAME, setting.key)
            .detail(Detail.TOGGLE, value)
            .call();
        return true;
    }

    private void showClaimSettings(Claim claim, Player player) {
        player.sendMessage("");
        BaseComponent[] txt;
        ComponentBuilder cb = new ComponentBuilder("");
        PlayerCommand.frame(cb, "Claim Settings");
        player.spigot().sendMessage(cb.create());
        for (Claim.Setting setting : Claim.Setting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            cb = new ComponentBuilder(" ");
            Object value = claim.getSetting(setting);
            String key = setting.name().toLowerCase();
            if (value == Boolean.TRUE) {
                cb.append("[ON]").color(ChatColor.BLUE);
                cb.append("  ", ComponentBuilder.FormatRetention.NONE);
                cb.append("[OFF]").color(ChatColor.GRAY);
                cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                                                 "/claim set " + key + " off"));
                txt = net.md_5.bungee.api.chat.TextComponent
                    .fromLegacyText(ChatColor.RED + "Disable " + setting.displayName);
                cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
            } else if (value == Boolean.FALSE) {
                cb.append("[ON]").color(ChatColor.GRAY);
                cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                                                 "/claim set " + key + " on"));
                txt = net.md_5.bungee.api.chat.TextComponent
                    .fromLegacyText(ChatColor.GREEN + "Enable " + setting.displayName);
                cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
                cb.append("  ", ComponentBuilder.FormatRetention.NONE);
                cb.append("[OFF]").color(ChatColor.RED);
            }
            cb.append(" " + setting.displayName).color(setting.isAdminOnly()
                                                       ? ChatColor.RED
                                                       : ChatColor.WHITE);
            player.spigot().sendMessage(cb.create());
        }
        player.sendMessage("");
    }

    private boolean grow(Player player, String[] args) {
        if (args.length != 0) return false;
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        Claim claim = plugin.findNearestOwnedClaim(player, 512);
        if (claim == null) {
            throw new CommandWarn("You don't have a claim nearby");
        }
        if (claim.getArea().contains(x, z)) {
            plugin.highlightClaim(claim, player);
            throw new CommandWarn("Stand where you want the claim to grow to");
        }
        Area area = claim.getArea();
        int ax = Math.min(area.ax, x);
        int ay = Math.min(area.ay, z);
        int bx = Math.max(area.bx, x);
        int by = Math.max(area.by, z);
        Area newArea = new Area(ax, ay, bx, by);
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        if (claim.getBlocks() < newArea.size()) {
            int needed = newArea.size() - claim.getBlocks();
            String formatMoney = Money.format((double) needed * settings.claimBlockCost);
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(needed + " more claim blocks required. ").color(ChatColor.RED);
            cb.append("[Buy More]").color(ChatColor.GRAY);
            cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim buy " + needed));
            cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, net.md_5.bungee.api.chat.TextComponent
                                                             .fromLegacyText(ChatColor.GRAY + "/claim buy "
                                                                             + ChatColor.ITALIC + needed
                                                                             + "\n" + ChatColor.WHITE + ChatColor.ITALIC
                                                                             + "Buy more " + needed + " claim blocks for "
                                                                             + formatMoney + ".")));
            player.spigot().sendMessage(cb.create());
            plugin.sessions.of(player).setClaimGrowSnippet(new Session.ClaimGrowSnippet(player.getWorld().getName(), x, z, claim.getId()));
            return true;
        }
        for (Claim other : plugin.getClaims()) {
            if (other != claim && other.isInWorld(claim.getWorld())
                && other.getArea().overlaps(newArea)) {
                throw new CommandWarn("Your claim would connect with another claim");
            }
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        player.sendMessage(ChatColor.BLUE + "Grew your claim to where you are standing");
        plugin.highlightClaim(claim, player);
        return true;
    }

    private boolean shrink(Player player, String[] args) {
        if (args.length != 0) return false;
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        Claim claim = plugin.getClaimAt(playerLocation);
        if (claim == null) {
            throw new CommandWarn("Stand in the claim you wish to shrink");
        }
        if (!claim.isOwner(player)) {
            throw new CommandWarn("You can only shrink your own claims");
        }
        Area area = claim.getArea();
        int ax = area.ax;
        int ay = area.ay;
        int bx = area.bx;
        int by = area.by;
        if (Math.abs(ax - x) < Math.abs(bx - x)) { // Closer to western edge
            ax = x;
        } else {
            bx = x;
        }
        if (Math.abs(ay - z) < Math.abs(by - z)) {
            ay = z;
        } else {
            by = z;
        }
        Area newArea = new Area(ax, ay, bx, by);
        claim.setArea(newArea);
        claim.saveToDatabase();
        player.sendMessage(ChatColor.BLUE + "Shrunk your claim to where you are standing");
        plugin.highlightClaim(claim, player);
        return true;
    }

    private boolean abandon(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new CommandWarn("There is no claim here.");
        }
        if (!claim.isOwner(player)) {
            throw new CommandWarn("This claim does not belong to you.");
        }
        long life = System.currentTimeMillis() - claim.getCreated();
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        long cooldown = settings.claimAbandonCooldown * 1000L * 60L;
        if (life < cooldown) {
            long wait = (cooldown - life) / (1000L * 60L);
            if (wait <= 1) {
                throw new CommandWarn("You must wait one more minute to abandon this claim.");
            } else {
                throw new CommandWarn("You must wait " + wait + " more minutes to abandon this claim.");
            }
        }
        plugin.setMetadata(player, plugin.META_ABANDON, claim.getId());
        BaseComponent[] txt = net.md_5.bungee.api.chat.TextComponent
            .fromLegacyText("Really delete this claim? All claim blocks will be lost.",
                            ChatColor.WHITE);
        player.spigot().sendMessage(txt);
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("This cannot be undone! ").color(ChatColor.RED);
        cb.append("[Confirm]").color(ChatColor.YELLOW);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim confirm " + claim.getId()));
        txt = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ChatColor.YELLOW + "Confirm claim removal.");
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
        cb.append(" ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        txt = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ChatColor.RED + "Cancel claim removal.");
        cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
        player.spigot().sendMessage(cb.create());
        return true;
    }

    public Component makeClaimInfo(Player player, Claim claim) {
        List<Component> lines = new ArrayList<>();
        if (claim.getName() != null) {
            lines.add(TextComponent.ofChildren(new Component[] {
                        Component.text("Name ", NamedTextColor.GRAY),
                        Component.text(claim.getName(), NamedTextColor.WHITE),
                    }));
        }
        lines.add(TextComponent.ofChildren(new Component[] {
                    Component.text("Owner ", NamedTextColor.GRAY),
                    Component.text(claim.getOwnerName(), NamedTextColor.WHITE),
                }));
        lines.add(TextComponent.ofChildren(new Component[] {
                    Component.text("Location ", NamedTextColor.GRAY),
                    Component.text(plugin.worldDisplayName(claim.getWorld() + " " + claim.centerX + "," + claim.centerY)),
                }));
        lines.add(TextComponent.ofChildren(new Component[] {
                    Component.text("Size ", NamedTextColor.GRAY),
                    Component.text("" + claim.getArea().width()),
                    Component.text("x", NamedTextColor.GRAY),
                    Component.text("" + claim.getArea().height()),
                    Component.text(" \u2192 ", NamedTextColor.GRAY),
                    Component.text("" + claim.getArea().size(), NamedTextColor.WHITE),
                    Component.text(" / ", NamedTextColor.GRAY),
                    Component.text("" + claim.getBlocks(), NamedTextColor.WHITE),
                }));
        // Members and Visitors
        for (TrustType trustType : TrustType.values()) {
            if (trustType.isNone()) continue;
            List<PlayerCache> list = claim.listPlayers(trustType::is);
            if (list.isEmpty()) continue;
            Component header = trustType.isBan()
                ? Component.text("Banned ", NamedTextColor.GRAY)
                : Component.text(trustType.displayName + " Trust ", NamedTextColor.GRAY);
            lines.add(Component.text().color(trustType.isBan() ? NamedTextColor.RED : NamedTextColor.WHITE)
                      .append(header)
                      .append(Component.join(Component.text(", ", NamedTextColor.GRAY),
                                             list.stream()
                                             .map(PlayerCache::getName)
                                             .sorted()
                                             .map(name -> Component.text(name))
                                             .collect(Collectors.toList())))
                      .build());
        }
        // Subclaims
        List<Subclaim> subclaims = claim.getSubclaims(player.getWorld());
        if (!subclaims.isEmpty()) {
            lines.add(TextComponent.ofChildren(new Component[] {
                        Component.text("Subclaims ", NamedTextColor.GRAY),
                        Component.text("" + subclaims.size(), NamedTextColor.WHITE),
                    }));
        }
        // Settings
        List<Component> settingsList = new ArrayList<>();
        for (Claim.Setting setting : Claim.Setting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            Object value = claim.getSetting(setting);
            if (value == null || value == setting.defaultValue) continue;
            final String valueString;
            final NamedTextColor valueColor;
            if (value == Boolean.TRUE) {
                valueString = "on";
                valueColor = NamedTextColor.GREEN;
            } else if (value == Boolean.FALSE) {
                valueString = "off";
                valueColor = NamedTextColor.YELLOW;
            } else {
                valueString = value.toString();
                valueColor = NamedTextColor.GRAY;
            }
            settingsList.add(Component.text(setting.key + "=" + valueString,
                                            (setting.isAdminOnly() ? NamedTextColor.DARK_RED : valueColor)));
        }
        if (settingsList.isEmpty()) {
            lines.add(TextComponent.ofChildren(new Component[] {
                        Component.text("Settings ", NamedTextColor.GRAY),
                        Component.join(Component.text(" "), settingsList),
                    }));
        }
        return Component.join(Component.newline(), lines);
    }

    private boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        List<Claim> playerClaims = plugin.findClaims(player);
        if (playerClaims.isEmpty()) {
            throw new CommandWarn("No claims to show");
        }
        int ci = 0;
        player.sendMessage(PlayerCommand.frame(playerClaims.size() > 1
                                               ? "You have " + playerClaims.size() + " claims"
                                               : "You have one claim"));
        final int pageCount = (playerClaims.size() - 1) / pageLen + 1;
        List<Component> pages = new ArrayList<>(pageCount);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
            List<Component> lines = new ArrayList<>(pageLen);
            for (int i = 0; i < pageLen; i += 1) {
                int claimIndex = pageIndex * pageLen + i;
                if (claimIndex >= playerClaims.size()) break;
                Claim claim = playerClaims.get(claimIndex);
                String displayName = claim.getName() != null
                    ? claim.getName()
                    : plugin.worldDisplayName(claim.getWorld());
                lines.add(Component.text().color(NamedTextColor.WHITE)
                          .append(Component.text(" + ", NamedTextColor.GREEN))
                          .append(Component.text(displayName))
                          .clickEvent(ClickEvent.runCommand("/claim info " + claim.getId()))
                          .hoverEvent(HoverEvent.showText(makeClaimInfo(player, claim)))
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
        return true;
    }

    private boolean listInvites(Player player, String[] args) {
        if (args.length != 0) return false;
        List<Claim> playerClaims = new ArrayList<>();
        for (Claim claim : plugin.getClaims()) {
            if (!claim.isOwner(player) && !claim.isHidden() && claim.getTrustType(player).canBuild()) {
                playerClaims.add(claim);
            }
        }
        if (playerClaims.isEmpty()) {
            throw new CommandWarn("No claims to show");
        }
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("Invited").color(ChatColor.GRAY);
        for (Claim claim : playerClaims) {
            String claimName = claim.getName() != null ? claim.getName() : claim.getOwnerName();
            cb.append("  ");
            cb.append("[" + claimName + "]").color(ChatColor.GREEN);
            cb.event(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                                             "/claim info " + claim.getId()));
            BaseComponent[] txt = net.md_5.bungee.api.chat.TextComponent
                .fromLegacyText(ChatColor.GREEN + claim.getOwnerName()
                                + " invited you to this claim.");
            cb.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, txt));
        }
        player.spigot().sendMessage(cb.create());
        return true;
    }

    private boolean ban(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache target = requirePlayerCache(args[0]);
        if (claim.isOwner(target.uuid)) {
            throw new CommandWarn("You cannot ban a claim owner!");
        }
        ClaimTrust row = claim.getTrusted().get(target.uuid);
        if (row != null) {
            if (row.parseTrustType().gt(claim.getTrustType(player))) {
                throw new CommandWarn(target.name + " outranks you in this claim!");
            }
            if (row.parseTrustType().isBan()) {
                throw new CommandWarn(target.name + " is already banned!");
            }
            row.setTrustType(TrustType.BAN);
            plugin.db.updateAsync(row, null);
        } else {
            row = new ClaimTrust(claim, TrustType.BAN, target.uuid);
            claim.getTrusted().put(target.uuid, row);
            plugin.db.insertAsync(row, null);
        }
        player.sendMessage(Component.text(target.name + " banned from this claim", NamedTextColor.DARK_RED));
        return true;
    }

    private boolean unban(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache target = requirePlayerCache(args[0]);
        ClaimTrust row = claim.getTrusted().get(target.uuid);
        if (row == null || !row.parseTrustType().isBan()) {
            throw new CommandWarn(target.name + " was not bannend!");
        }
        claim.getTrusted().remove(target.uuid);
        plugin.db.deleteAsync(row, null);
        player.sendMessage(Component.text(target.name + " no longer banned in this claim", NamedTextColor.GREEN));
        return true;
    }

    private boolean kick(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            throw new CommandWarn("Player not found: " + args[0]);
        }
        if (claim.isOwner(target)) {
            throw new CommandWarn("You cannot kick a claim owner!");
        }
        if (!claim.contains(player.getLocation())) {
            throw new CommandWarn(target.getName() + " is not in this claim!");
        }
        if (claim.getTrustType(target).gt(claim.getTrustType(player))) {
            throw new CommandWarn(target.getName() + " outranks you in this claim!");
        }
        claim.kick(target);
        player.sendMessage(Component.text(target.getName() + " kicked from this claim", NamedTextColor.DARK_RED));
        return true;
    }

    /**
     * Get claim with the desired trust level or higher at the
     * _entire_ claim. Subclaims are ignored here!
     */
    private Claim requireClaim(Player player, BlockVector at, TrustType trustType) {
        Claim claim = plugin.getClaimAt(at);
        if (claim == null) throw new CommandWarn("There is no claim here");
        if (claim.getTrustType(player).lt(trustType)) {
            throw new CommandWarn("You don't have " + trustType.displayName + " trust in this claim");
        }
        return claim;
    }

    private Claim requireClaim(Player player, TrustType trustType) {
        return requireClaim(player, BlockVector.of(player.getLocation()), trustType);
    }

    private PlayerCache requirePlayerCache(String arg) {
        PlayerCache playerCache = PlayerCache.forName(arg);
        if (playerCache == null) throw new CommandWarn("Player not found: " + arg);
        return playerCache;
    }

    private List<String> completeTrustedPlayerNames(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return null;
        Claim claim = plugin.getClaimAt(context.player.getLocation());
        if (claim == null || !claim.getTrustType(context.player).isCoOwner()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (PlayerCache playerCache : claim.listPlayers(TrustType::isTrust)) {
            if (playerCache.name.contains(arg)) result.add(playerCache.name);
        }
        return result;
    }

    private List<String> completeBannedPlayerNames(CommandContext context, CommandNode node, String arg) {
        if (context.player == null) return null;
        Claim claim = plugin.getClaimAt(context.player.getLocation());
        if (claim == null || !claim.getTrustType(context.player).isCoOwner()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (PlayerCache playerCache : claim.listPlayers(TrustType::isBan)) {
            if (playerCache.name.contains(arg)) result.add(playerCache.name);
        }
        return result;
    }
}
