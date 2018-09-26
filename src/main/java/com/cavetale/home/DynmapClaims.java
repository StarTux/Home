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

    DynmapClaims(HomePlugin plugin) {
        this.plugin = plugin;
    }

    boolean update() {
        Plugin dplugin = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
        if (dplugin == null) return false;
        if (!dplugin.isEnabled()) return false;
        DynmapAPI dynmap = (DynmapAPI)dplugin;
        MarkerAPI dmarker = dynmap.getMarkerAPI();
        if (dmarker == null) return false;
        MarkerSet markerSet = dmarker.getMarkerSet(MARKER_SET);
        if (markerSet == null) dmarker.createMarkerSet(MARKER_SET, "Claims", null, false);
        if (markerSet == null) return false;
        markerSet.setMinZoom(0);
        markerSet.setLayerPriority(10);
        markerSet.setHideByDefault(false);
        for (Claim claim: plugin.getClaims()) {
            double[] x = new double[4];
            double[] z = new double[4];
            x[0] = x[1] = (double)claim.getArea().ax;
            x[2] = x[3] = (double)claim.getArea().bx;
            z[0] = z[3] = (double)claim.getArea().ay;
            z[1] = z[2] = (double)claim.getArea().by;
            AreaMarker marker = markerSet.findAreaMarker("" + claim.getId());
            if (marker == null) {
                marker = markerSet.createAreaMarker("" + claim.getId(), claim.getOwnerName(), false, claim.getWorld(), x, z, false);
            } else {
                marker.setCornerLocations(x, z);
            }
            marker.setBoostFlag(true);
            marker.setFillStyle(0.0, 0);
        }
        for (AreaMarker marker: markerSet.getAreaMarkers()) {
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
        Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
        if (plugin == null) return;
        DynmapAPI dynmap = (DynmapAPI)plugin;
        MarkerAPI dmarker = dynmap.getMarkerAPI();
        if (dmarker == null) return;
        MarkerSet markerSet = dmarker.getMarkerSet(MARKER_SET);
        if (markerSet != null) markerSet.deleteMarkerSet();
    }
}
