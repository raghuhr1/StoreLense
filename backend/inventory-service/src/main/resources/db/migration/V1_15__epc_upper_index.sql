-- identifyEpc (InventoryService.identifyEpc) joins on UPPER(er.epc) = :epc, but
-- the only index on epc_registry is uq_epc_registry_key on the plain (epc, store_id)
-- columns -- unusable for a query filtering on UPPER(epc). Every identify-epc call
-- (the live gate screen's per-item image/name lookup) was falling back to a
-- sequential scan of the whole table.
CREATE INDEX IF NOT EXISTS idx_epc_registry_epc_upper
    ON inventory.epc_registry (UPPER(epc));
