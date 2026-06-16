package com.nguyenlengocduy.smartbanking.backend.entity.user;

public enum Role {
    // Full admin access
    ADMIN,
    // End user of the banking system, default role for new registrations
    CUSTOMER,
    // Read-only access for audit and compliance review
    AUDITOR,
    // Bank staff with limited write
    TELLER
}
