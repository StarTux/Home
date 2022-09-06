package com.cavetale.home;

import com.cavetale.home.struct.Vec2i;
import lombok.Value;

@Value
public final class Area {
    public final int ax;
    public final int ay;
    public final int bx;
    public final int by;

    public boolean contains(int x, int y) {
        return x >= ax && x <= bx && y >= ay && y <= by;
    }

    public boolean isWithin(int x, int y, int d) {
        return x >= ax - d && x <= bx + d && y >= ay - d && y <= by + d;
    }

    public boolean overlaps(Area o) {
        boolean h = o.ax <= this.bx && o.bx >= this.ax;
        boolean v = o.ay <= this.by && o.by >= this.ay;
        return h && v;
    }

    public boolean contains(Area o) {
        return o.ax >= this.ax && o.bx <= this.bx && o.ay >= this.ay && o.by <= this.by;
    }

    public int width() {
        return bx - ax + 1;
    }

    public int height() {
        return by - ay + 1;
    }

    public int size() {
        return (bx - ax + 1) * (by - ay + 1);
    }

    /**
     * Rough distance function getting the sum of the horizontal and
     * vertical distance.  Only to be used to find nearest claim;
     * never for precision operations!
     */
    public int distanceToPoint(int x, int y) {
        int dx;
        int dy;
        if (x < ax) {
            dx = Math.abs(ax - x);
        } else if (x > bx) {
            dx = Math.abs(x - bx);
        } else {
            dx = 0;
        }
        if (y < ay) {
            dy = Math.abs(ay - y);
        } else if (y > by) {
            dy = Math.abs(y - by);
        } else {
            dy = 0;
        }
        return dx + dy;
    }

    public int centerX() {
        return (ax + bx) / 2;
    }

    public int centerY() {
        return (ay + by) / 2;
    }

    @Override
    public String toString() {
        return "(" + ax + ", " + ay + ")-(" + bx + ", " + by + ")";
    }

    public int clampX(int x) {
        if (x < ax) return ax;
        if (x > bx) return bx;
        return x;
    }

    public int clampY(int y) {
        if (y < ay) return ay;
        if (y > by) return by;
        return y;
    }

    /**
     * Fit this claim into another.
     */
    public Area fitWithin(Area outer) {
        if (outer.contains(this)) return this;
        return new Area(outer.clampX(ax), outer.clampY(ay),
                        outer.clampX(bx), outer.clampY(by));
    }

    public Area growTo(int x, int y) {
        return new Area(Math.min(ax, x), Math.min(ay, y),
                        Math.max(bx, x), Math.max(by, y));
    }

    public Vec2i getNearestOutside(Vec2i nearby) {
        int distX = Math.min(Math.abs(ax - nearby.x), Math.abs(bx - nearby.x));
        int distY = Math.min(Math.abs(ay - nearby.y), Math.abs(by - nearby.y));
        if (distX < distY) {
            // closer to left or right
            return nearby.x > centerX()
                ? Vec2i.of(bx + 1, nearby.y)
                : Vec2i.of(ax - 1, nearby.y);
        } else {
            return nearby.y > centerY()
                ? Vec2i.of(nearby.x, by + 1)
                : Vec2i.of(nearby.x, ay - 1);
        }
    }
}
