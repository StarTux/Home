package com.cavetale.home;

import com.cavetale.money.Money;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

@RequiredArgsConstructor
public final class ClaimCommand extends PlayerCommand {
    private final HomePlugin plugin;
    static final List<String> COMMANDS = Arrays
        .asList("new", "info", "list", "port", "buy", "add",
                "invite", "remove", "set", "grow", "shrink");

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

    @Override
    public boolean onCommand(Player player, String[] args) throws Wrong {
        if (args.length == 0) return false;
        if (args.length == 1 && args[0].equals("help")) return false;
        final UUID playerId = player.getUniqueId();
        switch (args[0]) {
        case "info": return infoCommand(player, args);
        case "new": return newCommand(player, args);
        case "list": return listCommand(player, args);
        case "port": return portCommand(player, args);
        case "buy": return buyCommand(player, args);
        case "confirm": return confirmCommand(player, args);
        case "cancel": return cancelCommand(player, args);
        case "add": return addCommand(player, args);
        case "invite": return inviteCommand(player, args);
        case "remove": return removeCommand(player, args);
        case "set": return setCommand(player, args);
        case "grow": return growCommand(player, args);
        case "shrink": return shrinkCommand(player, args);
        case "abandon": return abandonCommand(player, args);
        default:
            return false;
        }
    }

    private boolean infoCommand(Player player, String[] args) throws Wrong {
        if (args.length > 2) return false;
        Claim claim;
        if (args.length == 1) {
            claim = plugin.getClaimAt(player.getLocation());
            if (claim == null) {
                throw new Wrong("Stand in the claim you want info on");
            }
        } else {
            int claimId;
            try {
                claimId = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                return true;
            }
            claim = plugin.findClaimWithId(claimId);
            if (claim == null) return true;
        }
        player.sendMessage("");
        printClaimInfo(player, claim);
        sendClaimButtons(player, claim);
        player.sendMessage("");
        plugin.highlightClaim(claim, player);
        for (Subclaim subclaim : claim.getSubclaims(player.getWorld())) {
            plugin.highlightSubclaim(subclaim, player);
        }
        return true;
    }

