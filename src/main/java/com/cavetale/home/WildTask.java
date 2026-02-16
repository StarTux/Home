package com.cavetale.home;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.winthier.connect.Redis;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
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

    private String getCooldownKey() {
        return "home:wild-cooldown-" + player.getUniqueId();
    }

    private long getCooldown() {
        final String value = Redis.get(getCooldownKey());
        return value != null
            ? Long.parseLong(value)
            : 0L;
    }

    private void getCooldownAsync(Consumer<Long> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            () -> {
                final long cooldown = getCooldown();
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cooldown));
            }
        );
    }

    private void setCooldownSeconds(long seconds) {
        plugin.getLogger().info("[WildTask] Setting cooldown " + player.getName() + ": " + seconds + "s");
        if (seconds <= 0L) {
            Redis.del(getCooldownKey());
            return;
        }
        final long value = System.currentTimeMillis() + 1000L * seconds;
        Redis.set(getCooldownKey(), "" + value, seconds);
    }

    private void setCooldownSecondsAsync(long seconds, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            () -> {
                setCooldownSeconds(seconds);
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        );
    }

    public void withCooldown() {
        if (plugin.doesIgnoreClaims(player.getUniqueId())) {
            withoutCooldown();
        }
        final long now = System.currentTimeMillis();
        getCooldownAsync(
            cooldown -> {
                if (cooldown > now) {
                    final long remainingSeconds = (cooldown - now - 1L) / 1000L + 1L;
                    if (remainingSeconds > 0L) {
                        if (remainingSeconds > 60L) {
                            final long remainingMinutes = (remainingSeconds - 1L) / 60L + 1L;
                            if (remainingMinutes == 1L) {
                                player.sendMessage(text("Please wait one more minute", RED));
                            } else {
                                player.sendMessage(text("Please wait " + remainingMinutes + " more minutes", RED));
                            }
                        } else {
                            if (remainingSeconds == 1L) {
                                player.sendMessage(text("Please wait one more second", RED));
                            } else {
                                player.sendMessage(text("Please wait " + remainingSeconds + " more seconds", RED));
                            }
                        }
                        return;
                    }
                } else {
                    setCooldownSecondsAsync(Globals.WILD_COOLDOWN, this::withoutCooldown);
                }
            }
        );
    }

    public void withoutCooldown() {
        player.sendMessage(text("Looking for an unclaimed place to build...", YELLOW));
        plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
    }

    private void findPlaceToBuild() {
        // Attempts
        if (attempts++ > 50) {
            player.sendMessage(text("Could not find a place to build. Please try again", RED));
            setCooldownSecondsAsync(0L, () -> { });
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
        if (!plugin.doesIgnoreClaims(player.getUniqueId())) {
            if (plugin.hasAClaim(player.getUniqueId())) {
                setCooldownSecondsAsync(Globals.WILD_WITH_CLAIM_COOLDOWN, () -> { });
            } else {
                setCooldownSecondsAsync(Globals.WILD_COOLDOWN, () -> { });
            }
        }
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
