package com.cavetale.home;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.mytems.Mytems;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class SubclaimCommand extends AbstractCommand<HomePlugin> {
    protected SubclaimCommand(final HomePlugin plugin) {
        super(plugin, "subclaim");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Subclaims command interface");
        rootNode.addChild("list")
            .description("List subclaims")
            .playerCaller(this::list)
            .denyTabCompletion();
        rootNode.addChild("create")
            .description("Create a subclaim")
            .playerCaller(this::create)
            .denyTabCompletion();
        rootNode.addChild("info")
            .description("Info about current subclaim")
            .playerCaller(this::info)
            .denyTabCompletion();
        rootNode.addChild("trust")
            .description("Trust players in current subclaim")
            .playerCaller(this::trust)
            .arguments("<player> [trust]")
            .completer(this::trustComplete);
        rootNode.addChild("untrust")
            .description("Reset player trust")
            .playerCaller(this::untrust)
            .completer(this::untrustComplete)
            .arguments("<player>");
        rootNode.addChild("delete")
            .description("Delete the current subclaim")
            .denyTabCompletion()
            .playerCaller(this::delete);
        rootNode.addChild("confirm")
            .hidden(true)
            .denyTabCompletion()
            .playerCaller(this::confirm);
        rootNode.addChild("cancel")
            .hidden(true)
            .denyTabCompletion()
            .playerCaller(this::cancel);
    }

    protected boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = requireClaim(player);
        if (!claim.getTrustType(player).canInteract()) {
            throw new CommandWarn("No subclaims here");
        }
        List<Subclaim> subclaims = claim.getSubclaims();
        if (subclaims.isEmpty()) {
            throw new CommandWarn("There are no subclaims");
        }
        player.sendMessage(text((subclaims.size() == 1
                                 ? "There is 1 subclaim:"
                                 : "There are " + subclaims.size() + " subclaims:"),
                                AQUA));
        int i = 0;
        for (Subclaim subclaim : subclaims) {
            int index = ++i;
            player.sendMessage(text(" " + index + ") " + subclaim.getListInfo(), DARK_AQUA));
            if (subclaim.isInWorld(player.getWorld())) {
                plugin.highlightSubclaim(subclaim, player);
            }
        }
        return true;
    }

    boolean create(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = requireClaim(player);
        if (!claim.getTrustType(player).isCoOwner()) {
            throw new CommandWarn("You do not own this claim!");
        }
        plugin.sessions.of(player).setPlayerInteractCallback(event -> createPickBlock(player, player.getWorld(), event, claim, null));
        player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Click a corner block for the new subclaim with the Claim Tool", AQUA)));
        player.showTitle(Title.title(Mytems.CLAIM_TOOL.asComponent(), text("Click a corner block", AQUA)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 2.0f);
        return true;
    }

    boolean createPickBlock(Player player, World world, PlayerInteractEvent event, Claim claim, Block blockA) {
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
        case RIGHT_CLICK_BLOCK:
            break;
        default: return false;
        }
        if (event.getHand() != EquipmentSlot.HAND) return false;
        Block block = event.getClickedBlock();
        if (block == null) return false;
        event.setCancelled(true);
        Claim clickedClaim = plugin.getClaimAt(block);
        if (clickedClaim != claim || !player.getWorld().equals(world)) {
            player.sendMessage(text("You left the claim. Subclaim creation aborted", RED));
            player.showTitle(Title.title(empty(), text("Subclaim creation aborted", RED)));
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            return true;
        }
        Subclaim existing = claim.getSubclaimAt(block);
        if (existing != null) {
            player.sendMessage(text("There's already a subclaim here! Subclaim creation aborted", RED));
            player.showTitle(Title.title(empty(), text("Subclaim creation aborted", RED)));
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            plugin.highlightSubclaim(existing, player);
            return true;
        }
        if (blockA == null) {
            plugin.sessions.of(player).setPlayerInteractCallback(event2 -> createPickBlock(player, world, event2, claim, block));
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Now click another corner block of the new subclaim", AQUA)));
            player.showTitle(Title.title(Mytems.CLAIM_TOOL.asComponent(), text("Click another corner block", AQUA)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 2.0f);
            plugin.highlightSubclaim(new Area(block.getX(), block.getZ(), block.getX(), block.getZ()), player);
            return true;
        }
        Area area = new Area(Math.min(block.getX(), blockA.getX()),
                             Math.min(block.getZ(), blockA.getZ()),
                             Math.max(block.getX(), blockA.getX()),
                             Math.max(block.getZ(), blockA.getZ()));
        if (!claim.getArea().contains(area)) {
            player.sendMessage(text("The subclaim would exceed the containing claim. Aborting", RED));
            player.showTitle(Title.title(empty(), text("Subclaim creation aborted", RED)));
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            return true;
        }
        for (Subclaim other : claim.getSubclaims(world)) {
            if (other.getArea().overlaps(area)) {
                player.sendMessage(text("The subclaim would overlap with an existing subclaim. Aborting.", RED));
                player.showTitle(Title.title(empty(), text("Subclaim creation aborted", RED)));
                plugin.sessions.of(player).setPlayerInteractCallback(null);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
                plugin.highlightSubclaim(other, player);
                return true;
            }
        }
        Subclaim subclaim = new Subclaim(plugin, claim, world, area);
        claim.getSubclaims().add(subclaim);
        subclaim.insertIntoDatabase();
        plugin.sessions.of(player).setPlayerInteractCallback(null);
        plugin.highlightSubclaim(subclaim, player);
        player.sendMessage(text("Subclaim created!", AQUA));
        player.showTitle(Title.title(empty(), text("Subclaim created", AQUA)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        PluginPlayerEvent.Name.CREATE_SUBCLAIM.call(plugin, player);
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length != 0) return false;
        Subclaim subclaim = requireSubclaim(player);
        showSubclaimInfo(player, subclaim);
        plugin.highlightSubclaim(subclaim, player);
        return true;
    }

    public void showSubclaimInfo(Player player, Subclaim subclaim) {
        player.sendMessage(empty());
        player.sendMessage(Util.frame(text("Subclaim Info", WHITE), AQUA));
        player.sendMessage(textOfChildren(text(" Area ", AQUA), text("" + subclaim.getArea(), WHITE)));
        player.sendMessage(textOfChildren(text(" Parent claim ", AQUA), text(subclaim.getParent().getOwnerName(), WHITE)));
        for (Map.Entry<SubclaimTrust, Set<UUID>> entry : subclaim.getTrustedMap().entrySet()) {
            Set<UUID> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;
            SubclaimTrust trust = entry.getKey();
            player.sendMessage(textOfChildren(text(" " + trust.displayName + " Trust ", AQUA),
                                              text(set.stream()
                                                   .map(Subclaim::cachedPlayerName)
                                                   .collect(Collectors.joining(", ")), WHITE)));
        }
        player.sendMessage(empty());
    }

    boolean trust(Player player, String[] args) {
        if (args.length != 2) return false;
        Subclaim subclaim = requireSubclaim(player);
        SubclaimTrust playerTrust = subclaim.getTrust(player);
        if (!playerTrust.entails(SubclaimTrust.CO_OWNER)) {
            throw new CommandWarn("You cannot edit this subclaim!");
        }
        String playerName = args[0];
        String trustName = args[1];
        UUID uuid = Subclaim.cachedPlayerUuid(playerName);
        if (uuid == null) {
            throw new CommandWarn("Player not found: " + playerName);
        }
        playerName = Subclaim.cachedPlayerName(uuid);
        if (subclaim.getTrust(uuid).exceeds(subclaim.getTrust(player))) {
            throw new CommandWarn("You cannot modify this player's trust level!");
        }
        SubclaimTrust trust;
        try {
            trust = SubclaimTrust.valueOf(trustName.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Invalid trust: " + trustName);
        }
        subclaim.setTrust(uuid, trust);
        player.sendMessage(text("Player trusted: " + playerName + " (" + trust.displayName + ")", AQUA));
        PluginPlayerEvent.Name.SUBCLAIM_TRUST.make(plugin, player)
            .detail(Detail.TARGET, uuid)
            .detail(Detail.NAME, trust.key)
            .callEvent();
        return true;
    }

    List<String> trustComplete(CommandContext context, CommandNode node, String[] args) {
        if (args.length == 0) return null; // impossible
        String arg = args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            return Stream.concat(Stream.of("*Everybody*"),
                                 plugin.getServer().getOnlinePlayers().stream().map(Player::getName))
                .filter(s -> s.toLowerCase().startsWith(arg))
                .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Stream.of(SubclaimTrust.values())
                .map(t -> t.name().toLowerCase())
                .filter(s -> s.startsWith(arg))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    boolean untrust(Player player, String[] args) {
        if (args.length != 1) return false;
        Subclaim subclaim = requireSubclaim(player);
        SubclaimTrust playerTrust = subclaim.getTrust(player);
        if (!playerTrust.entails(SubclaimTrust.OWNER)) {
            throw new CommandWarn("You cannot edit this subclaim!");
        }
        String playerName = args[0];
        UUID uuid = Subclaim.cachedPlayerUuid(playerName);
        if (uuid == null) {
            throw new CommandWarn("Player not found: " + playerName);
        }
        playerName = Subclaim.cachedPlayerName(uuid);
        if (subclaim.getTrust(uuid).exceeds(subclaim.getTrust(player))) {
            throw new CommandWarn("You cannot modify this player's trust level!");
        }
        SubclaimTrust oldTrust = subclaim.removeTrust(uuid);
        if (oldTrust == null) {
            throw new CommandWarn(playerName + " was not trusted");
        }
        player.sendMessage(text("Player trust removed: " + playerName + " (" + oldTrust.displayName + ")", AQUA));
        PluginPlayerEvent.Name.SUBCLAIM_UNTRUST.make(plugin, player)
            .detail(Detail.TARGET, uuid)
            .callEvent();
        return true;
    }

    List<String> untrustComplete(CommandContext context, CommandNode node, String[] args) {
        if (args.length == 0) return null;
        if (args.length == 1) {
            if (!context.isPlayer()) return null;
            Location loc = context.player.getLocation();
            Claim claim = plugin.getClaimAt(loc);
            if (claim == null || !claim.getTrustType(context.player).isCoOwner()) return Collections.emptyList();
            Subclaim subclaim = claim.getSubclaimAt(loc);
            if (subclaim == null) return Collections.emptyList();
            String arg = args[0].toLowerCase();
            return subclaim.getTrustedUuids().stream()
                .map(Subclaim::cachedPlayerName)
                .filter(Objects::nonNull)
                .filter(n -> n.toLowerCase().startsWith(arg))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    boolean delete(Player player, String[] args) {
        if (args.length != 0) return false;
        Subclaim subclaim = requireSubclaim(player);
        if (!subclaim.getParent().getTrustType(player).isCoOwner()) {
            throw new CommandWarn("You do not own this claim");
        }
        plugin.sessions.of(player).requireConfirmation("Delete subclaim: " + subclaim.getListInfo(), () -> {
                Subclaim subclaim2 = requireSubclaim(player);
                if (subclaim2 != subclaim) {
                    throw new CommandWarn("You left the subclaim!");
                }
                if (!subclaim.getParent().getTrustType(player).isCoOwner()) return; // Safety first
                if (!subclaim.getParent().removeSubclaim(subclaim)) return; // ???
                subclaim.deleteFromDatabase();
                player.sendMessage(text("Subclaim deleted: " + subclaim.getListInfo(), AQUA));
            });
        return true;
    }

    boolean confirm(Player player, String[] args) {
        plugin.sessions.of(player).confirmCommand();
        return true;
    }

    boolean cancel(Player player, String[] args) {
        plugin.sessions.of(player).cancelCommand();
        return true;
    }

    // Utility

    Claim requireClaim(Player player) {
        Claim claim = plugin.getClaimAt(player.getLocation());
        if (claim == null) throw new CommandWarn("There is no claim here!");
        return claim;
    }

    Subclaim requireSubclaim(Player player) {
        Claim claim = requireClaim(player);
        Subclaim subclaim = claim.getSubclaimAt(player.getLocation().getBlock());
        if (subclaim == null) throw new CommandWarn("There is no subclaim here!");
        return subclaim;
    }
}
