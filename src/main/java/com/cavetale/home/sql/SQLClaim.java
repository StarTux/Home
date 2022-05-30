package com.cavetale.home.sql;

import com.cavetale.home.Area;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data @Table(name = "claims")
public final class SQLClaim implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false)
    private UUID owner;

    @Column(nullable = false, length = 16)
    private String world;

    @Column(nullable = false)
    private int ax;

    @Column(nullable = false)
    private int ay;

    @Column(nullable = false)
    private int bx;

    @Column(nullable = false)
    private int by;

    @Column(nullable = false)
    private int blocks;

    @Column(nullable = false, length = 255)
    private String settings;

    @Column(nullable = true, length = 255)
    private String name;

    @Column(nullable = false)
    private Date created;

    public SQLClaim() { }

    public SQLClaim(final UUID owner, final String world, final Area area) {
        this.owner = owner;
        this.world = world;
        setArea(area);
        this.blocks = area.size();
        this.settings = "{}";
        this.name = null;
        this.created = new Date();
    }

    public Area getArea() {
        return new Area(ax, ay, bx, by);
    }

    public void setArea(Area area) {
        this.ax = area.ax;
        this.ay = area.ay;
        this.bx = area.bx;
        this.by = area.by;
    }
}
