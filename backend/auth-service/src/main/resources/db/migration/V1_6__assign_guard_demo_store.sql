SET search_path TO auth;

-- guard_demo was seeded with store_id = NULL (V1_4), which means principal.storeId()
-- resolves to null for that user and every bill/gate-check lookup 404s regardless of
-- billRef correctness, since those endpoints filter strictly by the guard's own store.
-- Assign it to the first active store so the C66 demo login has a working store out
-- of the box. Only applies if it's still unset, so real deployments that already
-- assigned a store are untouched.
UPDATE auth.users
SET store_id = (SELECT id FROM stores.stores WHERE is_active = true ORDER BY created_at LIMIT 1)
WHERE username = 'guard_demo' AND store_id IS NULL;
