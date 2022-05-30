package com.cavetale.home.sql;

import com.cavetale.home.Claim;
import com.cavetale.home.TrustType;
import com.winthier.sql.SQLRow;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Table(name = "claim_trust",
       indexes = @Index(name = "claim_id", columnList = "claim_id"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "trustee"}))
public final class SQLClaimTrust implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer claimId;

    @Column(nullable = false, length = 15)
    private String type;

    @Column(nullable = false)
    private UUID trustee;

    protected transient TrustType trustType;

    public SQLClaimTrust() { }

    public SQLClaimTrust(final Claim claim, final TrustType type, final UUID trustee) {
        this.claimId = claim.getId();
        this.type = type.key;
        this.trustee = trustee;
        this.trustType = type;
    }

    private TrustType parseTrustTypeHelper() {
        switch (this.type) {
        case "visit": return TrustType.INTERACT;
        case "member": return TrustType.BUILD;
        default: return TrustType.of(this.type);
        }
    }

    public TrustType parseTrustType() {
        if (trustType == null) {
            trustType = parseTrustTypeHelper();
        }
        return trustType;
    }

    public void setTrustType(final TrustType trustType) {
        this.type = trustType.key;
        this.trustType = trustType;
    }
}
