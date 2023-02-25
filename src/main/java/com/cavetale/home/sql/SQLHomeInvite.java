package com.cavetale.home.sql;

import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow.UniqueKey;
import com.winthier.sql.SQLRow;
import java.util.UUID;
import lombok.Data;

@Data
@Name("home_invites")
@NotNull
@UniqueKey({"homeId", "invitee"})
public final class SQLHomeInvite implements SQLRow {
    @Id private Integer id;

    @Keyed private int homeId;

    @Keyed private UUID invitee;

    public SQLHomeInvite() { }

    public SQLHomeInvite(final int homeId, final UUID invitee) {
        if (invitee == null) throw new NullPointerException("Invitee cannot be null");
        this.homeId = homeId;
        this.invitee = invitee;
    }
}
