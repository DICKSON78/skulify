package org.example.school_wallet.user;

import java.util.EnumSet;
import java.util.Set;

/**
 * Platform and school roles. Keep values stable: they are persisted and appear
 * in JWT claims.
 */
public enum Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    HEAD_MASTER,
    SECOND_MASTER,
    HEAD_TEACHER,
    ACADEMIC_MASTER,
    DISCIPLINE_MASTER,
    TEACHER,
    CLASS_TEACHER,
    ACCOUNTANT,
    REGISTRAR,
    HR_MANAGER,
    LIBRARIAN,
    STORE_KEEPER,
    CASHIER,
    PARENT,
    STUDENT;

    private static final Set<Role> MFA_REQUIRED =
            EnumSet.of(SUPER_ADMIN, TENANT_ADMIN, HEAD_MASTER, ACCOUNTANT, HR_MANAGER);

    public boolean requiresMfa() {
        return MFA_REQUIRED.contains(this);
    }

    public boolean isPlatformRole() {
        return this == SUPER_ADMIN;
    }
}
