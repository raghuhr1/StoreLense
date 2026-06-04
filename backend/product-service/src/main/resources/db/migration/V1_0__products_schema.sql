-- =============================================================================
-- StoreLense: Schema products
-- Owner: product-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- products.product_categories  (self-referencing tree)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products.product_categories (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    parent_id  UUID,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_product_categories        PRIMARY KEY (id),
    CONSTRAINT uq_product_categories_code   UNIQUE (code),
    CONSTRAINT fk_product_categories_parent FOREIGN KEY (parent_id)
        REFERENCES products.product_categories (id) ON DELETE SET NULL
);

CREATE TRIGGER trg_product_categories_updated_at
    BEFORE UPDATE ON products.product_categories
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- products.products
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products.products (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    sku              VARCHAR(100) NOT NULL,
    name             VARCHAR(500) NOT NULL,
    description      TEXT,
    category_id      UUID,
    brand            VARCHAR(255),
    supplier_code    VARCHAR(100),
    erp_product_code VARCHAR(100),
    unit_of_measure  VARCHAR(20)  NOT NULL DEFAULT 'EACH',
    weight_grams     INT,
    is_rfid_enabled  BOOLEAN      NOT NULL DEFAULT true,
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    erp_synced_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_products          PRIMARY KEY (id),
    CONSTRAINT uq_products_sku      UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES products.product_categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_products_weight   CHECK (weight_grams IS NULL OR weight_grams > 0),
    CONSTRAINT ck_products_uom      CHECK (unit_of_measure IN ('EACH', 'PAIR', 'SET', 'BOX', 'PACK', 'KG', 'L'))
);

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products.products
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- products.barcodes
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products.barcodes (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    product_id    UUID        NOT NULL,
    barcode_type  VARCHAR(20) NOT NULL,
    barcode_value VARCHAR(100) NOT NULL,
    is_primary    BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_barcodes          PRIMARY KEY (id),
    CONSTRAINT uq_barcodes_value    UNIQUE (barcode_value),
    CONSTRAINT fk_barcodes_product  FOREIGN KEY (product_id) REFERENCES products.products (id) ON DELETE CASCADE,
    CONSTRAINT ck_barcodes_type     CHECK (barcode_type IN ('ean13', 'ean8', 'upc_a', 'qr_code', 'code128', 'data_matrix'))
);

-- Only one primary barcode per product
CREATE UNIQUE INDEX IF NOT EXISTS uq_barcodes_primary_per_product
    ON products.barcodes (product_id) WHERE is_primary = true;

-- ----------------------------------------------------------------------------
-- products.epc_tags
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products.epc_tags (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    epc            VARCHAR(128) NOT NULL,
    epc_encoding   VARCHAR(20)  NOT NULL DEFAULT 'SGTIN_96',
    product_id     UUID,
    company_prefix VARCHAR(20),
    item_reference VARCHAR(20),
    serial_number  VARCHAR(30),
    is_encoded     BOOLEAN      NOT NULL DEFAULT false,
    encoded_at     TIMESTAMPTZ,
    encoded_by     UUID,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_epc_tags          PRIMARY KEY (id),
    CONSTRAINT uq_epc_tags_epc      UNIQUE (epc),
    CONSTRAINT fk_epc_tags_product  FOREIGN KEY (product_id) REFERENCES products.products (id) ON DELETE SET NULL,
    CONSTRAINT ck_epc_encoding      CHECK (epc_encoding IN ('SGTIN_96', 'SGTIN_198', 'SSCC_96', 'GIAI_96'))
);

CREATE TRIGGER trg_epc_tags_updated_at
    BEFORE UPDATE ON products.epc_tags
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_products_category
    ON products.products (category_id) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_products_erp_code
    ON products.products (erp_product_code) WHERE erp_product_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_products_rfid_enabled
    ON products.products (is_rfid_enabled) WHERE is_rfid_enabled = true AND is_active = true;

CREATE INDEX IF NOT EXISTS idx_barcodes_product
    ON products.barcodes (product_id);

CREATE INDEX IF NOT EXISTS idx_epc_tags_product
    ON products.epc_tags (product_id) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_epc_tags_encoded
    ON products.epc_tags (is_encoded, encoded_at) WHERE is_encoded = true;
