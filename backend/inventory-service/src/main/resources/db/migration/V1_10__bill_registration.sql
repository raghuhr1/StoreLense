-- Bills registered by POS when a sale is made
-- Allows the C66 gate app to look up items for a bill reference barcode

CREATE TABLE IF NOT EXISTS inventory.bills (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_ref    VARCHAR(100) NOT NULL,
    store_id    UUID         NOT NULL,
    cashier_id  UUID,
    total_items INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_bill_ref_store UNIQUE (bill_ref, store_id)
);

CREATE TABLE IF NOT EXISTS inventory.bill_items (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id      UUID         NOT NULL REFERENCES inventory.bills(id) ON DELETE CASCADE,
    ean          VARCHAR(50)  NOT NULL,
    product_name VARCHAR(200),
    qty          INT          NOT NULL DEFAULT 1,
    unit_price   NUMERIC(10,2)
);

CREATE INDEX IF NOT EXISTS idx_bills_store_ref
    ON inventory.bills (store_id, bill_ref);
