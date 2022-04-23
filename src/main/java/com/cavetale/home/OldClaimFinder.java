package com.cavetale.home;

import com.winthier.exploits.Exploits;
import com.winthier.playerinfo.PlayerInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
public final class OldClaimFinder {
    private final HomePlugin plugin;
    private final CommandSender sender;
    private final int days = 90;
    private final long now = System.currentTimeMillis();
    private final long then = now - Duration.ofDays(days).toMillis();
    private int claimCount;
    protected List<OldClaim> oldClaims = new ArrayList<>();
    private Map<String, Integer> perWorldClaimCount = new HashMap<>();
    private Map<String, Integer> perWorldTotal = new HashMap<>();
    protected int progress;
    protected double tps = 20.0;

    @RequiredArgsConstructor
    protected final class OldClaim {
        protected final Claim claim;
        protected Date lastSeen;
        protected int playerPlacedBlocks;
        protected boolean delete;
    }

    public void start() {
        for (Claim claim : plugin.getClaimCache().getAllLocalClaims()) {
            perWorldClaimCount.compute(claim.getWorld(), (w, i) -> i != null ? i + 1 : 1);
            if (claim.getOwner() == null || claim.isAdminClaim()) continue;
            if (claim.getCreated() > then) continue;
            int initialSize = plugin.getWorldSettings().get(claim.getWorld()).initialClaimSize;
            if (claim.getArea().width() > initialSize) continue;
            if (claim.getArea().height() > initialSize) continue;
            OldClaim oldClaim = new OldClaim(claim);
            claimCount += 1;
            PlayerInfo.getInstance().lastLog(claim.getOwner(), date -> lastLogCallback(oldClaim, date));
        }
    }

    private void lastLogCallback(OldClaim oldClaim, Date date) {
        claimCount -= 1;
        if (date.getTime() < then) {
            oldClaim.lastSeen = date;
            oldClaims.add(oldClaim);
            perWorldTotal.compute(oldClaim.claim.getWorld(), (w, i) -> i != null ? i + 1 : 1);
        }
        if (claimCount == 0) checkBlocks();
    }

    private void checkBlocks() {
        if (oldClaims.isEmpty()) {
            finish();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkBlocksAsync);
    }

    private void checkBlocksAsync() {
        final int max = 4;
        Semaphore semaphore = new Semaphore(max);
        long stop = 0L;
        for (OldClaim oldClaim : oldClaims) {
            progress += 1;
            Area area = oldClaim.claim.getArea();
            final int az = area.ay >> 4;
            final int bz = area.by >> 4;
            final int ax = area.ax >> 4;
            final int bx = area.bx >> 4;
            OUTER:
            for (int chunkZ = az; chunkZ <= bz; chunkZ += 1) {
                for (int chunkX = ax; chunkX <= bx; chunkX += 1) {
                    if (System.currentTimeMillis() - stop > 3000L) {
                        stop = System.currentTimeMillis();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                                sender.sendMessage("[OldClaimFinder]"
                                                   + " max=" + max
                                                   + " progress=" + progress + "/" + oldClaims.size());
                            });
                    }
                    if (tps <= 19.0) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ie) { }
                    }
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                        break OUTER;
                    }
                    final int finalChunkX = chunkX;
                    final int finalChunkZ = chunkZ;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                            tps = Bukkit.getTPS()[0];
                            World world = Bukkit.getWorld(oldClaim.claim.getWorld());
                            if (world == null) {
                                semaphore.release();
                                return;
                            }
                            world.getChunkAtAsync(finalChunkX, finalChunkZ, (Consumer<Chunk>) chunk -> {
                                    for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 1) {
                                        for (int z = 0; z < 16; z += 1) {
                                            for (int x = 0; x < 16; x += 1) {
                                                Block block = chunk.getBlock(x, y, z);
                                                if (!block.isEmpty() && Exploits.isPlayerPlaced(block)) {
                                                    oldClaim.playerPlacedBlocks += 1;
                                                }
                                            }
                                        }
                                    }
                                    semaphore.release();
                                });
                        });
                }
            }
        }
        try {
            semaphore.acquire(max);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        Bukkit.getScheduler().runTask(plugin, this::finish);
    }

    private void finish() {
        int total = 0;
        Collections.sort(oldClaims, (b, a) -> Integer.compare(a.playerPlacedBlocks, b.playerPlacedBlocks));
        for (OldClaim oldClaim : oldClaims) {
            if (oldClaim.playerPlacedBlocks < 10) oldClaim.delete = true;
            sender.sendMessage("[OldClaimFinder] Info #" + oldClaim.claim.getId()
                               + " ppb=" + oldClaim.playerPlacedBlocks
                               + " " + oldClaim.claim.getWorld()
                               + " " + oldClaim.claim.getArea().centerX()
                               + " " + oldClaim.claim.getArea().centerY()
                               + " owner=" + oldClaim.claim.getOwnerName()
                               + " last=" + oldClaim.lastSeen
                               + " delete=" + oldClaim.delete);
        }
        sender.sendMessage("[OldClaimFinder] Claims older than " + days + " days:");
        for (Map.Entry<String, Integer> entry : perWorldTotal.entrySet()) {
            String worldName = entry.getKey();
            int worldTotal = entry.getValue();
            int worldClaimCount = perWorldClaimCount.getOrDefault(worldName, 0);
            int percentage = worldClaimCount > 0
                ? (worldTotal * 100 - 1) / worldClaimCount + 1
                : 100;
            sender.sendMessage("[OldClaimFinder] " +  worldName + ": "
                               + worldTotal + "/" + worldClaimCount
                               + " (" + percentage + "%)");
            total += worldTotal;
        }
        sender.sendMessage("[OldClaimFinder] Total: " + total);
        int deleted = 0;
        for (OldClaim oldClaim : oldClaims) {
            if (!oldClaim.delete) continue;
            sender.sendMessage("[OldClaimFinder] Deleting #" + oldClaim.claim.getId()
                               + " ppb=" + oldClaim.playerPlacedBlocks
                               + " " + oldClaim.claim.getWorld()
                               + " " + oldClaim.claim.getArea().centerX()
                               + " " + oldClaim.claim.getArea().centerY()
                               + " owner=" + oldClaim.claim.getOwnerName()
                               + " last=" + oldClaim.lastSeen);
            //plugin.deleteClaim(oldClaim.claim);
            deleted += 1;
        }
        if (deleted > 0) {
            sender.sendMessage("[OldClaimFinder] Deleted " + deleted + " old claim(s)");
        }
        plugin.claimAdminCommand.oldClaimFinder = null;
    }
}
