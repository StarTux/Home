package com.cavetale.home.sql;

import com.cavetale.core.connect.Connect;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import lombok.Data;

@Data @NotNull @Name("worlds")
public final class SQLHomeWorld implements SQLRow {
    @Id private Integer id;
    @VarChar(40) @Unique private String world;
    @VarChar(40) private String server;
    @VarChar(255) @Nullable private String displayName;
    private boolean wild;
    private int claims;
    private long free;

    public SQLHomeWorld() { }

    public SQLHomeWorld(final String world, final String server) {
        this.world = world;
        this.server = server;
    }

    public boolean isOnThisServer() {
        return this.server.equals(Connect.get().getServerName());
    }

    public String computeDisplayName() {
        if (displayName != null) return displayName;
        if (world.endsWith("_nether")) return "Nether";
        if (world.endsWith("_the_end")) return "End";
        return "Overworld";
    }

    public String getDisplayName() {
        if (displayName == null) {
            displayName = computeDisplayName();
        }
        return displayName;
    }
}
