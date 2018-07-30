package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data @Table(name = "claim_trust",
             uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "type", "trustee"}))
public final class ClaimTrust {
    @Id Integer id;
    @Column(nullable = false) Integer claimId;
    @Column(nullable = false, length = 16) String type;
    @Column(nullable = true) UUID trustee; // null means public
}
