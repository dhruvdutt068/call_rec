-- ====================================================================
-- FOCUSED MIGRATION: 4 IDENTITY RESOLUTION TABLES ONLY
-- ====================================================================
-- Tables Created:
--   1. people          (Canonical CRM person identity)
--   2. devices         (Android device hardware registration)
--   3. phone_numbers   (Canonical phone numbers with unique normalized index)
--   4. contact_aliases (Device-specific address book contacts)
--
-- Tables Updated:
--   • sales_calls      (Adds person_id foreign key & ensures upsert constraint)
--
-- Automations & RPC:
--   • normalize_phone_number(raw TEXT)
--   • Triggers on phone_numbers & contact_aliases for auto-normalization
--   • Triggers on contact_aliases & sales_calls for deduplication & canonical resolution
--   • cleanup_orphaned_people() procedure
--   • resolve_and_attach_contact(...) RPC
--   • Row Level Security (RLS) policies and grants
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ====================================================================
-- 1. CANONICAL PHONE NORMALIZATION FUNCTION
-- ====================================================================
CREATE OR REPLACE FUNCTION public.normalize_phone_number(raw TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    digits TEXT;
BEGIN
    IF raw IS NULL OR TRIM(raw) = '' THEN
        RETURN '';
    END IF;
    digits := REGEXP_REPLACE(raw, '\D', '', 'g');
    IF LENGTH(digits) >= 10 THEN
        RETURN RIGHT(digits, 10);
    ELSE
        RETURN digits;
    END IF;
END;
$$;

-- ====================================================================
-- 2. PEOPLE TABLE (Canonical CRM Person Identity)
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.people (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    company_name TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.people ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS company_name TEXT;
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_people_display_name ON public.people (display_name);

-- ====================================================================
-- 3. DEVICES TABLE (Android Device Hardware Registry)
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL DEFAULT 'Android Device',
    device_phone TEXT NOT NULL DEFAULT '',
    device_identifier TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sync_at TIMESTAMPTZ
);

ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_name TEXT NOT NULL DEFAULT 'Android Device';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_phone TEXT NOT NULL DEFAULT '';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_identifier TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMPTZ;

DO $$
BEGIN
    ALTER TABLE public.devices ADD CONSTRAINT uq_device_identifier UNIQUE (device_identifier);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_devices_identifier ON public.devices (device_identifier);

-- ====================================================================
-- 4. PHONE_NUMBERS TABLE (Canonical Phone Registry)
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.phone_numbers (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    phone_type TEXT NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS normalized_number TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS phone_type TEXT NOT NULL DEFAULT 'PRIMARY';
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD CONSTRAINT uq_phone_numbers_normalized UNIQUE (normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_phone_numbers_person_id ON public.phone_numbers (person_id);
CREATE INDEX IF NOT EXISTS idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);

-- ====================================================================
-- 5. CONTACT_ALIASES TABLE (Device Address Book Aliases)
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.contact_aliases (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    android_contact_id TEXT NOT NULL DEFAULT '',
    alias_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS device_id TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS android_contact_id TEXT NOT NULL DEFAULT '';
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS alias_name TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS normalized_number TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    ALTER TABLE public.contact_aliases ADD CONSTRAINT uq_device_alias_phone UNIQUE (device_id, normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_contact_aliases_person_id ON public.contact_aliases (person_id);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_device_id ON public.contact_aliases (device_id);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_normalized ON public.contact_aliases (normalized_number);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_alias_name ON public.contact_aliases (alias_name);

-- ====================================================================
-- 6. LINK SALES_CALLS TO PEOPLE
-- ====================================================================
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS person_id TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = 'public' 
          AND table_name = 'sales_calls' 
          AND constraint_name = 'fk_sales_calls_person_id'
    ) THEN
        ALTER TABLE public.sales_calls 
        ADD CONSTRAINT fk_sales_calls_person_id 
        FOREIGN KEY (person_id) REFERENCES public.people(id) ON DELETE SET NULL;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- Drop legacy conflicting constraints if present
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS uq_sales_call_natural;
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS sales_calls_salesperson_phone_buyer_phone_created_at_key;

DO $$
BEGIN
    ALTER TABLE public.sales_calls ADD CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);

-- ====================================================================
-- 7. NORMALIZATION & CANONICAL DEDUPLICATION TRIGGERS
-- ====================================================================

-- 7.1 Auto-normalization trigger
CREATE OR REPLACE FUNCTION public.trg_normalize_phone_number()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.normalized_number IS NULL OR TRIM(NEW.normalized_number) = '' THEN
        NEW.normalized_number := public.normalize_phone_number(NEW.phone_number);
    ELSE
        NEW.normalized_number := public.normalize_phone_number(NEW.normalized_number);
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_phone_numbers_normalize ON public.phone_numbers;
CREATE TRIGGER trg_phone_numbers_normalize
BEFORE INSERT OR UPDATE ON public.phone_numbers
FOR EACH ROW EXECUTE FUNCTION public.trg_normalize_phone_number();

DROP TRIGGER IF EXISTS trg_contact_aliases_normalize ON public.contact_aliases;
CREATE TRIGGER trg_contact_aliases_normalize
BEFORE INSERT OR UPDATE ON public.contact_aliases
FOR EACH ROW EXECUTE FUNCTION public.trg_normalize_phone_number();

-- 7.2 Contact aliases enforce canonical person_id
CREATE OR REPLACE FUNCTION public.trg_enforce_canonical_person_on_alias()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_clean_norm TEXT;
    v_canonical_person_id TEXT;
BEGIN
    v_clean_norm := public.normalize_phone_number(COALESCE(NULLIF(TRIM(NEW.normalized_number), ''), NEW.phone_number));
    
    IF v_clean_norm IS NOT NULL AND v_clean_norm <> '' THEN
        SELECT person_id INTO v_canonical_person_id
        FROM public.phone_numbers
        WHERE normalized_number = v_clean_norm
        LIMIT 1;

        IF v_canonical_person_id IS NOT NULL AND v_canonical_person_id <> NEW.person_id THEN
            NEW.person_id := v_canonical_person_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_contact_aliases_enforce_canonical ON public.contact_aliases;
CREATE TRIGGER trg_contact_aliases_enforce_canonical
BEFORE INSERT OR UPDATE ON public.contact_aliases
FOR EACH ROW EXECUTE FUNCTION public.trg_enforce_canonical_person_on_alias();

-- 7.3 Sales calls enforce canonical person_id
CREATE OR REPLACE FUNCTION public.trg_enforce_canonical_person_on_call()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_clean_buyer_norm TEXT;
    v_canonical_person_id TEXT;
BEGIN
    IF NEW.buyer_phone IS NOT NULL AND NEW.buyer_phone <> '' THEN
        v_clean_buyer_norm := public.normalize_phone_number(NEW.buyer_phone);
        IF v_clean_buyer_norm <> '' THEN
            SELECT person_id INTO v_canonical_person_id
            FROM public.phone_numbers
            WHERE normalized_number = v_clean_buyer_norm
            LIMIT 1;

            IF v_canonical_person_id IS NOT NULL THEN
                NEW.person_id := v_canonical_person_id;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sales_calls_enforce_canonical ON public.sales_calls;
CREATE TRIGGER trg_sales_calls_enforce_canonical
BEFORE INSERT OR UPDATE ON public.sales_calls
FOR EACH ROW EXECUTE FUNCTION public.trg_enforce_canonical_person_on_call();

-- 7.4 Auto-clean orphaned duplicate people
CREATE OR REPLACE FUNCTION public.cleanup_orphaned_people()
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_deleted_count INTEGER := 0;
BEGIN
    WITH deleted AS (
        DELETE FROM public.people p
        WHERE NOT EXISTS (SELECT 1 FROM public.phone_numbers pn WHERE pn.person_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM public.contact_aliases ca WHERE ca.person_id = p.id)
          AND NOT EXISTS (SELECT 1 FROM public.sales_calls sc WHERE sc.person_id = p.id)
        RETURNING p.id
    )
    SELECT COUNT(*) INTO v_deleted_count FROM deleted;
    
    RETURN v_deleted_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.trg_after_alias_cleanup_orphans()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM public.cleanup_orphaned_people();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_contact_aliases_cleanup_orphans ON public.contact_aliases;
CREATE TRIGGER trg_contact_aliases_cleanup_orphans
AFTER INSERT OR UPDATE ON public.contact_aliases
FOR EACH STATEMENT EXECUTE FUNCTION public.trg_after_alias_cleanup_orphans();

-- ====================================================================
-- 8. ATOMIC RPC: resolve_and_attach_contact (Used by Mobile App)
-- ====================================================================
CREATE OR REPLACE FUNCTION public.resolve_and_attach_contact(
    p_device_id TEXT,
    p_android_contact_id TEXT,
    p_alias_name TEXT,
    p_phone_number TEXT,
    p_normalized_number TEXT,
    p_device_name TEXT DEFAULT NULL,
    p_device_phone TEXT DEFAULT NULL,
    p_device_identifier TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_raw_phone TEXT;
    v_clean_norm TEXT;
    v_clean_alias TEXT;
    v_person_id TEXT;
    v_phone_id TEXT;
    v_alias_id TEXT;
    v_now TIMESTAMPTZ := clock_timestamp();
    v_device_key TEXT;
BEGIN
    v_raw_phone := TRIM(COALESCE(p_phone_number, ''));
    v_clean_norm := public.normalize_phone_number(COALESCE(NULLIF(TRIM(p_normalized_number), ''), v_raw_phone));
    v_clean_alias := TRIM(COALESCE(p_alias_name, ''));
    v_device_key := COALESCE(NULLIF(TRIM(p_device_id), ''), NULLIF(TRIM(p_device_identifier), ''));
    
    IF v_clean_alias = '' THEN
        v_clean_alias := 'Unknown Contact';
    END IF;

    IF v_clean_norm = '' THEN
        RAISE EXCEPTION 'Normalized phone number cannot be blank';
    END IF;

    IF v_raw_phone = '' THEN
        v_raw_phone := v_clean_norm;
    END IF;

    -- 1. Ensure Device is registered (upsert)
    IF v_device_key IS NOT NULL AND v_device_key <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            v_device_key,
            COALESCE(NULLIF(TRIM(p_device_name), ''), 'Android Device'),
            COALESCE(TRIM(p_device_phone), ''),
            COALESCE(NULLIF(TRIM(p_device_identifier), ''), v_device_key),
            v_now,
            v_now,
            v_now
        )
        ON CONFLICT (id) DO UPDATE
        SET last_sync_at = v_now,
            device_name = COALESCE(NULLIF(EXCLUDED.device_name, 'Android Device'), public.devices.device_name);
    END IF;

    -- 2. Check if a phone record already exists for this normalized number
    SELECT person_id, id INTO v_person_id, v_phone_id
    FROM public.phone_numbers
    WHERE normalized_number = v_clean_norm;

    -- 3. If no person found, atomically create canonical Person and Phone record
    IF v_person_id IS NULL THEN
        v_person_id := 'P' || UPPER(SUBSTR(MD5(RANDOM()::TEXT || clock_timestamp()::TEXT), 1, 8));
        
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, v_now, v_now);

        v_phone_id := gen_random_uuid()::text;
        INSERT INTO public.phone_numbers (id, person_id, phone_number, normalized_number, phone_type, is_primary, created_at)
        VALUES (v_phone_id, v_person_id, v_raw_phone, v_clean_norm, 'PRIMARY', true, v_now)
        ON CONFLICT (normalized_number) DO UPDATE
        SET phone_number = EXCLUDED.phone_number
        RETURNING person_id, id INTO v_person_id, v_phone_id;

        PERFORM public.cleanup_orphaned_people();
    END IF;

    -- 4. Idempotently attach/update Contact Alias for this Device
    IF v_device_key IS NOT NULL AND v_device_key <> '' THEN
        v_alias_id := gen_random_uuid()::text;
        INSERT INTO public.contact_aliases (id, person_id, device_id, android_contact_id, alias_name, phone_number, normalized_number, created_at)
        VALUES (
            v_alias_id,
            v_person_id,
            v_device_key,
            COALESCE(TRIM(p_android_contact_id), ''),
            v_clean_alias,
            v_raw_phone,
            v_clean_norm,
            v_now
        )
        ON CONFLICT (device_id, normalized_number) DO UPDATE
        SET alias_name = EXCLUDED.alias_name,
            android_contact_id = EXCLUDED.android_contact_id,
            phone_number = EXCLUDED.phone_number,
            person_id = v_person_id
        RETURNING id INTO v_alias_id;
    END IF;

    RETURN jsonb_build_object(
        'person_id', v_person_id,
        'phone_number_id', v_phone_id,
        'alias_id', v_alias_id
    );
END;
$$;

-- ====================================================================
-- 9. ROW LEVEL SECURITY & PERMISSIONS
-- ====================================================================
ALTER TABLE public.people ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_aliases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales_calls ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow anon all on people" ON public.people;
CREATE POLICY "Allow anon all on people" ON public.people FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on devices" ON public.devices;
CREATE POLICY "Allow anon all on devices" ON public.devices FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on phone_numbers" ON public.phone_numbers;
CREATE POLICY "Allow anon all on phone_numbers" ON public.phone_numbers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on contact_aliases" ON public.contact_aliases;
CREATE POLICY "Allow anon all on contact_aliases" ON public.contact_aliases FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on sales_calls" ON public.sales_calls;
CREATE POLICY "Allow anon all on sales_calls" ON public.sales_calls FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

GRANT ALL ON TABLE public.people TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.phone_numbers TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.contact_aliases TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.sales_calls TO anon, authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.normalize_phone_number TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cleanup_orphaned_people TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;

-- ====================================================================
-- 10. RELOAD POSTGREST SCHEMA CACHE
-- ====================================================================
NOTIFY pgrst, 'reload schema';
