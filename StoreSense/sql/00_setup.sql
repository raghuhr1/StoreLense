-- =============================================================================
-- StoreLense: Database Setup
-- Run as superuser before any schema scripts.
-- =============================================================================

-- Required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";     -- gen_random_uuid(), pgp functions
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements"; -- query performance monitoring
CREATE EXTENSION IF NOT EXISTS "btree_gist";   -- GiST index support for exclusion constraints

-- Create schemas
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS stores;
CREATE SCHEMA IF NOT EXISTS products;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS soh;
CREATE SCHEMA IF NOT EXISTS refill;
CREATE SCHEMA IF NOT EXISTS rfid;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS audit;

-- Create application roles (DB-level)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storelense_app') THEN
        CREATE ROLE storelense_app LOGIN PASSWORD 'CHANGE_IN_ENV';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storelense_readonly') THEN
        CREATE ROLE storelense_readonly LOGIN PASSWORD 'CHANGE_IN_ENV';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'storelense_migrations') THEN
        CREATE ROLE storelense_migrations LOGIN PASSWORD 'CHANGE_IN_ENV';
    END IF;
END;
$$;

-- Grant schema ownership to migrations role for DDL
GRANT CREATE ON DATABASE storelense TO storelense_migrations;
GRANT USAGE ON SCHEMA auth, stores, products, inventory, soh, refill, rfid, reporting, audit TO storelense_migrations;

-- App role: DML on all schemas
GRANT USAGE ON SCHEMA auth, stores, products, inventory, soh, refill, rfid, reporting, audit TO storelense_app;

-- Readonly role: SELECT only
GRANT USAGE ON SCHEMA auth, stores, products, inventory, soh, refill, rfid, reporting, audit TO storelense_readonly;

-- Default privileges so future tables are automatically accessible
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA auth
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA stores
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA products
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA inventory
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA soh
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA refill
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA rfid
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA reporting
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO storelense_app;
ALTER DEFAULT PRIVILEGES FOR ROLE storelense_migrations IN SCHEMA audit
    GRANT SELECT, INSERT ON TABLES TO storelense_app; -- no UPDATE/DELETE on audit

-- Shared utility: updated_at trigger function
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
