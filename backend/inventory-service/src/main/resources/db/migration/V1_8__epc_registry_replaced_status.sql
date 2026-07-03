-- Allow 'replaced' as a valid EPC registry status.
-- Used when a physical RFID tag is replaced with a new one via Tag Items;
-- the old EPC is marked 'replaced' so it does not inflate the in-store count.
ALTER TABLE inventory.epc_registry DROP CONSTRAINT IF EXISTS ck_epc_registry_status;
ALTER TABLE inventory.epc_registry ADD CONSTRAINT ck_epc_registry_status
    CHECK (status IN ('in_store', 'sold', 'missing', 'damaged', 'transferred', 'replaced'));
