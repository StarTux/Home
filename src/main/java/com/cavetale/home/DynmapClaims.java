package com.cavetale.home;

import com.winthier.generic_events.GenericEvents;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

/**
 * Put all claims on the dynmap via the marker API. For simplicity use
 * an interval which updates all markers.
 * Known bugs: Not aware of claim mirroring.
 */
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
            if (claim.isHidden()) continue;
            AreaMarker marker = createOrUpdateAreaMarker(markerSet, "claim-" + claim.getId(), claim.getWorld(), claim.getArea());
            if (claim.isAdminClaim()) {
                marker.setLineStyle(2, 0.75, 0x0000FF);
                marker.setFillStyle(0.1, 0x0000FF);
                String label = "<strong>Admin Claim</strong>";
                marker.setLabel(label, true);
            } else {
                String label = ""
                    + "<strong>Owner</strong>: " + claim.getOwnerName()
                    + "<br/><strong>Size</strong>: "
                    + claim.getArea().width() + "x" + claim.getArea().height()
                    + "<br/><strong>Members</strong>: " + claim.getMembers().stream()
                    .map(GenericEvents::cachedPlayerName)
                    .collect(Collectors.joining(", "))
                    + "<br/><strong>Visitors</strong>: " + claim.getVisitors().stream()
                    .map(GenericEvents::cachedPlayerName)
                    .collect(Collectors.joining(", "));
                marker.setLabel(label, true);
                marker.setLineStyle(3, 0.75, 0xFF0000);
                marker.setFillStyle(0.1, 0xFF0000);
            }
            marker.setBoostFlag(true);
            if (claim.isAdminClaim()) continue; // No subclaims in admin claims
            for (Subclaim subclaim : claim.getSubclaims()) {
                marker = createOrUpdateAreaMarker(markerSet, "subclaim-" + claim.getId() + "-" + subclaim.getId(), subclaim.getWorld(), subclaim.getArea());
                Map<Subclaim.Trust, Set<UUID>> trusted = subclaim.getTrustedMap();
                String label = ""
                    + "<strong>Subclaim</strong>"
                    + "<br/>"
                    + "<strong>Owner</strong>: " + trusted.get(Subclaim.Trust.OWNER).stream()
                    .map(GenericEvents::cachedPlayerName)
                    .collect(Collectors.joining(", "))
                    + "<br/>"
                    + "<strong>Co-Owner</strong>: " + trusted.get(Subclaim.Trust.CO_OWNER).stream()
                    .map(GenericEvents::cachedPlayerName)
                    .collect(Collectors.joining(", "));
                marker.setLabel(label, true);
                marker.setLineStyle(2, 0.75, 0xFFFFFF);
                marker.setFillStyle(0.0, 0x000000);
            }
        }
        // Delete obsolete markers
        for (AreaMarker marker : markerSet.getAreaMarkers()) {
            String markerId = marker.getMarkerID();
            if (markerId.startsWith("claim-")) {
                int id;
                try {
                    id = Integer.parseInt(markerId.substring(6));
                } catch (NumberFormatException nfe) {
                    continue;
                }
                Claim claim = plugin.findClaimWithId(id);
                if (claim == null || claim.isHidden()) marker.deleteMarker();
            } else if (markerId.startsWith("subclaim-")) {
                String[] toks = markerId.split("-", 3);
                if (toks.length != 3) continue;
                int claimId;
                int subclaimId;
                try {
                    claimId = Integer.parseInt(toks[1]);
                    subclaimId = Integer.parseInt(toks[2]);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                Claim claim = plugin.findClaimWithId(claimId);
                if (claim == null || claim.isHidden()) continue;
                Subclaim subclaim = claim.getSubclaim(subclaimId);
                if (subclaim == null) {
                    marker.deleteMarker();
                }
            }
        }
        return true;
    }

    AreaMarker createOrUpdateAreaMarker(MarkerSet markerSet, String id, String world, Area area) {
        double[] x = new double[4];
        double[] z = new double[4];
        x[1] = (double) area.ax;
        x[3] = (double) area.bx;
        z[3] = (double) area.ay;
        z[2] = (double) area.by;
        //
        x[0] = x[1];
        x[2] = x[3];
        z[0] = z[3];
        z[1] = z[2];
        AreaMarker marker = markerSet.findAreaMarker(id);
        if (marker == null) {
            marker = markerSet.createAreaMarker(id, id, true, world, x, z, false);
        } else {
            marker.setCornerLocations(x, z);
        }
        return marker;
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
