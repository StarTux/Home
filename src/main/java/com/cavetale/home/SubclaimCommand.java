package com.cavetale.home;

import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

@RequiredArgsConstructor
public final class SubclaimCommand implements TabExecutor {
    private final HomePlugin plugin;
    private CommandNode rootNode;

    public void enable() {
        rootNode = new CommandNode("subclaim")
            .description("Subclaims command interface");
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
        plugin.getCommand("subclaim").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.call(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.complete(sender, command, alias, args);
    }

    boolean list(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = requireClaim(player);
        if (!claim.canVisit(player)) {
            throw new CommandWarn("No subclaims here");
        }
        List<Subclaim> subclaims = claim.getSubclaims();
        if (subclaims.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "There are no subclaims.");
            return true;
        }
        player.sendMessage(ChatColor.AQUA + (subclaims.size() == 1
                                             ? "There is 1 subclaim:"
                                             : "There are " + subclaims.size() + " subclaims:"));
        int i = 0;
        for (Subclaim subclaim : subclaims) {
            int index = ++i;
            player.sendMessage("  " + ChatColor.DARK_AQUA + index + ") " + subclaim.getListInfo());
            if (subclaim.isInWorld(player.getWorld())) {
                plugin.highlightSubclaim(subclaim, player);
            }
        }
        return true;
    }

