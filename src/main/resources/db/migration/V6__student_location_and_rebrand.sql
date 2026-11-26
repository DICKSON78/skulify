-- Student location (mkoa/wilaya/mtaa) and Skulify display-name update.

ALTER TABLE students ADD COLUMN IF NOT EXISTS region   VARCHAR(80);
ALTER TABLE students ADD COLUMN IF NOT EXISTS district VARCHAR(80);
ALTER TABLE students ADD COLUMN IF NOT EXISTS street   VARCHAR(120);

ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_region   VARCHAR(80);
ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_district VARCHAR(80);
ALTER TABLE students ADD COLUMN IF NOT EXISTS guardian_street   VARCHAR(120);

CREATE INDEX IF NOT EXISTS students_location_idx ON students (tenant_id, region, district);
CREATE INDEX IF NOT EXISTS students_guardian_location_idx ON students (tenant_id, guardian_region, guardian_district);

-- Gender no longer supports OTHER; keep existing records readable by the enum.
UPDATE students
SET gender = NULL
WHERE gender = 'OTHER';

UPDATE tenants
SET name = 'Skulify Academy', updated_at = now()
WHERE id = '11111111-1111-1111-1111-111111111111' OR name = 'School Wallet Academy';

UPDATE app_settings
SET setting_value = 'Skulify Academy', updated_at = now()
WHERE setting_key = 'school.name' AND setting_value LIKE '%School Wallet%';
