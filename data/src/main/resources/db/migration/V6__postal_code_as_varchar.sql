DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pos WHERE postal_code < 0 OR postal_code > 99999) THEN
        RAISE EXCEPTION 'pos.postal_code contains values outside 0..99999; clean them up before migrating';
    END IF;
END $$;

ALTER TABLE pos ALTER COLUMN postal_code TYPE varchar(5) USING lpad(postal_code::text, 5, '0');
ALTER TABLE pos ADD CONSTRAINT ck_pos_postal_code CHECK (postal_code ~ '^[0-9]{5}$');
