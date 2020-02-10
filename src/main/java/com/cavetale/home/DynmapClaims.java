package com.cavetale.home;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

final class DynmapClaims {
    private final HomePlugin plugin;
    private static final String MARKER_SET = "home.claims";

    DynmapClaims(final HomePlugin plugin) {
        this.plugin = plugin;
    }

    boolean update() {
        Plugin dplugin = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
        if (dplugin == null) return false;
        if (!dplugin.isEnabled()) return false;
        DynmapAPI dynmap = (DynmapAPI) dplugin;
        MarkerAPI dmarker = dynmap.getMarkerAPI();
        if (dmarker == null) return false;
        MarkerSet markerSet = dmarker.getMarkerSet(MARKER_SET);
        if (markerSet == null) dmarker.createMarkerSet(MARKER_SET, "Claims", null, false);
        if (markerSet == null) return false;
        markerSet.setMinZoom(0);
        markerSet.setLayerPriority(10);
        markerSet.setHideByDefault(false);
        for (Claim claim : plugin.getClaims()) {
            double[] x = new double[4];
            double[] z = new double[4];
            x[1] = (double) claim.getArea().ax;
            x[3] = (double) claim.getArea().bx;
            z[3] = (double) claim.getArea().ay;
            z[2] = (double) claim.getArea().by;
            //
            x[0] = x[1];
            x[2] = x[3];
            z[0] = z[3];
            z[1] = z[2];
            AreaMarker marker = markerSet.findAreaMarker("" + claim.getId());
            if (marker == null) {
                if (claim.getBoolSetting(Claim.Setting.HIDDEN)) {
                    continue;
                }
                marker = markerSet.createAreaMarker("" + claim.getId(), claim.getOwnerName(),
                                                    false, claim.getWorld(), x, z, false);
            } else {
                if (claim.getBoolSetting(Claim.Setting.HIDDEN)) {
                    marker.deleteMarker();
                    continue;
                }
                marker.setCornerLocations(x, z);
            }
            marker.setBoostFlag(true);
            if (claim.isAdminClaim()) {
                marker.setLineStyle(2, 0.75, 0x0000FF);
            } else {
                marker.setLineStyle(2, 0.75, 0xFF8888);
            }
            marker.setFillStyle(0.0, 0);
        }
        for (AreaMarker marker : markerSet.getAreaMarkers()) {
            int id;
            try {
                id = Integer.parseInt(marker.getMarkerID());
            } catch (NumberFormatException nfe) {
                continue;
            }
            Claim claim = plugin.findClaimWithId(id);
            if (claim == null) marker.deleteMarker();
        }
        return true;
    }

    void disable() {
        Plugin dplugin = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
        if (dplugin == null) return;
        DynmapAPI dynmap = (DynmapAPI) dplugin;
        MarkerAPI dmarker = dynmap.getMarkerAPI();
        if (dmarker == null) return;
        MarkerSet markerSet = dmarker.getMarkerSet(MARKER_SET);
        if (markerSet != null) markerSet.deleteMarkerSet();
    }
}
