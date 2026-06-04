-- =============================================================================
-- StoreLense: Seed Data
-- Reference data required for the system to function.
-- Safe to run multiple times (uses INSERT ... ON CONFLICT DO NOTHING).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Roles
-- ----------------------------------------------------------------------------
INSERT INTO auth.roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',
     'Full system access. Can manage users, stores, and all configuration.'),
    ('00000000-0000-0000-0000-000000000002', 'STORE_MANAGER',
     'Access to SOH, Refill, and Reporting for their assigned store.'),
    ('00000000-0000-0000-0000-000000000003', 'STORE_ASSOCIATE',
     'Can initiate and perform SOH count sessions for their assigned store.'),
    ('00000000-0000-0000-0000-000000000004', 'REFILL_ASSOCIATE',
     'Can view and complete refill tasks for their assigned store.')
ON CONFLICT (name) DO NOTHING;

-- ----------------------------------------------------------------------------
-- System admin user
-- Password: Admin@StoreLense1  (bcrypt cost 12 — CHANGE BEFORE PRODUCTION)
-- $2a$12$placeholder — replace with: SELECT crypt('Admin@StoreLense1', gen_salt('bf', 12));
-- ----------------------------------------------------------------------------
INSERT INTO auth.users (
    id, username, email, password_hash,
    first_name, last_name, store_id, is_active,
    password_changed_at
) VALUES (
    '00000000-0000-0000-0001-000000000001',
    'admin',
    'admin@storelense.internal',
    '$2a$12$REPLACE_WITH_BCRYPT_HASH',
    'System',
    'Administrator',
    NULL,   -- NULL store_id = access to all stores
    true,
    now()
)
ON CONFLICT (username) DO NOTHING;

-- Assign ADMIN role to system admin
INSERT INTO auth.user_roles (user_id, role_id, granted_by)
VALUES (
    '00000000-0000-0000-0001-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0001-000000000001'
)
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Product categories (top-level)
-- ----------------------------------------------------------------------------
INSERT INTO products.product_categories (id, parent_id, code, name) VALUES
    ('10000000-0000-0000-0000-000000000001', NULL, 'APPAREL',     'Apparel'),
    ('10000000-0000-0000-0000-000000000002', NULL, 'FOOTWEAR',    'Footwear'),
    ('10000000-0000-0000-0000-000000000003', NULL, 'ACCESSORIES', 'Accessories'),
    ('10000000-0000-0000-0000-000000000004', NULL, 'HOMEWARES',   'Homewares'),
    ('10000000-0000-0000-0000-000000000005', NULL, 'ELECTRONICS', 'Electronics')
ON CONFLICT (code) DO NOTHING;

-- Apparel sub-categories
INSERT INTO products.product_categories (id, parent_id, code, name) VALUES
    ('10000000-0000-0000-0001-000000000001', '10000000-0000-0000-0000-000000000001', 'APPAREL_TOPS',    'Tops'),
    ('10000000-0000-0000-0001-000000000002', '10000000-0000-0000-0000-000000000001', 'APPAREL_BOTTOMS',  'Bottoms'),
    ('10000000-0000-0000-0001-000000000003', '10000000-0000-0000-0000-000000000001', 'APPAREL_OUTERWEAR','Outerwear'),
    ('10000000-0000-0000-0001-000000000004', '10000000-0000-0000-0000-000000000001', 'APPAREL_UNDERWEAR','Underwear & Socks')
ON CONFLICT (code) DO NOTHING;

-- Footwear sub-categories
INSERT INTO products.product_categories (id, parent_id, code, name) VALUES
    ('10000000-0000-0000-0002-000000000001', '10000000-0000-0000-0000-000000000002', 'FOOTWEAR_CASUAL',  'Casual'),
    ('10000000-0000-0000-0002-000000000002', '10000000-0000-0000-0000-000000000002', 'FOOTWEAR_FORMAL',  'Formal'),
    ('10000000-0000-0000-0002-000000000003', '10000000-0000-0000-0000-000000000002', 'FOOTWEAR_SPORT',   'Sport'),
    ('10000000-0000-0000-0002-000000000004', '10000000-0000-0000-0000-000000000002', 'FOOTWEAR_SANDALS', 'Sandals & Thongs')
ON CONFLICT (code) DO NOTHING;
