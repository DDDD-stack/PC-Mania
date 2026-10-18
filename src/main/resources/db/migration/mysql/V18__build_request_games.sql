-- Kept in step with postgresql/V18.
ALTER TABLE build_request ADD COLUMN games VARCHAR(300) NULL;
