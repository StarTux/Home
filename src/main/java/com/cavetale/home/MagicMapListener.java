package com.cavetale.home;

import com.cavetale.magicmap.MagicMapPlugin;
import com.cavetale.magicmap.MagicMapPostRenderEvent;
import com.cavetale.magicmap.MapCache;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class MagicMapListener implements Listener {
    private final HomePlugin plugin;
    static final int CLAIM_COLOR = 28 * 4 + 2;
    static final int SUBCLAIM_COLOR = 8 * 4 + 2;
    static final int BLACK = 29 * 4 + 3;
    static final int DARK_GRAY = 21 * 4 + 3;

    public MagicMapListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return this;
    }

    @EventHandler
    void onMagicMapPostRender(MagicMapPostRenderEvent event) {
        Area mapArea = new Area(event.getMinX(), event.getMinZ(), event.getMaxX(), event.getMaxZ());
        for (Claim claim : plugin.findClaimsInWorld(event.getWorldName())) {
            if (!claim.getArea().overlaps(mapArea)) continue;
            if (claim.isHidden() && !claim.getTrustType(event.getPlayer()).canBuild()) continue;
            drawRect(event.getMapCache(), mapArea, claim.getArea(), CLAIM_COLOR, claim.getOwnerName() + "'s claim");
            for (Subclaim subclaim : claim.getSubclaims()) {
                drawRect(event.getMapCache(), mapArea, subclaim.getArea(), SUBCLAIM_COLOR, null);
            }
        }
    }

    static void drawRect(MapCache mapCache, Area mapArea, Area claimArea, int color, String label) {
        Area drawArea = claimArea.fitWithin(mapArea);
        for (int x = drawArea.ax; x <= drawArea.bx; x += 1) {
            drawDotted(mapCache, mapArea, x, drawArea.ay, color);
            drawDotted(mapCache, mapArea, x, drawArea.by, color);
        }
        for (int y = drawArea.ay; y <= drawArea.by; y += 1) {
            drawDotted(mapCache, mapArea, drawArea.ax, y, color);
            drawDotted(mapCache, mapArea, drawArea.bx, y, color);
        }
        if (label == null) return;
        MagicMapPlugin.getInstance().getTinyFont()
            .print(label, drawArea.ax - mapArea.ax + 2, drawArea.ay - mapArea.ay + 2,
                   (x, y) -> mapCache.setPixel(x, y, color),
                   (x, y) -> mapCache.setPixel(x, y, DARK_GRAY));
    }

    static void drawDotted(MapCache mapCache, Area mapArea, int worldX, int worldY, int color) {
        int x = worldX - mapArea.ax;
        int y = worldY - mapArea.ay;
        if ((x & 1) == (y & 1)) return;
        mapCache.setPixel(x, y, color);
    }
}
