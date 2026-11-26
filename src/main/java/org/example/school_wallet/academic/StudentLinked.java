package org.example.school_wallet.academic;

import java.util.UUID;

/** Implemented by records that belong to one student, for parent/student scoping. */
public interface StudentLinked {

    UUID getStudentId();
}
