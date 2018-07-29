package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data @Table(name = "home_invites")
final class HomeInvite {
    @Id Integer id;
    @Column(nullable = false) Integer homeId;
    @Column(nullable = true) UUID invitee; // null means public
}
