-- Sprint 3: add ERP-trigger provenance columns + expand session_type CHECK
SET search_path TO soh;

ALTER TABLE soh_sessions
    ADD COLUMN source      VARCHAR(30)  NOT NULL DEFAULT 'manual',
    ADD COLUMN zone_region VARCHAR(100);

-- PostgreSQL CHECK constraints cannot be altered in-place; must drop and recreate.
ALTER TABLE soh_sessions
    DROP CONSTRAINT ck_soh_sessions_type;

ALTER TABLE soh_sessions
    ADD CONSTRAINT ck_soh_sessions_type
        CHECK (session_type IN ('manual', 'scheduled', 'full_store', 'spot_check', 'erp_triggered'));
