-- StoreLense PostgreSQL initialisation
-- Runs once when the postgres container is first created.
-- Flyway migrations in each service create the tables.

-- Application user
CREATE USER storelense_app WITH PASSWORD 'changeme';

-- Shared trigger function used by all service schemas
CREATE OR REPLACE FUNCTION public.fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $func$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$func$;
GRANT EXECUTE ON FUNCTION public.fn_set_updated_at() TO storelense_app;

-- Create all service schemas
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS stores;
CREATE SCHEMA IF NOT EXISTS products;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS soh;
CREATE SCHEMA IF NOT EXISTS refill;
CREATE SCHEMA IF NOT EXISTS rfid;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS erp;

-- Grant full access on all schemas
GRANT ALL PRIVILEGES ON DATABASE storelense TO storelense_app;
GRANT ALL ON SCHEMA auth        TO storelense_app;
GRANT ALL ON SCHEMA stores      TO storelense_app;
GRANT ALL ON SCHEMA products    TO storelense_app;
GRANT ALL ON SCHEMA inventory   TO storelense_app;
GRANT ALL ON SCHEMA soh         TO storelense_app;
GRANT ALL ON SCHEMA refill      TO storelense_app;
GRANT ALL ON SCHEMA rfid        TO storelense_app;
GRANT ALL ON SCHEMA reporting   TO storelense_app;
GRANT ALL ON SCHEMA erp         TO storelense_app;

-- Allow creating tables in all schemas
ALTER DEFAULT PRIVILEGES IN SCHEMA auth        GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA stores      GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA products    GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA inventory   GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA soh         GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA refill      GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rfid        GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA reporting   GRANT ALL ON TABLES    TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA erp         GRANT ALL ON TABLES    TO storelense_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth        GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA stores      GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA products    GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA inventory   GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA soh         GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA refill      GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA rfid        GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA reporting   GRANT ALL ON SEQUENCES TO storelense_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA erp         GRANT ALL ON SEQUENCES TO storelense_app;
