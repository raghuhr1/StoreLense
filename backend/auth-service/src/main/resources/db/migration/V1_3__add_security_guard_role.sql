SET search_path TO auth;

INSERT INTO roles (id, name, description) VALUES
    ('00000000-0000-0000-0000-000000000005', 'SECURITY_GUARD', 'C66 exit gate — scan bill QR + RFID bag, mark EPCs sold')
ON CONFLICT (name) DO NOTHING;
