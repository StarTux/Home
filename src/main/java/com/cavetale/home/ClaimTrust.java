package com.cavetale.home;

import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data @Table(name = "claim_trust",
             uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "trustee"}))
public final class ClaimTrust {
    @Id Integer id;
    @Column(nullable = false) Integer claimId;
    @Column(nullable = false, length = 15) String type;
    @Column(nullable = false) UUID trustee;

    static enum Type {
        MEMBER, VISIT;
    }

    public ClaimTrust() { }

    ClaimTrust(Claim claim, Type type, UUID trustee) {
        this.claimId = claim.getId();
        this.type = type.name().toLowerCase();
        this.trustee = trustee;
    }
}
