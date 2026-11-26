package org.example.school_wallet.tenant;

import java.util.UUID;

public final class SchoolTenants {

    public static final UUID SYSTEM_ID = new UUID(0L, 0L);
    public static final UUID DEMO_SCHOOL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String DEMO_SCHOOL_CODE = "DEMO-SCHOOL";

    private SchoolTenants() {
    }
}
