package com.cavetale.home.sql;

import com.winthier.sql.SQLRow;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Table(name = "subclaims")
@AllArgsConstructor @NoArgsConstructor
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
}
