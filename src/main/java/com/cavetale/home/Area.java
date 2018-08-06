package com.cavetale.home;

import lombok.Value;

@Value
final class Area {
    public final int ax, ay, bx, by;

    boolean contains(int x, int y) {
        return x >= ax && x <= bx && y >= ay && y <= by;
    }

    boolean isWithin(int x, int y, int d) {
        return x >= ax - d && x <= bx + d && y >= ay - d && y <= by + d;
    }

    boolean overlaps(Area o) {
        boolean h = o.ax <= this.bx && o.bx >= this.ax;
        boolean v = o.ay <= this.by && o.by >= this.ay;
        return h && v;
    }

    boolean contains(Area o) {
        return o.ax >= this.ax && o.bx <= this.bx && o.ay >= this.ay && o.by <= this.by;
    }

    int width() {
        return bx - ax + 1;
    }

    int height() {
        return by - ay + 1;
    }

    int size() {
        return (bx - ax + 1) * (by - ay + 1);
    }

    // Rough distance function getting the sum of the horizontal and
    // vertical distance.  Only to be used to find nearest claim;
    // never for precision operations!
    int distanceToPoint(int x, int y) {
        int dx, dy;
        if (x < ax) {
            dx = ax - x;
        } else if (x > bx) {
            dx = x - bx;
        } else {
            dx = 0;
        }
        if (y < ay) {
            dy = ay - y;
        } else if (y > by) {
            dy = y - by;
        } else {
            dy = 0;
        }
        return dx + dy;
    }
}
