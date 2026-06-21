package com.devpulse.developer;

public class TenantOwnershipException extends RuntimeException {
    public TenantOwnershipException(String message) {
        super(message);
    }
}