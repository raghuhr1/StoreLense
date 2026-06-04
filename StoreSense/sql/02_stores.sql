-- =============================================================================
-- StoreLense: Schema stores
-- Owner: store-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- stores.stores
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stores.stores (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_code     VARCHAR(20)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    address_line1  VARCHAR(255),
    address_line2  VARCHAR(255),
    city           VARCHAR(100),
    state_province VARCHAR(100),
    postal_code    VARCHAR(20),
    country_code   CHAR(2)      NOT NULL DEFAULT 'AU',
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'UTC',
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    erp_store_code VARCHAR(50),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stores           PRIMARY KEY (id),
    CONSTRAINT uq_stores_code      UNIQUE (store_code),
    CONSTRAINT uq_stores_erp_code  UNIQUE (erp_store_code)
);

CREATE TRIGGER trg_stores_updated_at
    BEFORE UPDATE ON stores.stores
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- stores.zones
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stores.zones (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id      UUID        NOT NULL,
    zone_code     VARCHAR(50) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    zone_type     VARCHAR(30) NOT NULL DEFAULT 'floor',
    display_order INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_zones            PRIMARY KEY (id),
    CONSTRAINT uq_zones_store_code UNIQUE (store_id, zone_code),
    CONSTRAINT fk_zones_store      FOREIGN KEY (store_id) REFERENCES stores.stores (id) ON DELETE CASCADE,
    CONSTRAINT ck_zones_type       CHECK (zone_type IN ('floor', 'backroom', 'fitting_room', 'stockroom', 'display', 'entrance'))
);

CREATE TRIGGER trg_zones_updated_at
    BEFORE UPDATE ON stores.zones
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- stores.rfid_readers
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stores.rfid_readers (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id          UUID        NOT NULL,
    zone_id           UUID,
    reader_code       VARCHAR(50) NOT NULL,
    reader_type       VARCHAR(20) NOT NULL,
    ip_address        INET,
    mac_address       MACADDR,
    firmware_version  VARCHAR(50),
    antenna_count     SMALLINT    NOT NULL DEFAULT 4,
    tx_power_dbm      NUMERIC(5,2),
    is_active         BOOLEAN     NOT NULL DEFAULT true,
    last_heartbeat_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_rfid_readers            PRIMARY KEY (id),
    CONSTRAINT uq_rfid_readers_code       UNIQUE (store_id, reader_code),
    CONSTRAINT fk_rfid_readers_store      FOREIGN KEY (store_id) REFERENCES stores.stores (id) ON DELETE CASCADE,
    CONSTRAINT fk_rfid_readers_zone       FOREIGN KEY (zone_id)  REFERENCES stores.zones  (id) ON DELETE SET NULL,
    CONSTRAINT ck_rfid_readers_type       CHECK (reader_type IN ('fixed', 'handheld', 'bluetooth_sled')),
    CONSTRAINT ck_rfid_readers_antennas   CHECK (antenna_count BETWEEN 1 AND 32),
    CONSTRAINT ck_rfid_readers_power      CHECK (tx_power_dbm BETWEEN 0 AND 33)
);

CREATE TRIGGER trg_rfid_readers_updated_at
    BEFORE UPDATE ON stores.rfid_readers
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- stores.store_config
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stores.store_config (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id            UUID         NOT NULL,
    rfid_power_dbm      NUMERIC(5,2) NOT NULL DEFAULT 30.0,
    rfid_session        SMALLINT     NOT NULL DEFAULT 2,
    rfid_target         VARCHAR(5)   NOT NULL DEFAULT 'A',
    soh_schedule_cron   VARCHAR(100),
    refill_auto_assign  BOOLEAN      NOT NULL DEFAULT false,
    erp_sync_enabled    BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_store_config          PRIMARY KEY (id),
    CONSTRAINT uq_store_config_store    UNIQUE (store_id),
    CONSTRAINT fk_store_config_store    FOREIGN KEY (store_id) REFERENCES stores.stores (id) ON DELETE CASCADE,
    CONSTRAINT ck_store_config_session  CHECK (rfid_session BETWEEN 0 AND 3),
    CONSTRAINT ck_store_config_target   CHECK (rfid_target IN ('A', 'B', 'AB')),
    CONSTRAINT ck_store_config_power    CHECK (rfid_power_dbm BETWEEN 0 AND 33)
);

CREATE TRIGGER trg_store_config_updated_at
    BEFORE UPDATE ON stores.store_config
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_stores_active
    ON stores.stores (is_active, store_code);

CREATE INDEX IF NOT EXISTS idx_zones_store
    ON stores.zones (store_id) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_rfid_readers_store_zone
    ON stores.rfid_readers (store_id, zone_id);

CREATE INDEX IF NOT EXISTS idx_rfid_readers_heartbeat
    ON stores.rfid_readers (last_heartbeat_at DESC) WHERE is_active = true;
