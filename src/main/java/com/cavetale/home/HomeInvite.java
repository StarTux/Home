package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data @Table(name = "home_invites",
             uniqueConstraints = @UniqueConstraint(columnNames = {"home_id", "invitee"}))
public final class HomeInvite {
    @Id Integer id;
    @Column(nullable = false) Integer homeId;
    @Column(nullable = false) UUID invitee;

    public HomeInvite() { }

    HomeInvite(final int homeId, final UUID invitee) {
        if (invitee == null) throw new NullPointerException("Invitee cannot be null");
        this.homeId = homeId;
        this.invitee = invitee;
    }
}
