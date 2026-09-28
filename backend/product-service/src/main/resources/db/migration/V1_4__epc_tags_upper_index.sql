-- InventoryService.identifyEpc joins on UPPER(et.epc) = :epc, but epc_tags has no
-- index on epc at all -- every call was a sequential scan of the whole table.
CREATE INDEX IF NOT EXISTS idx_epc_tags_epc_upper
    ON products.epc_tags (UPPER(epc));
