package com.cavetale.home.sql;

import com.winthier.sql.SQLRow;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Table(name = "home_invites",
       indexes = {
           @Index(name = "home_id", columnList = "home_id"),
           @Index(name = "invitee", columnList = "invitee"),
       },
       uniqueConstraints = @UniqueConstraint(columnNames = {"home_id", "invitee"}))
public final class SQLHomeInvite implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer homeId;

    @Column(nullable = false)
    private UUID invitee;

    public SQLHomeInvite() { }

    public SQLHomeInvite(final int homeId, final UUID invitee) {
        if (invitee == null) throw new NullPointerException("Invitee cannot be null");
        this.homeId = homeId;
        this.invitee = invitee;
    }
}
