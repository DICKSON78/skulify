-- Semester length drives week-based class attendance progress.

ALTER TABLE terms ADD COLUMN IF NOT EXISTS number_of_weeks INTEGER DEFAULT 16;

UPDATE terms
SET number_of_weeks = 16
WHERE number_of_weeks IS NULL OR number_of_weeks <= 0;
