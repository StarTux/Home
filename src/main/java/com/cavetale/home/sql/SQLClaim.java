package com.cavetale.home.sql;

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
}
