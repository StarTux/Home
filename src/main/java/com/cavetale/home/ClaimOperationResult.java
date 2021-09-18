package com.cavetale.home;

public enum ClaimOperationResult {
    INSUFFICIENT_BLOCKS,
    OVERLAP,
    SUCCESS;

    public boolean isSuccessful() {
        return this == ClaimOperationResult.SUCCESS;
    }
}
