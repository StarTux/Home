package com.cavetale.home.claimcache;

import com.cavetale.home.Area;
import com.cavetale.home.Claim;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * For fast claim location lookup.
 * One cache per world.
 */
final class SpatialClaimCache {
    private static final int CHUNK_BITS = 8;
    private static final int CHUNK_SIZE = 1 << CHUNK_BITS;
    private final YList ylist = new YList();
    /** All claims in this world. */
    protected final List<Claim> allClaims = new ArrayList<>();

    public void insert(Claim claim) {
        applySlots(claim.getArea(), true, slot -> slot.claims.add(claim));
        allClaims.add(claim);
    }

    public void remove(Claim claim) {
        applySlots(claim.getArea(), false, slot -> slot.claims.remove(claim));
        allClaims.remove(claim);
    }

    public void update(Claim claim, Area oldArea, Area newArea) {
        applySlots(claim.getArea(), false, slot -> slot.claims.remove(claim));
        applySlots(claim.getArea(), true, slot -> slot.claims.add(claim));
    }

    public Claim findClaimAt(int worldX, int worldZ) {
        int slotX = worldX >> CHUNK_BITS;
        int slotY = worldZ >> CHUNK_BITS;
        Slot slot = findSlot(slotX, slotY, false);
        if (slot == null) return null;
        for (Claim claim : slot.claims) {
            if (claim.getArea().contains(worldX, worldZ)) return claim;
        }
        return null;
    }

    public List<Claim> findClaimsWithin(Area area) {
        int ax = area.ax >> CHUNK_BITS;
        int bx = area.bx >> CHUNK_BITS;
        int ay = area.ay >> CHUNK_BITS;
        int by = area.by >> CHUNK_BITS;
        List<Claim> result = new ArrayList<>();
        for (int y = ay; y <= by; y += 1) {
            for (int x = ax; x <= bx; x += 1) {
                Slot slot = findSlot(x, y, false);
                if (slot == null) continue;
                for (Claim claim : slot.claims) {
                    if (!result.contains(claim) && area.overlaps(claim.getArea())) {
                        result.add(claim);
                    }
                }
            }
        }
        return result;
    }

    private void applySlots(final Area area, final boolean create, final Consumer<Slot> consumer) {
        final int ay = area.ay >> CHUNK_BITS;
        final int by = area.by >> CHUNK_BITS;
        final int ax = area.ax >> CHUNK_BITS;
        final int bx = area.bx >> CHUNK_BITS;
        for (int slotY = ay; slotY <= by; slotY += 1) {
            for (int slotX = ax; slotX <= bx; slotX += 1) {
                Slot slot = findSlot(slotX, slotY, create);
                if (slot != null) consumer.accept(slot);
            }
        }
    }

    private Slot findSlot(int slotX, int slotY, boolean create) {
        XList xlist = ylist.get(slotY, create);
        return xlist != null
            ? xlist.get(slotX, create)
            : null;
    }

    /**
     * The twin list contains 2 lists, one for positive, one for
     * negative indexes. Thus, it can grow in both directions.
     *
     * @param <T> Contained elements.
     */
    private abstract static class TwinList<T> {
        protected final List<T> positive = new ArrayList<>();
        protected final List<T> negative = new ArrayList<>();

        public final T get(final int index, final boolean create) {
            return index < 0
                ? getHelper(negative, -index - 1, create)
                : getHelper(positive, index, create);
        }

        public final T remove(final int index) {
            if (index < 0) {
                final int index2 = -index - 1;
                if (index2 >= negative.size()) return null;
                T result = negative.get(index2);
                negative.set(index2, null);
                return result;
            } else {
                if (index >= positive.size()) return null;
                T result = positive.get(index);
                positive.set(index, null);
                return result;
            }
        }

        private T getHelper(final List<T> list, final int index, final boolean create) {
            if (!create && index >= list.size()) return null;
            while (index >= list.size()) list.add(null);
            T result = list.get(index);
            if (create && result == null) {
                result = create();
                list.set(index, result);
            }
            return result;
        }

        protected abstract T create();
    }

    private static final class YList extends TwinList<XList> {
        @Override protected XList create() {
            return new XList();
        }
    }

    private static final class XList extends TwinList<Slot> {
        @Override protected Slot create() {
            return new Slot();
        }
    }

    private static final class Slot {
        protected final List<Claim> claims = new ArrayList<>();
    }
}
