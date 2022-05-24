package com.cavetale.home.sql;

import com.cavetale.core.connect.Connect;
import com.winthier.sql.SQLRow;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data @Table(name = "worlds")
public final class SQLHomeWorld implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false, length = 32, unique = true)
    private String world;

    @Column(nullable = false, length = 32)
    private String server;

    @Column(nullable = true, length = 255)
    private String displayName;

    @Column(nullable = false)
    private boolean wild;

    @Column(nullable = false)
    private int claims;

    @Column(nullable = false)
    private long free;

    public SQLHomeWorld() { }

    public SQLHomeWorld(final String world, final String server) {
        this.world = world;
        this.server = server;
    }

    public boolean isOnThisServer() {
        return this.server.equals(Connect.get().getServerName());
    }
}
