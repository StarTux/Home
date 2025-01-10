package com.cavetale.home;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaimOperationResult {
    INSUFFICIENT_BLOCKS("You do not have enough claim blocks"),
    OVERLAP("Your claim would overlap with another claim"),
    SUBCLAIM_EXCLUDED("There are subclaims in the way"),
    SUCCESS("");

    private final String warningMessage;

    public boolean isSuccessful() {
        return this == ClaimOperationResult.SUCCESS;
    }
}
