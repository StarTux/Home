package com.cavetale.home.sql;

import com.cavetale.home.Claim;
import com.cavetale.home.TrustType;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow.UniqueKey;
import java.util.UUID;
import lombok.Data;

@Data
@Name("claim_trust")
@NotNull
@UniqueKey({"claim_id", "trustee"})
public final class SQLClaimTrust implements SQLRow {
    @Id private Integer id;

    @Keyed private Integer claimId;

    @VarChar(15) private String type;

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
