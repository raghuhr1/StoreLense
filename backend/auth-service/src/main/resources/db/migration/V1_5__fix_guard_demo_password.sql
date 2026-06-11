SET search_path TO auth;

-- Fix: guard_demo was seeded with the same hash as admin (Admin@StoreLense1).
-- Correct password is Guard@StoreLense1 (bcrypt cost=12).
UPDATE users
SET    password_hash = '$2b$12$s0LsjWxv6jpITvQOLQ7eQO1II3ZhfRJm42.uOSjPFWMmDK3g.yyiO',
       updated_at    = now()
WHERE  username = 'guard_demo';
