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

    @Column(nullable = true)
    private Integer wildPriority;

    public SQLHomeWorld() { }

    public SQLHomeWorld(final String world, final String server) {
        this.world = world;
        this.server = server;
    }

    public int getWildPriority() {
        return wildPriority != null ? wildPriority : 0;
    }

    public boolean isOnThisServer() {
        return this.server.equals(Connect.get().getServerName());
    }
}
