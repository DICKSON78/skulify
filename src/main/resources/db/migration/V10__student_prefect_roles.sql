-- Student responsibilities are prefect roles, not staff appointments.

UPDATE students
SET student_position = 'LIBRARY_PREFECT'
WHERE student_position = 'LIBRARIAN';

UPDATE students
SET student_position = 'LAB_PREFECT'
WHERE student_position = 'LAB_TECHNICIAN';
