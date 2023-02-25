package com.cavetale.home.sql;

import com.cavetale.home.Area;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Data
@NotNull
@Name("claims")
public final class SQLClaim implements SQLRow {
    @Id private Integer id;

    private UUID owner;

    @VarChar(16) private String world;

    private int ax;

    private int ay;

    private int bx;

    private int by;

    private int blocks;

    @VarChar(255) private String settings;

    @Nullable @VarChar(255) private String name;

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