    boolean create(Player player, String[] args) {
        if (args.length != 0) return false;
        Claim claim = requireClaim(player);
        if (!claim.isOwner(player)) {
            throw new CommandWarn("You do not own this claim!");
        }
        plugin.sessions.of(player).setPlayerInteractCallback(event -> createPickBlock(player, player.getWorld(), event, claim, null));
        player.sendMessage(ChatColor.GREEN + "Click a corner block for the new subclaim");
        player.sendTitle("", ChatColor.GREEN + "Click a corner block");
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
            player.sendMessage(ChatColor.RED + "You left the claim. Subclaim creation aborted.");
            player.sendTitle("", ChatColor.RED + "Subclaim creation aborted");
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            return true;
        }
        Subclaim existing = claim.getSubclaimAt(block);
        if (existing != null) {
            player.sendMessage(ChatColor.RED + "There's already a subclaim here! Subclaim creation aborted.");
            player.sendTitle("", ChatColor.RED + "Subclaim creation aborted");
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            plugin.highlightSubclaim(existing, player);
            return true;
        }
        if (blockA == null) {
            plugin.sessions.of(player).setPlayerInteractCallback(event2 -> createPickBlock(player, world, event2, claim, block));
            player.sendMessage(ChatColor.GREEN + "Now click another corner block of the new subclaim");
            player.sendTitle("", ChatColor.GREEN + "Click another corner block");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 2.0f);
            plugin.highlightSubclaim(new Area(block.getX(), block.getZ(), block.getX(), block.getZ()), player);
            return true;
        }
        Area area = new Area(Math.min(block.getX(), blockA.getX()),
                             Math.min(block.getZ(), blockA.getZ()),
                             Math.max(block.getX(), blockA.getX()),
                             Math.max(block.getZ(), blockA.getZ()));
        if (!claim.getArea().contains(area)) {
            player.sendMessage(ChatColor.RED + "The subclaim would exceed the containing claim. Aborting");
            player.sendTitle("", ChatColor.RED + "Subclaim creation aborted");
            plugin.sessions.of(player).setPlayerInteractCallback(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
            return true;
        }
        for (Subclaim other : claim.getSubclaims(world)) {
            if (other.getArea().overlaps(area)) {
                player.sendMessage(ChatColor.RED + "The subclaim would overlap with an existing subclaim. Aborting.");
                player.sendTitle("", ChatColor.RED + "Subclaim creation aborted");
                plugin.sessions.of(player).setPlayerInteractCallback(null);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.2f, 0.5f);
                plugin.highlightSubclaim(other, player);
                return true;
            }
        }
        Subclaim subclaim = new Subclaim(plugin, claim, world, area);
        claim.addSubclaim(subclaim);
        subclaim.saveToDatabase();
        plugin.sessions.of(player).setPlayerInteractCallback(null);
        plugin.highlightSubclaim(subclaim, player);
        player.sendMessage(ChatColor.GREEN + "Subclaim created!");
        player.sendTitle("", ChatColor.GREEN + "Subclaim created");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        return true;
    }

    boolean info(Player player, String[] args) {
        if (args.length != 0) return false;
        Subclaim subclaim = requireSubclaim(player);
        if (!subclaim.getParent().canVisit(player)) throw new CommandWarn("No subclaim here");
        player.sendMessage("");
        player.sendMessage("" + ChatColor.AQUA + ChatColor.BOLD + "Subclaim Info");
        player.sendMessage("  " + ChatColor.AQUA + "Area " + ChatColor.WHITE + subclaim.getArea());
        player.sendMessage("  " + ChatColor.AQUA + "Parent claim " + ChatColor.WHITE + subclaim.getParent().getOwnerName());
        for (Map.Entry<Subclaim.Trust, Set<UUID>> entry : subclaim.getTrustedMap().entrySet()) {
            Set<UUID> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;
            Subclaim.Trust trust = entry.getKey();
            player.sendMessage("  " + ChatColor.AQUA + trust.displayName + " Trust " + ChatColor.WHITE + set.stream()
                               .map(Subclaim::cachedPlayerName)
                               .collect(Collectors.joining(", ")));
        }
        player.sendMessage("");
        plugin.highlightSubclaim(subclaim, player);
        return true;
    }

    boolean trust(Player player, String[] args) {
        if (args.length != 2) return false;
        Subclaim subclaim = requireSubclaim(player);
        Subclaim.Trust playerTrust = subclaim.getTrust(player);
        if (!playerTrust.entails(Subclaim.Trust.MANAGER)) {
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
        Subclaim.Trust trust;
        try {
            trust = Subclaim.Trust.valueOf(trustName.toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Invalid trust: " + trustName);
        }
        subclaim.setTrust(uuid, trust);
        subclaim.saveToDatabase();
        player.sendMessage(ChatColor.AQUA + "Player trusted: " + playerName + " (" + trust.displayName + ")");
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
            return Stream.of(Subclaim.Trust.values())
                .map(t -> t.name().toLowerCase())
                .filter(s -> s.startsWith(arg))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    boolean untrust(Player player, String[] args) {
        if (args.length != 1) return false;
        Subclaim subclaim = requireSubclaim(player);
        Subclaim.Trust playerTrust = subclaim.getTrust(player);
        if (!playerTrust.entails(Subclaim.Trust.OWNER)) {
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
        Subclaim.Trust oldTrust = subclaim.removeTrust(uuid);
        if (oldTrust != null) {
            subclaim.saveToDatabase();
            player.sendMessage(ChatColor.AQUA + "Player trust reset: " + playerName + " (was " + oldTrust.displayName + ")");
        } else {
            throw new CommandWarn(playerName + " was not trusted");
        }
        return true;
    }

    List<String> untrustComplete(CommandContext context, CommandNode node, String[] args) {
        if (args.length == 0) return null;
        if (args.length == 1) {
            if (!context.isPlayer()) return null;
            Location loc = context.player.getLocation();
            Claim claim = plugin.getClaimAt(loc);
            if (claim == null || !claim.isOwner(context.player)) return Collections.emptyList();
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
        if (!subclaim.getParent().isOwner(player)) {
            throw new CommandWarn("You do not own this claim");
        }
        plugin.sessions.of(player).requireConfirmation("Delete subclaim: " + subclaim.getListInfo(), () -> {
                Subclaim subclaim2 = requireSubclaim(player);
                if (subclaim2 != subclaim) {
                    throw new CommandWarn("You left the subclaim!");
                }
                if (!subclaim.getParent().isOwner(player)) return; // Safety first
                if (!subclaim.getParent().removeSubclaim(subclaim)) return; // ???
                plugin.db.delete(subclaim.toSQLRow());
                player.sendMessage(ChatColor.GREEN + "Subclaim deleted: " + subclaim.getListInfo());
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
