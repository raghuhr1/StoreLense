-- Allow PRODUCT_LINK as a matched_by value: EPCs that resolve to a product via
-- direct EAN association but do not decode to a matching SGTIN-96 EAN.
SET search_path TO erp;

ALTER TABLE erp_soh_snapshot_epcs
    DROP CONSTRAINT ck_erp_soh_snapshot_matched_by;

ALTER TABLE erp_soh_snapshot_epcs
    ADD CONSTRAINT ck_erp_soh_snapshot_matched_by
        CHECK (matched_by IN ('SGTIN96', 'INBOUND_COMMISSION', 'MANUAL', 'PRODUCT_LINK'));
