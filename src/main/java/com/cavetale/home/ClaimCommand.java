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
import com.cavetale.core.money.Money;
import com.cavetale.home.sql.SQLHomeWorld;
import com.cavetale.home.struct.BlockVector;
import com.cavetale.mytems.item.coin.Coin;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
            .remotePlayerCaller(this::port);
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
            .description("Lift a ban")
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
        plugin.sessions.of(player).setSidebarTicks(0);
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
            claim = plugin.getClaimById(claimId);
            if (claim == null) return true;
        }
        player.sendMessage("");
        player.sendMessage(Util.frame("Claim Info"));
        player.sendMessage(makeClaimInfo(player, claim));
        if (claim.getTrustType(player).canBuild()) {
            player.sendMessage(text("Teleport ", GRAY)
                               .append(text("[Port]", BLUE))
                               .hoverEvent(showText(text("Port to this claim", BLUE)))
                               .clickEvent(runCommand("/claim port " + claim.getId())));
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
        World playerWorld = player.getWorld();
        String playerWorldName = playerWorld.getName();
        if (!plugin.isLocalHomeWorld(playerWorldName)) {
            throw new CommandWarn("You cannot make claims in this world");
        }
        if (plugin.getMirrorWorlds().containsKey(playerWorldName)) {
            playerWorldName = plugin.getMirrorWorlds().get(playerWorldName);
        }
        // Check for other claims
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int y = playerLocation.getBlockZ();
        UUID uuid = player.getUniqueId();
        // Check for claim collisions
        final int claimSize;
        final double claimCost;
        int playerClaimCount = plugin.findClaims(uuid).size();
        if (playerClaimCount == 0) {
            claimSize = Globals.INITIAL_CLAIM_SIZE;
            claimCost = Globals.INITIAL_CLAIM_COST;
        } else {
            claimSize = Globals.SECONDARY_CLAIM_SIZE;
            claimCost = Globals.SECONDARY_CLAIM_COST;
        }
        if (!Money.get().has(uuid, claimCost)) {
            throw new CommandWarn(join(noSeparators(), text("You cannot afford ", RED), Coin.format(claimCost)));
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
        // Here we use Aqua as base color and White as highlight. Perhaps rethink this?
        TextComponent.Builder message = text().color(WHITE);
        message.append(newline())
            .append(Util.frame("New Claim"))
            .append(newline())
            .append(text("You are about to create a claim of "))
            .append(text(area.width() + "x" + area.height() + " blocks", BLUE))
            .append(text(" around your current location."));
        if (claimCost >= 0.01) {
            message.append(text(" This will cost you "))
                .append(Coin.format(claimCost))
                .append(text("."));
        }
        message.append(text(" Your new claim will have "))
            .append(text(area.size(), BLUE))
            .append(text(" claim blocks."));
        if (Globals.CLAIM_BLOCK_COST >= 0.01) {
            message.append(text(" You can buy additional claim blocks for "))
                .append(Coin.format(Globals.CLAIM_BLOCK_COST))
                .append(text(" per block."));
        }
        message.append(text(" You can abandon the claim after a cooldown of "))
            .append(text(Globals.CLAIM_ABANDON_COOLDOWN + " minutes", BLUE))
            .append(text(". Claim blocks will "))
            .append(text("not", BLUE))
            .append(text(" be refunded."))
            .append(newline())
            .append(text("Proceed? ", GRAY))
            .append(text("[Confirm]", GREEN)
                    .clickEvent(runCommand("/claim confirm " + ncmeta.token))
                    .hoverEvent(showText(text("Confirm this purchase", GREEN))))
            .append(space())
            .append(text("[Cancel]", RED)
                    .clickEvent(runCommand("/claim cancel"))
                    .hoverEvent(showText(text("Cancel this purchase", RED))))
            .append(newline());
        player.sendMessage(message);
        return true;
    }

    private boolean port(final RemotePlayer player, String[] args) {
        if (args.length > 1) return false;
        UUID uuid = player.getUniqueId();
        final Claim claim;
        if (args.length == 1) {
            int claimId = CommandArgCompleter.requireInt(args[0], i -> i > 0);
            claim = plugin.getClaimById(claimId);
            if (claim == null) {
                throw new CommandWarn("Claim not found");
            }
            if (!claim.isOwner(uuid) && claim.isHidden()) {
                throw new CommandWarn("Cannot visit claim");
            }
            if (!claim.getTrustType(uuid).canBuild()) {
                throw new CommandWarn("Cannot build in claim");
            }
        } else {
            claim = plugin.findPrimaryClaim(player.getUniqueId());
            if (claim == null) {
                throw new CommandWarn("You don't have a claim yet");
            }
        }
        SQLHomeWorld homeWorld = plugin.findHomeWorld(claim.getWorld());
        if (homeWorld != null && !homeWorld.isOnThisServer() && player.isPlayer()) {
            Connect.get().dispatchRemoteCommand(player.getPlayer(), "claim port " + String.join(" ", args), homeWorld.getServer());
            return true;
        }
        final World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) {
            throw new CommandWarn("Claim not found");
        }
        final int x = claim.getCenterX();
        final int z = claim.getCenterY();
        world.getChunkAtAsync(x >> 4, z >> 4, (Consumer<Chunk>) chunk -> {
                final Location location;
                if (world.getEnvironment() == World.Environment.NETHER) {
                    Block block = world.getBlockAt(x, 1, z);
                    while (!block.isEmpty() || !block.getRelative(0, 1, 0).isEmpty() || !block.getRelative(0, -1, 0).getType().isSolid()) {
                        block = block.getRelative(0, 1, 0);
                    }
                    location = block.getLocation().add(0.5, 0.0, 0.5);
                } else {
                    location = world.getHighestBlockAt(x, z).getLocation().add(0.5, 1.0, 0.5);
                }
                player.bring(plugin, location, player2 -> {
                        if (player2 == null) return;
                        player2.sendMessage(text("Teleporting to claim", GREEN));
                        player2.sendMessage("x=" + x + ", z=" + z);
                    });
            });
        return true;
    }

    private boolean buy(Player player, String[] args) {
        if (args.length != 1) return false;
        final UUID uuid = player.getUniqueId();
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
            claim = plugin.getClaimById(snippet.claimId);
        } else {
            claim = plugin.findNearestOwnedClaim(player, 512);
        }
        if (claim == null) {
            throw new CommandWarn("There is no claim here!");
        }
        if (!claim.isOwner(player)) {
            throw new CommandWarn("You do not own this claim!");
        }
        double price = (double) buyClaimBlocks * Globals.CLAIM_BLOCK_COST;
        if (!Money.get().has(uuid, price)) {
            throw new CommandWarn(join(noSeparators(), text("You do not have "),
                                       Coin.format(price), text(" to buy "),
                                       text(buyClaimBlocks + " claim blocks"))
                                  .color(RED));
        }
        BuyClaimBlocks meta = new BuyClaimBlocks(buyClaimBlocks, price, claim.getId(), ""
                                                 + ThreadLocalRandom.current().nextInt(9999));
        plugin.setMetadata(player, plugin.META_BUY, meta);
        ComponentLike message = text().color(WHITE)
            .content("Buying ")
            .append(text(buyClaimBlocks, GREEN))
            .append(text(" for "))
            .append(Coin.format(price))
            .append(text("."))
            .append(newline())
            .append(text("Confirm this purchase ", GRAY))
            .append(text("[Confirm]", GREEN)
                    .clickEvent(runCommand("/claim confirm " + meta.token))
                    .hoverEvent(showText(join(separator(newline()),
                                              text("Confirm", GREEN),
                                              text("Buy " + buyClaimBlocks + " for ", GRAY),
                                              Coin.format(price)))))
            .append(space())
            .append(text("[Cancel]", RED)
                    .clickEvent(runCommand("/claim cancel"))
                    .hoverEvent(showText(text("Cancel purchase", RED))));
        player.sendMessage(message);
        return true;
    }

    private boolean confirm(Player player, String[] args) {
        if (args.length != 1) return true;
        final UUID uuid = player.getUniqueId();
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
            if (!Money.get().take(uuid, meta.price, plugin, "Buy " + meta.amount + " claim blocks")) {
                throw new CommandWarn(join(noSeparators(), text("You cannot afford ", RED), Coin.format(meta.price)));
            }
            claim.setBlocks(claim.getBlocks() + meta.amount);
            Session.ClaimGrowSnippet snippet = plugin.sessions.of(player).getClaimGrowSnippet();
            plugin.sessions.of(player).setClaimGrowSnippet(null);
            Location location = player.getLocation();
            if (snippet != null && snippet.isNear(location) && claim.growTo(location.getBlockX(), location.getBlockZ()).isSuccessful()) {
                player.sendMessage(text("Added " + meta.amount + " and grew to your location!"));
                plugin.highlightClaim(claim, player);
                PluginPlayerEvent.Name.GROW_CLAIM.call(plugin, player);
            } else if (claim.getSetting(ClaimSetting.AUTOGROW)) {
                player.sendMessage(text("Added " + meta.amount + " blocks to this claim. It will grow automatically.", GREEN));
            } else {
                player.sendMessage(text("Added " + meta.amount
                                        + " blocks to this claim."
                                        + " Grow it manually or enable \"autogrow\" in the settings.", GREEN));
            }
            PluginPlayerEvent.Name.BUY_CLAIM_BLOCKS.make(plugin, player)
                .detail(Detail.COUNT, meta.amount)
                .detail(Detail.MONEY, meta.price)
                .callEvent();
            return true;
        }
        // AbandonClaim confirm
        int claimId = plugin.getMetadata(player, plugin.META_ABANDON, Integer.class).orElse(-1);
        if (claimId >= 0) {
            plugin.removeMetadata(player, plugin.META_ABANDON);
            Claim claim = plugin.getClaimById(claimId);
            if (claim == null || !claim.isOwner(player) || !args[0].equals("" + claimId)) {
                throw new CommandWarn("Claim removal expired");
            }
            plugin.deleteClaim(claim);
            player.sendMessage(text("Claim removed", YELLOW));
            return true;
        }
        // NewClaim confirm
        NewClaimMeta ncmeta = plugin.getMetadata(player, plugin.META_NEWCLAIM, NewClaimMeta.class)
            .orElse(null);
        if (ncmeta != null) {
            plugin.removeMetadata(player, plugin.META_NEWCLAIM);
            if (!args[0].equals(ncmeta.token)) return true;
            if (!plugin.isLocalHomeWorld(ncmeta.world)) return true;
            for (Claim claimInWorld : plugin.findClaimsInWorld(ncmeta.world)) {
                // This whole check is a repeat from the new claim command.
                if (claimInWorld.getArea().overlaps(ncmeta.area)) {
                    throw new CommandWarn("Your claim would overlap an existing claim.");
                }
            }
            if (ncmeta.price >= 0.01) {
                if (!Money.get().take(uuid, ncmeta.price, plugin,
                                      "Make new claim in " + plugin.worldDisplayName(ncmeta.world))) {
                    throw new CommandWarn(join(noSeparators(), text("You cannot afford ", RED), Coin.format(ncmeta.price)));
                }
            }
            Claim claim = new Claim(plugin, uuid, ncmeta.world, ncmeta.area);
            plugin.getClaimCache().add(claim);
            claim.insertIntoDatabase(success -> {
                    if (!success) {
                        player.sendMessage(text("Something went wrong! Please contact an administrator", RED));
                        plugin.getClaimCache().remove(claim);
                        return;
                    }
                    ComponentLike message = text().color(WHITE)
                        .append(text("Claim created!  "))
                        .append(text("[View]", GREEN))
                        .clickEvent(runCommand("/claim info"))
                        .hoverEvent(showText(join(separator(newline()),
                                                  text("/claim info"),
                                                  text("View claim info and highlight your claim",
                                                       GRAY))));
                    player.sendMessage(message);
                    plugin.highlightClaim(claim, player);
                    PluginPlayerEvent.Name.CREATE_CLAIM.call(plugin, player);
                });
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
            player.sendMessage(text("Claim operation cancelled", RED));
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
        TrustType oldTrust = claim.getTrustType(target.uuid);
        if (oldTrust.gte(trustType)) {
            throw new CommandWarn(target.name + " already has " + oldTrust.displayName + " trust in this claim");
        } else if (oldTrust.isBan()) {
            throw new CommandWarn(target.name + " is banned from this claim!");
        }
        claim.setTrustType(target.uuid, trustType);
        player.sendMessage(text(target.name + " now has " + trustType.displayName
                                + " trust in this claim.", GREEN));
        PluginPlayerEvent.Name.CLAIM_TRUST.make(plugin, player)
            .detail(Detail.TARGET, target.uuid)
            .detail(Detail.NAME, trustType.key)
            .callEvent();
        return true;
    }

    private boolean untrust(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache playerCache = requirePlayerCache(args[0]);
        TrustType oldTrust = claim.getTrustType(playerCache.uuid);
        if (!oldTrust.isTrust()) {
            throw new CommandWarn(playerCache.name + " is not trusted here!");
        }
        if (oldTrust.gt(claim.getTrustType(player))) {
            throw new CommandWarn(playerCache.name + " outranks you in this claim!");
        }
        claim.removeTrust(playerCache.uuid);
        player.sendMessage(text("Removed " + oldTrust.displayName + " trust for " + playerCache.name, GREEN));
        PluginPlayerEvent.Name.CLAIM_UNTRUST.make(plugin, player)
            .detail(Detail.TARGET, playerCache.uuid)
            .callEvent();
        return true;
    }

    private boolean rename(Player player, String[] args) {
        if (args.length == 0) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        String name = String.join(" ", args);
        if (name.length() > 24) throw new CommandWarn("Name too long!");
        claim.setName(name);
        player.sendMessage(text("Claim renamed to " + name, YELLOW));
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
        ClaimSetting setting;
        try {
            setting = ClaimSetting.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Unknown claim setting: " + args[0]);
        }
        if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) {
            throw new CommandWarn("Unknown claim setting: " + args[0]);
        }
        final boolean value;
        switch (args[1]) {
        case "on": case "true": case "enabled": value = true; break;
        case "off": case "false": case "disabled": value = false; break;
        default:
            throw new CommandWarn("Unknown settings value: " + args[1]);
        }
        if (claim.getSetting(setting) != value) {
            claim.setSetting(setting, value);
        }
        showClaimSettings(claim, player);
        PluginPlayerEvent.Name.CHANGE_CLAIM_SETTING.make(plugin, player)
            .detail(Detail.NAME, setting.key)
            .detail(Detail.TOGGLE, value)
            .callEvent();
        return true;
    }

    private void showClaimSettings(Claim claim, Player player) {
        List<ComponentLike> lines = new ArrayList<>();
        lines.add(empty());
        lines.add(Util.frame("Claim Settings"));
        for (ClaimSetting setting : ClaimSetting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            TextComponent.Builder line = text().color(WHITE);
            boolean on = claim.getSetting(setting);
            String key = setting.name().toLowerCase();
            line.append(text("[ON]", (on ? GREEN : DARK_GRAY))
                        .clickEvent(runCommand("/claim set " + key + " on"))
                        .hoverEvent(showText(text("Enable " + setting.displayName, GREEN))));
            line.append(space());
            line.append(text("[OFF]", (on ? DARK_GRAY : RED))
                        .clickEvent(runCommand("/claim set " + key + " off"))
                        .hoverEvent(showText(text("Disable " + setting.displayName, RED))));
            line.append(space());
            line.append(text(setting.displayName, setting.isAdminOnly() ? RED : WHITE));
            lines.add(line);
        }
        lines.add(empty());
        player.sendMessage(join(separator(newline()), lines));
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
        if (claim.getBlocks() < newArea.size()) {
            int needed = newArea.size() - claim.getBlocks();
            Component tooltip = join(separator(newline()),
                                     text("/claim buy " + needed, YELLOW),
                                     text("Buy more " + needed + " claim blocks", GRAY),
                                     text("for ", GRAY),
                                     Coin.format((double) needed * Globals.CLAIM_BLOCK_COST));
            ComponentLike message = text().color(YELLOW)
                .append(text(needed + " more claim blocks required. "))
                .append(text("[Buy More]", GRAY)
                        .clickEvent(runCommand("/claim buy " + needed))
                        .hoverEvent(showText(tooltip)));
            player.sendMessage(message);
            Session.ClaimGrowSnippet snippet = new Session.ClaimGrowSnippet(player.getWorld().getName(), x, z, claim.getId());
            plugin.sessions.of(player).setClaimGrowSnippet(snippet);
            return true;
        }
        for (Claim other : plugin.getClaimCache().within(claim.getWorld(), newArea)) {
            if (other != claim) throw new CommandWarn("Your claim would overlap with another claim");
        }
        claim.setArea(newArea);
        player.sendMessage(text("Grew your claim to where you are standing", GREEN));
        plugin.highlightClaim(claim, player);
        PluginPlayerEvent.Name.GROW_CLAIM.call(plugin, player);
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
        for (Subclaim subclaim : claim.getSubclaims()) {
            if (!newArea.contains(subclaim.getArea())) {
                throw new CommandWarn("There are subclaims in the way!");
            }
        }
        claim.setArea(newArea);
        player.sendMessage(text("Shrunk your claim to where you are standing", GREEN));
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
        long life = System.currentTimeMillis() - claim.getCreated().getTime();
        long cooldown = Globals.CLAIM_ABANDON_COOLDOWN * 1000L * 60L;
        if (life < cooldown) {
            long wait = (cooldown - life) / (1000L * 60L);
            if (wait <= 1) {
                throw new CommandWarn("You must wait one more minute to abandon this claim.");
            } else {
                throw new CommandWarn("You must wait " + wait + " more minutes to abandon this claim.");
            }
        }
        plugin.setMetadata(player, plugin.META_ABANDON, claim.getId());
        ComponentLike message = text().color(DARK_RED)
            .append(text("Really delete this claim? All claim blocks will be lost."))
            .append(newline())
            .append(text("This cannot be undone! "))
            .append(text("[Confirm]", RED)
                    .clickEvent(runCommand("/claim confirm " + claim.getId()))
                    .hoverEvent(showText(text("Confirm Claim Abandonment", RED))))
            .append(space())
            .append(text("[Cancel]", GREEN)
                    .clickEvent(runCommand("/claim cancel"))
                    .hoverEvent(showText(text("Cancel Claim Abandonment", GREEN))));
        player.sendMessage(message);
        return true;
    }

    public Component makeClaimInfo(Player player, Claim claim) {
        List<Component> lines = new ArrayList<>();
        if (claim.getName() != null) {
            lines.add(join(noSeparators(), text("Name ", GRAY), text(claim.getName(), WHITE)));
        }
        lines.add(join(noSeparators(), text("Owner ", GRAY), text(claim.getOwnerName(), WHITE)));
        lines.add(join(noSeparators(), text("Location ", GRAY),
                       text(plugin.worldDisplayName(claim.getWorld() + " " + claim.getCenterX() + "," + claim.getCenterY()))));
        lines.add(join(noSeparators(), text("Size ", GRAY),
                       text("" + claim.getArea().width()),
                       text("x", GRAY),
                       text("" + claim.getArea().height()),
                       text(" \u2192 ", GRAY),
                       text("" + claim.getArea().size(), WHITE),
                       text(" / ", GRAY),
                       text("" + claim.getBlocks(), WHITE)));
        // Members and Visitors
        for (TrustType trustType : TrustType.values()) {
            if (trustType.isNone()) continue;
            List<PlayerCache> list = claim.listPlayers(trustType::is);
            if (list.isEmpty()) continue;
            Component header = trustType.isBan()
                ? text("Banned ", GRAY)
                : text(trustType.displayName + " Trust ", GRAY);
            lines.add(text().color(trustType.isBan() ? RED : WHITE)
                      .append(header)
                      .append(join(separator(text(", ", GRAY)),
                                   list.stream()
                                   .map(PlayerCache::getName)
                                   .sorted()
                                   .map(name -> text(name))
                                   .collect(Collectors.toList())))
                      .build());
        }
        // Subclaims
        List<Subclaim> subclaims = claim.getSubclaims(player.getWorld());
        if (!subclaims.isEmpty()) {
            lines.add(join(noSeparators(), text("Subclaims ", GRAY), text("" + subclaims.size(), WHITE)));
        }
        // Settings
        List<Component> settingsList = new ArrayList<>();
        for (ClaimSetting setting : ClaimSetting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            boolean value = claim.getSetting(setting);
            if (value == setting.defaultValue) continue;
            final String valueString;
            final TextColor valueColor;
            if (value) {
                valueString = "on";
                valueColor = GREEN;
            } else {
                valueString = "off";
                valueColor = RED;
            }
            settingsList.add(join(noSeparators(), text(setting.key), text(":", DARK_GRAY), text(valueString))
                             .color(setting.isAdminOnly() ? DARK_RED : valueColor));
        }
        if (!settingsList.isEmpty()) {
            lines.add(join(noSeparators(), text("Settings ", GRAY), join(separator(text(" ")), settingsList)));
        }
        return join(separator(newline()), lines);
    }

    private boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        List<Claim> playerClaims = plugin.findClaims(player);
        if (playerClaims.isEmpty()) {
            throw new CommandWarn("No claims to show");
        }
        int ci = 0;
        player.sendMessage(Util.frame(playerClaims.size() > 1
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
                lines.add(text().color(WHITE)
                          .append(text(" + ", GREEN))
                          .append(text(displayName))
                          .clickEvent(runCommand("/claim info " + claim.getId()))
                          .hoverEvent(showText(makeClaimInfo(player, claim)))
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
        return true;
    }

    private boolean listInvites(Player player, String[] args) {
        if (args.length != 0) return false;
        List<Claim> playerClaims = new ArrayList<>();
        for (Claim claim : plugin.getClaimCache().getAllClaims()) {
            if (!claim.isOwner(player) && !claim.isHidden() && claim.getTrustType(player).canBuild()) {
                playerClaims.add(claim);
            }
        }
        if (playerClaims.isEmpty()) {
            throw new CommandWarn("No claims to show");
        }
        TextComponent.Builder message = text();
        message.append(text("Invited", GRAY));
        for (Claim claim : playerClaims) {
            String claimName = claim.getName() != null ? claim.getName() : claim.getOwnerName();
            message.append(space());
            message.append(text("[" + claimName + "]", GREEN)
                           .clickEvent(runCommand("/claim info " + claim.getId()))
                           .hoverEvent(showText(text(claim.getOwnerName()
                                                     + " invited you to this claim",
                                                     GREEN))));
        }
        player.sendMessage(message);
        return true;
    }

    private boolean ban(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache target = requirePlayerCache(args[0]);
        if (claim.isOwner(target.uuid)) {
            throw new CommandWarn("You cannot ban a claim owner!");
        }
        TrustType oldTrust = claim.getTrustType(target.uuid);
        if (oldTrust.gt(claim.getTrustType(player))) {
            throw new CommandWarn(target.name + " outranks you in this claim!");
        }
        if (oldTrust.isBan()) {
            throw new CommandWarn(target.name + " is already banned!");
        }
        claim.setTrustType(target.uuid, TrustType.BAN);
        player.sendMessage(text(target.name + " banned from this claim", DARK_RED));
        return true;
    }

    private boolean unban(Player player, String[] args) {
        if (args.length != 1) return false;
        Claim claim = requireClaim(player, TrustType.CO_OWNER);
        PlayerCache target = requirePlayerCache(args[0]);
        TrustType oldTrust = claim.getTrustType(target.uuid);
        if (!oldTrust.isBan()) {
            throw new CommandWarn(target.name + " was not bannend!");
        }
        claim.removeTrust(target.uuid);
        player.sendMessage(text(target.name + " no longer banned in this claim", GREEN));
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
        if (!claim.contains(target.getLocation())) {
            throw new CommandWarn(target.getName() + " is not in this claim!");
        }
        if (claim.getTrustType(target).gt(claim.getTrustType(player))) {
            throw new CommandWarn(target.getName() + " outranks you in this claim!");
        }
        claim.kick(target);
        player.sendMessage(text(target.getName() + " kicked from this claim", DARK_RED));
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