    private boolean newCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1) return false;
        boolean isHomeWorld = false;
        boolean isHomeEndWorld = false;
        World playerWorld = player.getWorld();
        String playerWorldName = playerWorld.getName();
        if (!plugin.getHomeWorlds().contains(playerWorldName)) {
            throw new Wrong("You cannot make claims in this world");
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
            throw new Wrong("You cannot afford " + Money.format(claimCost) + "!");
        }
        final int rad = claimSize / 2;
        final int tol = (rad * 2 == claimSize) ? 1 : 0;
        Area area = new Area(x - rad + tol, y - rad + tol, x + rad, y + rad);
        for (Claim claimInWorld : plugin.findClaimsInWorld(playerWorldName)) {
            // This whole check is repeated in the confirm command
            if (claimInWorld.getArea().overlaps(area)) {
                throw new Wrong("Your claim would overlap an existing claim.");
            }
        }
        NewClaimMeta ncmeta = new NewClaimMeta(playerWorldName, x, y, area, claimCost, ""
                                               + ThreadLocalRandom.current().nextInt(9999));
        plugin.setMetadata(player, plugin.META_NEWCLAIM, ncmeta);
        player.sendMessage("");
        player.spigot().sendMessage(frame(new ComponentBuilder(""), "New Claim").create());
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
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + ncmeta.token));
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                .fromLegacyText(ChatColor.GREEN + "Confirm this purchase")));
        cb.append("  ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                .fromLegacyText(ChatColor.RED + "Cancel this purchase")));
        player.spigot().sendMessage(cb.create());
        player.sendMessage("");
        return true;
    }

    private boolean portCommand(final Player player, String[] args) throws Wrong {
        if (args.length > 2) return false;
        final Claim claim;
        if (args.length == 2) {
            int claimId;
            try {
                claimId = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                return true;
            }
            claim = plugin.findClaimWithId(claimId);
            if (claim == null) return true;
            if (!claim.isOwner(player) && claim.isHidden()) return true;
            if (!claim.canVisit(player)) return true;
        } else {
            claim = plugin.findPrimaryClaim(player.getUniqueId());
            if (claim == null) {
                throw new Wrong("You don't have a claim yet.");
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

    private boolean buyCommand(Player player, String[] args) throws Wrong {
        if (args.length != 2) return false;
        final UUID playerId = player.getUniqueId();
        int buyClaimBlocks;
        try {
            buyClaimBlocks = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            buyClaimBlocks = -1;
        }
        if (buyClaimBlocks <= 0) {
            throw new Wrong("Invalid claim blocks amount: " + args[1]);
        }
        Claim claim = plugin.findNearestOwnedClaim(player);
        if (claim == null) {
            throw new Wrong("You don't have a claim in this world");
        }
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        double price = (double) buyClaimBlocks * settings.claimBlockCost;
        String priceFormat = Money.format(price);
        if (Money.get(playerId) < price) {
            throw new Wrong("You do not have " + priceFormat + " to buy "
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
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + meta.token));
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                .fromLegacyText(ChatColor.GREEN + "Confirm\nBuy "
                                                + buyClaimBlocks + " for " + priceFormat + ".")));
        cb.append("  ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                .fromLegacyText(ChatColor.RED + "Cancel purchase.")));
        player.spigot().sendMessage(cb.create());
        return true;
    }

    private boolean confirmCommand(Player player, String[] args) throws Wrong {
        if (args.length != 2) return true;
        final UUID playerId = player.getUniqueId();
        // BuyClaimBlocks confirm
        BuyClaimBlocks meta = plugin.getMetadata(player, plugin.META_BUY, BuyClaimBlocks.class)
            .orElse(null);
        if (meta != null) {
            plugin.removeMetadata(player, plugin.META_BUY);
            Claim claim = plugin.getClaimById(meta.claimId);
            if (claim == null) return true;
            if (!args[1].equals(meta.token)) {
                throw new Wrong("Purchase expired");
            }
            if (!Money.take(playerId, meta.price, plugin, "Buy " + meta.amount + " claim blocks")) {
                throw new Wrong("You cannot afford " + Money.format(meta.price));
            }
            claim.setBlocks(claim.getBlocks() + meta.amount);
            claim.saveToDatabase();
            if (claim.getBoolSetting(Claim.Setting.AUTOGROW)) {
                player.sendMessage(ChatColor.WHITE + "Added " + meta.amount
                                   + " blocks to this claim. It will grow automatically.");
            } else {
                player.sendMessage(ChatColor.WHITE + "Added " + meta.amount
                                   + " blocks to this claim."
                                   + " Grow it manually or enable \"autogrow\" in the settings.");
            }
        }
        // AbandonClaim confirm
        int claimId = plugin.getMetadata(player, plugin.META_ABANDON, Integer.class).orElse(-1);
        if (claimId >= 0) {
            plugin.removeMetadata(player, plugin.META_ABANDON);
            Claim claim = plugin.findClaimWithId(claimId);
            if (claim == null || !claim.isOwner(player) || !args[1].equals("" + claimId)) {
                throw new Wrong("Claim removal expired");
            }
            plugin.deleteClaim(claim);
            player.sendMessage(ChatColor.YELLOW + "Claim removed");
        }
        // NewClaim confirm
        NewClaimMeta ncmeta = plugin.getMetadata(player, plugin.META_NEWCLAIM, NewClaimMeta.class)
            .orElse(null);
        if (ncmeta != null) {
            plugin.removeMetadata(player, plugin.META_NEWCLAIM);
            if (!args[1].equals(ncmeta.token)) return true;
            if (!plugin.getHomeWorlds().contains(ncmeta.world)) return true;
            WorldSettings settings = plugin.getWorldSettings().get(ncmeta.world);
            for (Claim claimInWorld : plugin.findClaimsInWorld(ncmeta.world)) {
                // This whole check is a repeat from the new claim command.
                if (claimInWorld.getArea().overlaps(ncmeta.area)) {
                    throw new Wrong("Your claim would overlap an existing claim.");
                }
            }
            if (ncmeta.price >= 0.01 && !Money.take(playerId, ncmeta.price, plugin, "Make new claim in " + plugin.worldDisplayName(ncmeta.world))) {
                throw new Wrong("You cannot afford " + Money.format(ncmeta.price) + "!");
            }
            Claim claim = new Claim(plugin, playerId, ncmeta.world, ncmeta.area);
            claim.saveToDatabase();
            plugin.getClaims().add(claim);
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append("Claim created!  ").color(ChatColor.WHITE);
            cb.append("[View]").color(ChatColor.GREEN);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim"));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                    .fromLegacyText(ChatColor.GREEN + "/claim\n"
                                                    + ChatColor.WHITE + ChatColor.ITALIC
                                                    + "The command to access all your claims.")));
            player.spigot().sendMessage(cb.create());
            plugin.highlightClaim(claim, player);
        }
        return true;
    }

    private boolean cancelCommand(Player player, String[] args) {
        if (args.length != 1) return false;
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

    private boolean addCommand(Player player, String[] args) throws Wrong {
        if (args.length != 2) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new Wrong("Stand in the claim to which you want to add members.");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("You are not the owner of this claim.");
        }
        String targetName = args[1];
        UUID targetId = PlayerCache.uuidForName(targetName);
        if (targetId == null) {
            throw new Wrong("Player not found: " + targetName + ".");
        }
        if (claim.canBuild(targetId)) {
            throw new Wrong("Player is already a member of this claim.");
        }
        ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.MEMBER, targetId);
        plugin.getDb().insertAsync(ct, null);
        claim.getMembers().add(targetId);
        if (claim.getVisitors().contains(targetId)) {
            claim.getVisitors().remove(targetId);
            plugin.getDb().find(ClaimTrust.class)
                .eq("claim_id", claim.getId())
                .eq("trustee", targetId).delete();
        }
        player.sendMessage(ChatColor.GREEN + "Member added: " + targetName + ".");
        return true;
    }

    private boolean inviteCommand(Player player, String[] args) throws Wrong {
        if (args.length != 2) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new Wrong("Stand in the claim to which you want to invite people.");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("You are not the owner of this claim.");
        }
        String targetName = args[1];
        UUID targetId = PlayerCache.uuidForName(targetName);
        if (targetId == null) {
            throw new Wrong("Player not found: " + targetName + ".");
        }
        if (claim.canVisit(targetId)) {
            throw new Wrong("Player is already invited to this claim.");
        }
        ClaimTrust ct = new ClaimTrust(claim, ClaimTrust.Type.VISIT, targetId);
        plugin.getDb().insertAsync(ct, null);
        claim.getVisitors().add(targetId);
        player.sendMessage(ChatColor.GREEN + "Player invited: " + targetName + ".");
        return true;
    }

    private boolean removeCommand(Player player, String[] args) throws Wrong {
        if (args.length != 2) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new Wrong("Stand in the claim to which you want to invite people");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("You are not the owner of this claim");
        }
        String targetName = args[1];
        UUID targetId = PlayerCache.uuidForName(targetName);
        if (targetId == null) {
            throw new Wrong("Player not found: " + targetName);
        }
        if (claim.getMembers().contains(targetId)) {
            claim.getMembers().remove(targetId);
            plugin.getDb().find(ClaimTrust.class)
                .eq("claim_id", claim.getId())
                .eq("trustee", targetId)
                .delete();
            player.sendMessage(ChatColor.YELLOW + targetName + " may no longer build");
        } else if (claim.getVisitors().contains(targetId)) {
            claim.getVisitors().remove(targetId);
            plugin.getDb().find(ClaimTrust.class)
                .eq("claim_id", claim.getId())
                .eq("trustee", targetId)
                .delete();
            player.sendMessage(ChatColor.YELLOW + targetName + " may no longer visit");
        } else {
            throw new Wrong(targetName + " has no permission in this claim");
        }
        return true;
    }

    private boolean setCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1 && args.length != 3) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new Wrong("Stand in the claim you wish to edit");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("Only the claim owner can do this");
        }
        if (args.length == 1) {
            showClaimSettings(claim, player);
            return true;
        }
        Claim.Setting setting;
        try {
            setting = Claim.Setting.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new Wrong("Unknown claim setting: " + args[1]);
        }
        if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) {
            throw new Wrong("Unknown claim setting: " + args[1]);
        }
        Object value;
        switch (args[2]) {
        case "on": case "true": case "enabled": value = true; break;
        case "off": case "false": case "disabled": value = false; break;
        default:
            throw new Wrong("Unknown settings value: " + args[2]);
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
        return true;
    }

    private void showClaimSettings(Claim claim, Player player) {
        player.sendMessage("");
        BaseComponent[] txt;
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Settings");
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
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/claim set " + key + " off"));
                txt = TextComponent
                    .fromLegacyText(ChatColor.RED + "Disable " + setting.displayName);
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            } else if (value == Boolean.FALSE) {
                cb.append("[ON]").color(ChatColor.GRAY);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/claim set " + key + " on"));
                txt = TextComponent
                    .fromLegacyText(ChatColor.GREEN + "Enable " + setting.displayName);
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
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

    private boolean growCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1) return false;
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        Claim claim = plugin.findNearestOwnedClaim(player);
        if (claim == null) {
            throw new Wrong("You don't have a claim nearby");
        }
        if (claim.getArea().contains(x, z)) {
            plugin.highlightClaim(claim, player);
            throw new Wrong("Stand where you want the claim to grow to");
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
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim buy " + needed));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                    .fromLegacyText(ChatColor.GRAY + "/claim buy "
                                                    + ChatColor.ITALIC + needed
                                                    + "\n" + ChatColor.WHITE + ChatColor.ITALIC
                                                    + "Buy more " + needed + " claim blocks for "
                                                    + formatMoney + ".")));
            player.spigot().sendMessage(cb.create());
            return true;
        }
        for (Claim other : plugin.getClaims()) {
            if (other != claim && other.isInWorld(claim.getWorld())
                && other.getArea().overlaps(newArea)) {
                throw new Wrong("Your claim would connect with another claim");
            }
        }
        claim.setArea(newArea);
        claim.saveToDatabase();
        player.sendMessage(ChatColor.BLUE + "Grew your claim to where you are standing");
        plugin.highlightClaim(claim, player);
        return true;
    }

    private boolean shrinkCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1) return false;
        Location playerLocation = player.getLocation();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();
        Claim claim = plugin.getClaimAt(playerLocation);
        if (claim == null) {
            throw new Wrong("Stand in the claim you wish to shrink");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("You can only shrink your own claims");
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

    private boolean abandonCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1) return false;
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) {
            throw new Wrong("There is no claim here.");
        }
        if (!claim.isOwner(player)) {
            throw new Wrong("This claim does not belong to you.");
        }
        long life = System.currentTimeMillis() - claim.getCreated();
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        long cooldown = settings.claimAbandonCooldown * 1000L * 60L;
        if (life < cooldown) {
            long wait = (cooldown - life) / (1000L * 60L);
            if (wait <= 1) {
                throw new Wrong("You must wait one more minute to abandon this claim.");
            } else {
                throw new Wrong("You must wait " + wait + " more minutes to abandon this claim.");
            }
        }
        plugin.setMetadata(player, plugin.META_ABANDON, claim.getId());
        BaseComponent[] txt = TextComponent
            .fromLegacyText("Really delete this claim? All claim blocks will be lost.",
                            ChatColor.WHITE);
        player.spigot().sendMessage(txt);
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("This cannot be undone! ").color(ChatColor.RED);
        cb.append("[Confirm]").color(ChatColor.YELLOW);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim confirm " + claim.getId()));
        txt = TextComponent.fromLegacyText(ChatColor.YELLOW + "Confirm claim removal.");
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        cb.append(" ").reset();
        cb.append("[Cancel]").color(ChatColor.RED);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim cancel"));
        txt = TextComponent.fromLegacyText(ChatColor.RED + "Cancel claim removal.");
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        player.spigot().sendMessage(cb.create());
        return true;
    }

    @Override
    public List<String> onTabComplete(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        String cmd = args.length == 0 ? "" : args[0];
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return complete(args[0], COMMANDS.stream());
        } else if (args.length > 1) {
            switch (cmd) {
            case "new": case "port": case "set": case "grow": case "shrink":
                return Collections.emptyList();
            case "buy":
                if (arg.isEmpty()) return Arrays.asList("10", "100", "1000", "10000");
                try {
                    int amount = Integer.parseInt(arg);
                    return Arrays.asList("" + amount, "" + amount + "0");
                } catch (NumberFormatException nfe) {
                    return Collections.emptyList();
                }
            default:
                return null;
            }
        }
        return null;
    }

    @Override
    public void commandHelp(Player player) {
        commandHelp(player, "/claim", new String[]{}, "View your claim options.");
        commandHelp(player, "/claim new", new String[]{}, "Make a claim here.");
        commandHelp(player, "/claim list", new String[]{}, "List your claims.");
        commandHelp(player, "/claim info", new String[]{}, "View claim info.");
        commandHelp(player, "/claim port", new String[]{"<id>"}, "Teleport to claim.");
        commandHelp(player, "/claim buy", new String[]{}, "Buy new claim blocks.");
        commandHelp(player, "/claim add", new String[]{"<player>"}, "Add a member.");
        commandHelp(player, "/claim invite", new String[]{"<player>"}, "Add visitor.");
        commandHelp(player, "/claim remove",  new String[]{"<player>"},
                    "Remove member or visitor.");
        commandHelp(player, "/claim set", new String[]{}, "View or edit claim settings.");
        commandHelp(player, "/claim grow", new String[]{}, "Grow claim to your location.");
        commandHelp(player, "/claim shrink", new String[]{}, "Grow claim to your location.");
        commandHelp(player, "/claim abandon", new String[]{}, "Abandon your claim.");
    }

    public void printClaimInfo(Player player, Claim claim) {
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim Info");
        player.spigot().sendMessage(cb.create());
        cb = new ComponentBuilder("")
            .append("Owner ").color(ChatColor.GRAY)
            .append(claim.getOwnerName()).color(ChatColor.WHITE);
        player.spigot().sendMessage(cb.create());
        cb = new ComponentBuilder("").append("Location ").color(ChatColor.GRAY)
            .append(plugin.worldDisplayName(claim.getWorld()) + " " + claim.centerX)
            .color(ChatColor.WHITE)
            .append(",").color(ChatColor.GRAY)
            .append("" + claim.centerY).color(ChatColor.WHITE);
        player.spigot().sendMessage(cb.create());
        cb = new ComponentBuilder("").append("Size ").color(ChatColor.GRAY)
            .append("" + claim.getArea().width()).color(ChatColor.WHITE)
            .append("x").color(ChatColor.GRAY)
            .append("" + claim.getArea().height()).color(ChatColor.WHITE)
            .append(" => ").color(ChatColor.GRAY)
            .append("" + claim.getArea().size()).color(ChatColor.WHITE)
            .append(" / ").color(ChatColor.GRAY)
            .append("" + claim.getBlocks()).color(ChatColor.WHITE)
            .append(" blocks").color(ChatColor.GRAY);
        player.spigot().sendMessage(cb.create());
        // Members and Visitors
        for (int i = 0; i < 2; i += 1) {
            List<UUID> ids = null;
            String key = null;
            switch (i) {
            case 0: key = "Members"; ids = claim.getMembers(); break;
            case 1: key = "Visitors"; ids = claim.getVisitors(); break;
            default: continue;
            }
            if (ids.isEmpty()) continue;
            cb = new ComponentBuilder("");
            cb.append(key).color(ChatColor.GRAY);
            for (UUID id : ids) {
                cb.append(" ").reset();
                cb.append(PlayerCache.nameForUuid(id)).color(ChatColor.WHITE);
            }
            player.spigot().sendMessage(cb.create());
        }
        // Subclaims
        List<Subclaim> subclaims = claim.getSubclaims(player.getWorld());
        cb = new ComponentBuilder("Subclaims").color(ChatColor.GRAY)
            .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/subclaim "))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent
                                  .fromLegacyText(ChatColor.AQUA + "/subclaim")))
            .append(" ")
            .append("" + subclaims.size()).color(ChatColor.WHITE);
        player.sendMessage(cb.create());
        // Settings
        cb = new ComponentBuilder("");
        cb.append("Settings").color(ChatColor.GRAY);
        for (Claim.Setting setting : Claim.Setting.values()) {
            if (setting.isAdminOnly() && !Claim.ownsAdminClaims(player)) continue;
            Object value = claim.getSetting(setting);
            if (value == null) continue;
            if (value == setting.defaultValue) continue;
            cb.append(" ").reset();
            cb.append(setting.name().toLowerCase()).color(setting.isAdminOnly()
                                                          ? ChatColor.RED
                                                          : ChatColor.WHITE);
            cb.append("=").color(ChatColor.GRAY);
            String valueString;
            ChatColor valueColor;
            if (value == Boolean.TRUE) {
                cb.append("on").color(ChatColor.BLUE);
            } else if (value == Boolean.FALSE) {
                cb.append("off").color(ChatColor.RED);
            } else {
                valueColor = ChatColor.GRAY; valueString = value.toString();
                cb.append(value.toString()).color(ChatColor.GRAY);
            }
        }
        player.spigot().sendMessage(cb.create());
    }

    public void sendClaimButtons(Player player, Claim claim) {
        // Buttons
        Location playerLocation = player.getLocation();
        UUID playerId = player.getUniqueId();
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("General").color(ChatColor.GRAY);
        final ChatColor buttonColor = ChatColor.GREEN;
        cb.append("  ").append("[Info]").color(buttonColor);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim info"));
        BaseComponent[] txt = TextComponent
            .fromLegacyText(buttonColor + "/claim info\n"
                            + ChatColor.WHITE + ChatColor.ITALIC + "Get claim info.");
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        if (claim.canVisit(player)) {
            cb.append("  ").append("[Port]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/claim port " + claim.getId()));
            txt = TextComponent.fromLegacyText(buttonColor + "Teleport to this claim.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        }
        if (claim.isOwner(player)) {
            cb.append("  ").append("[Abandon]").color(ChatColor.DARK_RED);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim abandon"));
            txt = TextComponent
                .fromLegacyText(ChatColor.DARK_RED + "/claim abandon\n"
                                + ChatColor.WHITE + ChatColor.ITALIC + "Abandon this claim.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        }
        player.spigot().sendMessage(cb.create());
        WorldSettings settings = plugin.getWorldSettings().get(claim.getWorld());
        if (claim.isOwner(player) && claim.contains(playerLocation)) {
            cb = new ComponentBuilder("");
            cb.append("Manage").color(ChatColor.GRAY);
            cb.append("  ").append("[Buy]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim buy "));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim buy " + ChatColor.ITALIC + "<amount>\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Add some claim blocks to this claim. One claim block costs "
                                + Money.format(settings.claimBlockCost) + ".");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append("  ").append("[Settings]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim set"));
            txt = TextComponent.fromLegacyText(buttonColor + "/claim set\n"
                                               + ChatColor.WHITE + ChatColor.ITALIC
                                               + "View or change claim settings.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append("  ").append("[Grow]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim grow"));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim grow\n" + ChatColor.WHITE + ChatColor.ITALIC
                                + "Grow this claim to your current location.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append("  ").append("[Shrink]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim shrink"));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim shrink\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Reduce this claim's size so that the nearest corner"
                                + " snaps to your current location.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            player.spigot().sendMessage(cb.create());
            cb = new ComponentBuilder("");
            cb.append("Friends").color(ChatColor.GRAY);
            cb.append("  ").append("[Add]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim add "));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim add " + ChatColor.ITALIC + "<player>\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Trust some player to build in this claim.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append("  ").append("[Invite]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim invite "));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim invite " + ChatColor.ITALIC + "<player>\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Trust some player to visit your claim."
                                + " They will be able to open doors and such.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            cb.append("  ").append("[Remove]").color(buttonColor);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim remove "));
            txt = TextComponent
                .fromLegacyText(buttonColor + "/claim remove " + ChatColor.ITALIC + "<player>\n"
                                + ChatColor.WHITE + ChatColor.ITALIC
                                + "Remove someone's trust from this claim.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            player.spigot().sendMessage(cb.create());
        }
    }

    private boolean listCommand(Player player, String[] args) throws Wrong {
        if (args.length != 1) return false;
        UUID playerId = player.getUniqueId();
        player.sendMessage("");
        ComponentBuilder cb = new ComponentBuilder("");
        frame(cb, "Claim List");
        player.spigot().sendMessage(cb.create());
        List<Claim> playerClaims = plugin.findClaims(player);
        int ci = 0;
        cb = new ComponentBuilder("");
        cb.append("Owned").color(ChatColor.GRAY);
        for (Claim claim : playerClaims) {
            cb.append("  ");
            cb.append("[" + plugin.worldDisplayName(claim.getWorld()) + "]")
                .color(ChatColor.BLUE);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/claim info " + claim.getId()));
            BaseComponent[] txt = TextComponent
                .fromLegacyText(ChatColor.BLUE + "Your claim in "
                                + plugin.worldDisplayName(claim.getWorld()));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
        }
        player.spigot().sendMessage(cb.create());
        playerClaims = new ArrayList<>();
        for (Claim claim : plugin.getClaims()) {
            if (!claim.isOwner(player) && !claim.isHidden() && claim.canVisit(player)) {
                playerClaims.add(claim);
            }
        }
        if (!playerClaims.isEmpty()) {
            cb = new ComponentBuilder("");
            cb.append("Invited").color(ChatColor.GRAY);
            for (Claim claim : playerClaims) {
                cb.append("  ");
                cb.append("[" + claim.getOwnerName() + "]").color(ChatColor.GREEN);
                cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/claim info " + claim.getId()));
                BaseComponent[] txt = TextComponent
                    .fromLegacyText(ChatColor.GREEN + claim.getOwnerName()
                                    + " invited you to this claim.");
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            }
            player.spigot().sendMessage(cb.create());
        }
        if (plugin.isHomeWorld(player.getWorld())
            && plugin.findClaimsInWorld(playerId, player.getWorld().getName()).isEmpty()) {
            cb = new ComponentBuilder("");
            cb.append("Make one ").color(ChatColor.GRAY);
            cb.append("  ").append("[New]").color(ChatColor.GOLD);
            cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim new"));
            BaseComponent[] txt = TextComponent
                .fromLegacyText(ChatColor.GOLD + "/claim new\n"
                                + ChatColor.WHITE + ChatColor.ITALIC + "Make a claim right here.");
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, txt));
            player.spigot().sendMessage(cb.create());
        }
        player.sendMessage("");
        return true;
    }
}
