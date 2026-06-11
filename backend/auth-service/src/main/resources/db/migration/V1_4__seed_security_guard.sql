SET search_path TO auth;

-- Demo security guard user: Guard@StoreLense1
-- Assigned to store PT001 (Sydney CBD / BNG ORION from seed)
-- The storeId is resolved at runtime by the seed script; this seeds a store-agnostic guard
-- that the C66 app demo login page uses by default.
INSERT INTO users (
    id, username, email, password_hash,
    first_name, last_name, store_id, is_active, password_changed_at
) VALUES (
    '00000000-0000-0000-0002-000000000001',
    'guard_demo',
    'guard@storelense.internal',
    '$2b$12$L71FbNV6Qbg3clDJyij0/OHLPk.KnnX2Du3ihCtek/yiWh9bkJYH6',
    'Gate', 'Guard',
    NULL, true, now()
) ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id, granted_by)
VALUES (
    '00000000-0000-0000-0002-000000000001',
    '00000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0001-000000000001'
) ON CONFLICT (user_id, role_id) DO NOTHING;
