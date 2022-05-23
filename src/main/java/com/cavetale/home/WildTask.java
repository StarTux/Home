package com.cavetale.home;

import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class WildTask {
    final HomePlugin plugin;
    final World world;
    final Player player;
    int attempts = 0;
    static final long NANOS = 1000000000L;
    static final String META_COOLDOWN_WILD = "home.cooldown.wild";
    int blockX;
    int blockZ;

    protected void withCooldown() {
        long now = System.nanoTime() / NANOS;
        long last = plugin.getMetadata(player, META_COOLDOWN_WILD, Long.class).orElse(-1L);
        if (last >= 0) {
            long since = now - last;
            long cooldown = (long) Globals.WILD_COOLDOWN;
            long remain = cooldown - since;
            if (remain > 0) {
                player.sendMessage(Component.text("Please wait " + remain + " more seconds",
                                                  NamedTextColor.RED));
                return;
            }
        }
        plugin.setMetadata(player, META_COOLDOWN_WILD, now);
        player.sendMessage(Component.text("Looking for an unclaimed place to build...",
                                          NamedTextColor.YELLOW));
        plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
    }

    private void findPlaceToBuild() {
        if (!player.isValid()) return;
        // Attempts
        long now = System.nanoTime() / NANOS;
        plugin.setMetadata(player, META_COOLDOWN_WILD, now);
        if (attempts++ > 50) {
            player.sendMessage(Component.text("Could not find a place to build. Please try again",
                                              NamedTextColor.RED));
            return;
        }
        // Determine center and border
        if (!plugin.isLocalHomeWorld(world)) {
            plugin.getLogger().warning("WildTask: World not found: " + world.getName());
            player.sendMessage(Component.text("Something went wrong. Please contact an administrator",
                                              NamedTextColor.RED));
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
        if (!player.isValid()) return;
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
        Location ploc = player.getLocation();
        location.setPitch(ploc.getPitch());
        location.setYaw(ploc.getYaw());
        // Teleport, notify, and set cooldown
        plugin.warpTo(player, location, () -> {
                Component claimNewTooltip = Component
                    .join(JoinConfiguration.separator(Component.newline()),
                          Component.text("/claim new", NamedTextColor.GREEN),
                          Component.text("Create a claim and set a home"),
                          Component.text("at this location so you can"),
                          Component.text("build and return any time."))
                    .color(NamedTextColor.GRAY);
                Component wildTooltip = Component
                    .join(JoinConfiguration.separator(Component.newline()),
                          Component.text("/wild", NamedTextColor.GREEN),
                          Component.text("Find another place to build"))
                    .color(NamedTextColor.GRAY);
                ComponentLike message = Component.text().color(NamedTextColor.WHITE)
                    .append(Component.text("Found you a place to build."))
                    .append(Component.newline())
                    .append(Component.text("Do you like it? ", NamedTextColor.GRAY))
                    .append(Component.text("[Claim]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.suggestCommand("/claim new"))
                            .hoverEvent(HoverEvent.showText(claimNewTooltip)))
                    .append(Component.text(" or "))
                    .append(Component.text("[Try Again]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand("/wild"))
                            .hoverEvent(HoverEvent.showText(wildTooltip)));
                player.sendMessage(message);
                PluginPlayerEvent.Name.USE_WILD.call(plugin, player);
            });
    }
}
