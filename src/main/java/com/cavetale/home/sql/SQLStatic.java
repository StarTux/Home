package com.cavetale.home.sql;

import com.winthier.sql.SQLRow;
import java.util.List;

public final class SQLStatic {
    public static List<Class<? extends SQLRow>> getAllTableClasses() {
        return List.of(SQLHomeWorld.class,
                       SQLClaim.class,
                       SQLClaimTrust.class,
                       SQLSubclaim.class,
                       SQLHome.class,
                       SQLHomeInvite.class);
    }

    private SQLStatic() { }
}
