package com.cavetale.home;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
final class WildTask {
    protected final HomePlugin plugin;
    protected final World world;
    protected final RemotePlayer player;
    protected int attempts = 0;
    protected static final long NANOS = 1000000000L;
    protected int blockX;
    protected int blockZ;
    private static final Map<UUID, Long> LAST_USES = new HashMap<>();

    public void withCooldown() {
        long now = System.nanoTime() / NANOS;
        long last = LAST_USES.getOrDefault(player.getUniqueId(), -1L);
        if (last >= 0) {
            long since = now - last;
            long cooldown = (long) Globals.WILD_COOLDOWN;
            long remain = cooldown - since;
            if (remain > 0) {
                player.sendMessage(text("Please wait " + remain + " more seconds", RED));
                return;
            }
        }
        LAST_USES.put(player.getUniqueId(), now);
        withoutCooldown();
    }

    public void withoutCooldown() {
        player.sendMessage(text("Looking for an unclaimed place to build...", YELLOW));
        plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
    }

    private void findPlaceToBuild() {
        // Attempts
        long now = System.nanoTime() / NANOS;
        LAST_USES.put(player.getUniqueId(), now);
        if (attempts++ > 50) {
            player.sendMessage(text("Could not find a place to build. Please try again", RED));
            return;
        }
        // Determine center and border
        if (!plugin.isLocalHomeWorld(world)) {
            plugin.getLogger().warning("WildTask: World not found: " + world.getName());
            player.sendMessage(text("Something went wrong. Please contact an administrator", RED));
        }
        // Borders
        int margin = Globals.CLAIM_MARGIN;
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int size = (int) Math.min(50000.0, border.getSize()) - margin * 2;
        if (size < 0) {
            throw new RuntimeException("World border makes no sense: " + size);
        }
        List<Claim> worldClaims = plugin.findClaimsInWorld(world.getName());
        boolean foundSpot = false;
        for (int i = 0; i < 100; i += 1) {
            if (findUnclaimedSpot(worldClaims, cx, cz, size, margin)) {
                foundSpot = true;
                break;
            }
        }
        if (foundSpot) {
            world.getChunkAtAsync(blockX >> 4, blockZ >> 4, (Consumer<Chunk>) this::onChunkLoaded);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
        }
    }

    private boolean findUnclaimedSpot(List<Claim> worldClaims,
                              final int cx, final int cz,
                              final int size, final int margin) {
        int x = cx - size / 2 + plugin.random.nextInt(size);
        int z = cz - size / 2 + plugin.random.nextInt(size);
        for (Claim claim : worldClaims) {
            if (claim.getArea().isWithin(x, z, margin)) {
                return false;
            }
        }
        blockX = x;
        blockZ = z;
        return true;
    }

    private void onChunkLoaded(Chunk chunk) {
        Block block = world.getHighestBlockAt(blockX, blockZ);
        for (int i = 0; i < 255; i += 1) {
            if (!block.isSolid() && !block.isLiquid()) break;
            block = block.getRelative(0, 1, 0);
        }
        Block below = block.getRelative(0, -1, 0);
        if (!below.getType().isSolid()) {
            plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
            return;
        }
        Location location = block.getLocation().add(0.5, 1.0, 0.5);
        // Teleport, notify, and set cooldown
        player.bring(plugin, location, player2 -> {
                if (player2 == null) return;
                Component claimNewTooltip = Component
                    .join(JoinConfiguration.separator(newline()),
                          text("/claim new", GREEN),
                          text("Create a claim and set a home"),
                          text("at this location so you can"),
                          text("build and return any time."))
                    .color(GRAY);
                Component wildTooltip = Component
                    .join(JoinConfiguration.separator(newline()),
                          text("/wild", GREEN),
                          text("Find another place to build"))
                    .color(GRAY);
                ComponentLike message = text().color(WHITE)
                    .append(text("Found you a place to build."))
                    .append(newline())
                    .append(text("Do you like it? ", GRAY))
                    .append(text("[Claim]", GREEN)
                            .clickEvent(ClickEvent.suggestCommand("/claim new"))
                            .hoverEvent(HoverEvent.showText(claimNewTooltip)))
                    .append(text(" or "))
                    .append(text("[Try Again]", AQUA)
                            .clickEvent(ClickEvent.suggestCommand("/wild"))
                            .hoverEvent(HoverEvent.showText(wildTooltip)));
                player2.sendMessage(message);
                PluginPlayerEvent.Name.USE_WILD.call(plugin, player2);
            });
    }
}
