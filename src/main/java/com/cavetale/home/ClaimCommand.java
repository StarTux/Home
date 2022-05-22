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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
        // Here we use Aqua as base color and White as highlight. Perhaps rethink this?
        TextComponent.Builder message = Component.text().color(NamedTextColor.WHITE);
        message.append(Component.newline())
            .append(Util.frame("New Claim"))
            .append(Component.newline())
            .append(Component.text("You are about to create a claim of "))
            .append(Component.text(area.width() + "x" + area.height() + " blocks", NamedTextColor.BLUE))
            .append(Component.text(" around your current location."));
        if (claimCost >= 0.01) {
            message.append(Component.text(" This will cost you "))
                .append(Component.text(Money.format(claimCost), NamedTextColor.GOLD))
                .append(Component.text("."));
        }
        message.append(Component.text(" Your new claim will have "))
            .append(Component.text(area.size(), NamedTextColor.BLUE))
            .append(Component.text(" claim blocks."));
        if (settings.claimBlockCost >= 0.01) {
            message.append(Component.text(" You can buy additional claim blocks for "))
                .append(Component.text(Money.format(settings.claimBlockCost), NamedTextColor.GOLD))
                .append(Component.text(" per block."));
        }
        message.append(Component.text(" You can abandon the claim after a cooldown of "))
            .append(Component.text(settings.claimAbandonCooldown + " minutes", NamedTextColor.BLUE))
            .append(Component.text(". Claim blocks will "))
            .append(Component.text("not", NamedTextColor.BLUE))
            .append(Component.text(" be refunded."))
            .append(Component.newline())
            .append(Component.text("Proceed? ", NamedTextColor.GRAY))
            .append(Component.text("[Confirm]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/claim confirm " + ncmeta.token))
                    .hoverEvent(HoverEvent.showText(Component.text("Confirm this purchase", NamedTextColor.GREEN))))
            .append(Component.space())
            .append(Component.text("[Cancel]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/claim cancel"))
                    .hoverEvent(HoverEvent.showText(Component.text("Cancel this purchase", NamedTextColor.RED))))
            .append(Component.newline());
        player.sendMessage(message);
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
            claim = plugin.getClaimById(claimId);
            if (claim == null) return true;
            if (!claim.isOwner(player) && claim.isHidden()) return true;
            if (!claim.getTrustType(player).canBuild()) return true;
        } else {
            claim = plugin.findPrimaryClaim(player.getUniqueId());
            if (claim == null) {
                throw new CommandWarn("You don't have a claim yet.");
            }
        }
        final World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return true;
        final int x = claim.centerX;
        final int z = claim.centerY;
        world.getChunkAtAsync(x >> 4, z >> 4, (Consumer<Chunk>) chunk -> {
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
                player.sendMessage(Component.text("Teleporting to claim", NamedTextColor.BLUE));
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
        ComponentLike message = Component.text().color(NamedTextColor.WHITE)
            .content("Buying ")
            .append(Component.text(buyClaimBlocks, NamedTextColor.GREEN))
            .append(Component.text(" for "))
            .append(Component.text(priceFormat, NamedTextColor.GOLD))
            .append(Component.text("."))
            .append(Component.newline())
            .append(Component.text("Confirm this purchase ", NamedTextColor.GRAY))
            .append(Component.text("[Confirm]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/claim confirm " + meta.token))
                    .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()),
                                                                   Component.text("Confirm", NamedTextColor.GREEN),
                                                                   Component.text("Buy " + buyClaimBlocks
                                                                                  + " for " + priceFormat,
                                                                                  NamedTextColor.GRAY)))))
            .append(Component.space())
            .append(Component.text("[Cancel]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/claim cancel"))
                    .hoverEvent(HoverEvent.showText(Component.text("Cancel purchase", NamedTextColor.RED))));
        player.sendMessage(message);
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
                player.sendMessage(Component.text("Added " + meta.amount + " and grew to your location!"));
                plugin.highlightClaim(claim, player);
                PluginPlayerEvent.Name.GROW_CLAIM.call(plugin, player);
            } else if (claim.getBoolSetting(Claim.Setting.AUTOGROW)) {
                player.sendMessage(Component.text("Added " + meta.amount
                                                  + " blocks to this claim. It will grow automatically."));
            } else {
                player.sendMessage(Component.text("Added " + meta.amount
                                                  + " blocks to this claim."
                                                  + " Grow it manually or enable \"autogrow\" in the settings."));
            }
            claim.saveToDatabase();
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
            player.sendMessage(Component.text("Claim removed", NamedTextColor.YELLOW));
            return true;
        }
        // NewClaim confirm
        NewClaimMeta ncmeta = plugin.getMetadata(player, plugin.META_NEWCLAIM, NewClaimMeta.class)
            .orElse(null);
        if (ncmeta != null) {
            plugin.removeMetadata(player, plugin.META_NEWCLAIM);
            if (!args[0].equals(ncmeta.token)) return true;
            if (!plugin.isLocalHomeWorld(ncmeta.world)) return true;
            WorldSettings settings = plugin.getWorldSettings().get(ncmeta.world);
            for (Claim claimInWorld : plugin.findClaimsInWorld(ncmeta.world)) {
                // This whole check is a repeat from the new claim command.
                if (claimInWorld.getArea().overlaps(ncmeta.area)) {
                    throw new CommandWarn("Your claim would overlap an existing claim.");
                }
            }
            if (ncmeta.price >= 0.01) {
                if (!Money.take(playerId, ncmeta.price, plugin,
                                "Make new claim in " + plugin.worldDisplayName(ncmeta.world))) {
                    throw new CommandWarn("You cannot afford " + Money.format(ncmeta.price) + "!");
                }
            }
            Claim claim = new Claim(plugin, playerId, ncmeta.world, ncmeta.area);
            claim.saveToDatabase();
            plugin.getClaimCache().add(claim);
            ComponentLike message = Component.text().color(NamedTextColor.WHITE)
                .append(Component.text("Claim created!  "))
                .append(Component.text("[View]", NamedTextColor.GREEN))
                .clickEvent(ClickEvent.runCommand("/claim info"))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.separator(Component.newline()),
                                                               Component.text("/claim info"),
                                                               Component.text("View claim info and highlight your claim",
                                                                              NamedTextColor.GRAY))));
            player.sendMessage(message);
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
            player.sendMessage(Component.text("Claim operation cancelled", NamedTextColor.RED));
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
        PluginPlayerEvent.Name.CHANGE_CLAIM_SETTING.make(plugin, player)
            .detail(Detail.NAME, setting.key)
            .detail(Detail.TOGGLE, value)
            .callEvent();
        return true;
    }

    private void showClaimSettings(Claim claim, Player player) {
        List<ComponentLike> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Util.frame("Claim Settings"));
        for (Claim.Setting setting : Claim.Setting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            TextComponent.Builder line = Component.text().color(NamedTextColor.WHITE);
            Object value = claim.getSetting(setting);
            String key = setting.name().toLowerCase();
            boolean on = value == Boolean.TRUE;
            line.append(Component.text("[ON]", (on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                        .clickEvent(ClickEvent.runCommand("/claim set " + key + " on"))
                        .hoverEvent(HoverEvent.showText(Component.text("Enable " + setting.displayName, NamedTextColor.GREEN))));
            line.append(Component.space());
            line.append(Component.text("[OFF]", (on ? NamedTextColor.DARK_GRAY : NamedTextColor.RED))
                        .clickEvent(ClickEvent.runCommand("/claim set " + key + " off"))
                        .hoverEvent(HoverEvent.showText(Component.text("Disable " + setting.displayName, NamedTextColor.RED))));
            line.append(Component.space());
            line.append(Component.text(setting.displayName, setting.isAdminOnly() ? NamedTextColor.RED : NamedTextColor.WHITE));
            lines.add(line);
        }
        lines.add(Component.empty());
        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
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
            Component tooltip = Component.join(JoinConfiguration.separator(Component.newline()),
                                               Component.text("/claim buy " + needed, NamedTextColor.YELLOW),
                                               Component.text("Buy more " + needed + " claim blocks", NamedTextColor.GRAY),
                                               Component.text("for ", NamedTextColor.GRAY)
                                               .append(Component.text(formatMoney, NamedTextColor.GOLD)));
            ComponentLike message = Component.text().color(NamedTextColor.YELLOW)
                .append(Component.text(needed + " more claim blocks required. "))
                .append(Component.text("[Buy More]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/claim buy " + needed))
                        .hoverEvent(HoverEvent.showText(tooltip)));
            player.sendMessage(message);
            Session.ClaimGrowSnippet snippet = new Session.ClaimGrowSnippet(player.getWorld().getName(), x, z, claim.getId());
            plugin.sessions.of(player).setClaimGrowSnippet(snippet);
            return true;
        }
        for (Claim other : plugin.getClaimCache().within(claim.getWorld(), newArea)) {
            if (other != claim) throw new CommandWarn("Your claim would overlap with another claim");
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        player.sendMessage(Component.text("Grew your claim to where you are standing", NamedTextColor.GREEN));
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
        claim.saveToDatabase();
        player.sendMessage(Component.text("Shrunk your claim to where you are standing", NamedTextColor.GREEN));
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
        ComponentLike message = Component.text().color(NamedTextColor.DARK_RED)
            .append(Component.text("Really delete this claim? All claim blocks will be lost."))
            .append(Component.newline())
            .append(Component.text("This cannot be undone! "))
            .append(Component.text("[Confirm]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/claim confirm " + claim.getId()))
                    .hoverEvent(HoverEvent.showText(Component.text("Confirm Claim Abandonment", NamedTextColor.RED))))
            .append(Component.space())
            .append(Component.text("[Cancel]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/claim cancel"))
                    .hoverEvent(HoverEvent.showText(Component.text("Cancel Claim Abandonment", NamedTextColor.GREEN))));
        player.sendMessage(message);
        return true;
    }

    public Component makeClaimInfo(Player player, Claim claim) {
        List<Component> lines = new ArrayList<>();
        if (claim.getName() != null) {
            lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
                        Component.text("Name ", NamedTextColor.GRAY),
                        Component.text(claim.getName(), NamedTextColor.WHITE),
                    }));
        }
        lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
                    Component.text("Owner ", NamedTextColor.GRAY),
                    Component.text(claim.getOwnerName(), NamedTextColor.WHITE),
                }));
        lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
                    Component.text("Location ", NamedTextColor.GRAY),
                    Component.text(plugin.worldDisplayName(claim.getWorld() + " " + claim.centerX + "," + claim.centerY)),
                }));
        lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
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
                      .append(Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)),
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
            lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
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
            lines.add(Component.join(JoinConfiguration.noSeparators(), new Component[] {
                        Component.text("Settings ", NamedTextColor.GRAY),
                        Component.join(JoinConfiguration.separator(Component.text(" ")), settingsList),
                    }));
        }
        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
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
                lines.add(Component.text().color(NamedTextColor.WHITE)
                          .append(Component.text(" + ", NamedTextColor.GREEN))
                          .append(Component.text(displayName))
                          .clickEvent(ClickEvent.runCommand("/claim info " + claim.getId()))
                          .hoverEvent(HoverEvent.showText(makeClaimInfo(player, claim)))
                          .build());
            }
            pages.add(Component.join(JoinConfiguration.separator(Component.newline()), lines));
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
        TextComponent.Builder message = Component.text();
        message.append(Component.text("Invited", NamedTextColor.GRAY));
        for (Claim claim : playerClaims) {
            String claimName = claim.getName() != null ? claim.getName() : claim.getOwnerName();
            message.append(Component.space());
            message.append(Component.text("[" + claimName + "]", NamedTextColor.GREEN)
                           .clickEvent(ClickEvent.runCommand("/claim info " + claim.getId()))
                           .hoverEvent(HoverEvent.showText(Component.text(claim.getOwnerName()
                                                                          + " invited you to this claim",
                                                                          NamedTextColor.GREEN))));
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
