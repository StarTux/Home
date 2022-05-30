package com.cavetale.home.sql;

import com.cavetale.home.Area;
import com.winthier.sql.SQLRow;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;

@Data
@Table(name = "subclaims",
       indexes = @Index(name = "claim_id", columnList = "claim_id"))
public final class SQLSubclaim implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false)
    private int claimId;

    @Column(nullable = false)
    private String world;

    @Column(nullable = false)
    private int ax;

    @Column(nullable = false)
    private int ay;

    @Column(nullable = false)
    private int bx;

    @Column(nullable = false)
    private int by;

    @Column(nullable = false, length = 4096)
    private String tag;

    public SQLSubclaim() { }

    public SQLSubclaim(final int claimId, final String world, final Area area) {
        this.claimId = claimId;
        this.world = world;
        setArea(area);
        this.tag = "{}";
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
