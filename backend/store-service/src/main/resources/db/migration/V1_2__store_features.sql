-- =============================================================================
-- StoreLense: Per-store feature flags
-- =============================================================================

CREATE TABLE IF NOT EXISTS stores.store_features (
    store_id   UUID        NOT NULL,
    feature    VARCHAR(40) NOT NULL,
    enabled    BOOLEAN     NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_store_features        PRIMARY KEY (store_id, feature),
    CONSTRAINT fk_store_features_store  FOREIGN KEY (store_id) REFERENCES stores.stores (id) ON DELETE CASCADE,
    CONSTRAINT ck_store_features_name   CHECK (feature IN (
        'INVENTORY', 'INBOUND', 'REPLENISHMENT', 'CYCLE_COUNT',
        'TRANSFERS', 'ANALYTICS', 'SALES', 'DEVICES', 'ERP_INTEGRATION'
    ))
);

CREATE TRIGGER trg_store_features_updated_at
    BEFORE UPDATE ON stores.store_features
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- Seed all features ON for every existing store
INSERT INTO stores.store_features (store_id, feature)
SELECT s.id, f.feature
FROM   stores.stores s
CROSS JOIN (VALUES
    ('INVENTORY'), ('INBOUND'), ('REPLENISHMENT'), ('CYCLE_COUNT'),
    ('TRANSFERS'), ('ANALYTICS'), ('SALES'), ('DEVICES'), ('ERP_INTEGRATION')
) AS f(feature)
ON CONFLICT DO NOTHING;
