package com.cavetale.home;

import com.cavetale.mytems.Mytems;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class ClaimToolListener implements Listener {
    private final HomePlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        boolean left = false;
        boolean right = false;
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
            left = true;
            break;
        case RIGHT_CLICK_BLOCK:
            right = true;
            break;
        default:
            return;
        }
        if (!Mytems.CLAIM_TOOL.isItem(event.getItem())) return;
        final Player player = event.getPlayer();
        if (plugin.sessions.of(player).onPlayerInteract(event)) return;
        if (left) {
            onLeftClick(player, event.getClickedBlock());
        } else if (right) {
            onRightClick(player, event.getClickedBlock());
        }
    }

    private void onLeftClick(Player player, Block block) {
        final Session session = plugin.getSessions().of(player);
        if (session.getClaimTool() != null) {
            session.setClaimTool(null);
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Claim resizing cancelled", YELLOW)));
            return;
        }
        final Claim claim = plugin.getClaimAt(block);
        if (claim == null) {
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("There is no claim here.", RED)));
            for (Claim nearby : plugin.findNearbyClaims(player.getLocation(), 64)) {
                plugin.highlightClaim(nearby, player);
            }
            return;
        }
        // Highlight
        plugin.highlightClaim(claim, player);
        for (Subclaim subclaim : claim.getSubclaims(player.getWorld())) {
            plugin.highlightSubclaim(subclaim, player);
        }
        // Show info
        final Subclaim subclaim = claim.getSubclaimAt(block);
        if (subclaim != null) {
            plugin.getSubclaimCommand().showSubclaimInfo(player, subclaim);
        } else {
            plugin.getClaimCommand().showClaimInfo(player, claim);
        }
    }

    /**
     * Here we handle any right click with the claim tool at a block.
     * The claim may be null.
     */
    private void onRightClick(Player player, Block block) {
        final Session session = plugin.getSessions().of(player);
        final ClaimToolSession claimTool = session.getClaimTool();
        if (claimTool == null) {
            onRightClickClaimFirst(player, session, block);
        } else {
            final Claim claim = claimTool.getClaim();
            if (claim == null || !claim.isOwner(player) || !claim.isInWorld(block.getWorld().getName())) {
                session.setClaimTool(null);
                onRightClickClaimFirst(player, session, block);
                return;
            }
            // At this point we have confirmed that the previous click
            // belonged to a valid claim and that the player is still
            // owner and in the same world.
            final Area newArea = claim.getArea().resizeToContain(claimTool.getClickedCorner(), block.getX(), block.getZ());
            if (newArea == null) {
                player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Wrong direction. Resize cancelled", RED)));
                plugin.highlightClaim(claim, player);
                session.setClaimTool(null);
                return;
            }
            if (newArea.equals(claim.getArea())) {
                session.setClaimTool(null);
                onRightClickClaimFirst(player, session, block);
                return;
            }
            if (claim.tryToResizeFullAuto(player, newArea)) {
                player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Claim resized", GREEN)));
                session.setClaimTool(null);
                plugin.highlightClaim(claim, player);
                // Finish
            }
        }
    }

    /**
     * Player clicks with the claim tool while no claim was previously
     * selected.  Here we check if the player clicks a valid claim
     * that they may edit and store the info in the session for the
     * second click which is expected later.
     *
     * This method was outsourced because it is called from multiple
     * locations in onRightClick.
     */
    private void onRightClickClaimFirst(Player player, Session session, Block block) {
        final Claim claim = plugin.getClaimAt(block);
        if (claim == null) {
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("There is no claim here.", RED)));
            for (Claim nearby : plugin.findNearbyClaims(player.getLocation(), 64)) {
                plugin.highlightClaim(nearby, player);
            }
            return;
        }
        if (!claim.isOwner(player)) {
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("You cannot resize this claim.", RED)));
            plugin.highlightClaim(claim, player);
            return;
        }
        final BlockFace blockFace = claim.getArea().getClickedFace(block.getX(), block.getZ());
        if (blockFace == null) {
            player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Resize this claim by clicking one of its corners.", RED)));
            plugin.highlightClaim(claim, player);
            return;
        }
        session.setClaimTool(new ClaimToolSession(claim.getId(), blockFace));
        player.sendMessage(textOfChildren(Mytems.CLAIM_TOOL, text("Resizing Claim " + toCamelCase(" ", blockFace) + " ", YELLOW),
                                          Mytems.MOUSE_RIGHT,
                                          text(" move border ", GREEN),
                                          Mytems.MOUSE_LEFT,
                                          text(" cancel", RED)));
        plugin.highlightClaim(claim, player);
    }
}
