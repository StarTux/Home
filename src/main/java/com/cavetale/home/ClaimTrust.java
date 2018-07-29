package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data @Table(name = "claim_trust")
final class ClaimTrust {
    @Id Integer id;
    @Column(nullable = false) Integer claimId;
    @Column(nullable = false) String type;
    @Column(nullable = true) UUID trustee; // null means public
}
