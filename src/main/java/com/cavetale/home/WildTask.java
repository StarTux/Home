package com.cavetale.home;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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

    void withCooldown() {
        WorldSettings settings = plugin.worldSettings.get(world.getName());
        long now = System.nanoTime() / NANOS;
        long last = plugin.getMetadata(player, META_COOLDOWN_WILD, Long.class).orElse(-1L);
        if (last >= 0) {
            long since = now - last;
            long cooldown = (long) settings.wildCooldown;
            long remain = cooldown - since;
            if (remain > 0) {
                player.sendMessage(ChatColor.RED + "Please wait "
                                   + remain + " more seconds");
                return;
            }
        }
        plugin.setMetadata(player, META_COOLDOWN_WILD, now);
        player.sendMessage(ChatColor.YELLOW
                           + "Looking for an unclaimed place to build...");
        plugin.getServer().getScheduler().runTask(plugin, this::findPlaceToBuild);
    }

    void findPlaceToBuild() {
        if (!player.isValid()) return;
        // Attempts
        long now = System.nanoTime() / NANOS;
        plugin.setMetadata(player, META_COOLDOWN_WILD, now);
        if (attempts++ > 100) {
            player.sendMessage(ChatColor.RED
                               + "Could not find a place to build. Please try again");
            return;
        }
        // Determine center and border
        if (!plugin.isHomeWorld(world)) {
            plugin.getLogger().warning("WildTask: World not found: " + world.getName());
            player.sendMessage(ChatColor.RED
                               + "Something went wrong. Please contact an administrator.");
        }
        // Borders
        WorldSettings settings = plugin.worldSettings.get(world.getName());
        int margin = settings.claimMargin;
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int size = (int) Math.min(50000.0, border.getSize()) - margin * 2;
        if (size < 0) {
            throw new RuntimeException("World border makes no sense: " + size);
        }
        int x = 0;
        int z = 0;
        List<Claim> worldClaims = plugin.findClaimsInWorld(world.getName());
        boolean foundSpot = false;
        for (int i = 0; i < 100; i += 1) {
            if (findUnclaimedSpot(worldClaims, cx, cz, size, margin)) {
                foundSpot = true;
                break;
            }
        }
        if (!foundSpot) {
            player.sendMessage(ChatColor.RED
                               + "Could not find a place to build. Please try again");
        }
        world.getChunkAtAsync(x >> 4, z >> 4, this::onChunkLoaded);
    }

    boolean findUnclaimedSpot(List<Claim> worldClaims,
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
                ComponentBuilder cb = new ComponentBuilder("");
                cb.append("Found you a place to build.").color(ChatColor.WHITE);
                player.spigot().sendMessage(cb.create());
                cb = new ComponentBuilder("");
                cb.append("Click here: ").color(ChatColor.WHITE);
                cb.append("[Claim]").color(ChatColor.GREEN);
                cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/claim new"));
                String endl = "\n" + ChatColor.WHITE + ChatColor.ITALIC;
                BaseComponent[] tooltip = TextComponent
                    .fromLegacyText(ChatColor.GREEN + "/claim new"
                                    + endl + "Create a claim and set a home"
                                    + endl + "at this location so you can"
                                    + endl + "build and return any time.");
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
                cb.append(" ", ComponentBuilder.FormatRetention.NONE).reset();
                cb.append(" or here: ").color(ChatColor.WHITE);
                cb.append("[Retry]").color(ChatColor.YELLOW);
                cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/wild"));
                tooltip = TextComponent
                    .fromLegacyText(ChatColor.GREEN + "/wild"
                                    + endl + "Find another place to build.");
                cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip));
                player.spigot().sendMessage(cb.create());
            });
    }
}
