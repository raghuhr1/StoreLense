SET search_path TO auth;

INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',             'Full system access'),
    ('00000000-0000-0000-0000-000000000002', 'STORE_MANAGER',     'Store-level management'),
    ('00000000-0000-0000-0000-000000000003', 'STORE_ASSOCIATE',   'SOH count operations'),
    ('00000000-0000-0000-0000-000000000004', 'REFILL_ASSOCIATE',  'Refill task completion')
ON CONFLICT (name) DO NOTHING;
