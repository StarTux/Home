package com.cavetale.home.sql;

import com.cavetale.home.Area;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import lombok.Data;

@Data
@Name("subclaims")
@NotNull
public final class SQLSubclaim implements SQLRow {
    @Id private Integer id;

    @Keyed private int claimId;

    @VarChar(32) private String world;

    private int ax;

    private int ay;

    private int bx;

    private int by;

    @Text private String tag;

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
