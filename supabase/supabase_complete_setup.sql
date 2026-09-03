-- ====================================================================
-- MASTER SUPABASE SETUP SCRIPT (Run this in Supabase SQL Editor)
-- ====================================================================
-- This script safely creates or updates all required tables, columns,
-- constraints, RLS policies, and RPC functions for AllSet / Callog.
-- ====================================================================

-- 1. SALES_CALLS TABLE
CREATE TABLE IF NOT EXISTS public.sales_calls (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    salesperson_phone TEXT NOT NULL,
    salesperson_name TEXT NOT NULL DEFAULT '',
    buyer_phone TEXT NOT NULL,
    buyer_name TEXT,
    call_type TEXT NOT NULL DEFAULT 'INCOMING',
    call_id BIGINT NOT NULL,
    duration INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    person_id TEXT,
    CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id)
);

CREATE INDEX IF NOT EXISTS idx_sales_calls_salesperson ON public.sales_calls(salesperson_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_buyer ON public.sales_calls(buyer_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);

-- 2. DEVICES TABLE
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL DEFAULT 'Android Device',
    device_phone TEXT NOT NULL DEFAULT '',
    device_identifier TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
    last_sync_at BIGINT
);

-- Safely alter column types if table previously existed with TIMESTAMPTZ
DO $$
BEGIN
    ALTER TABLE public.devices ALTER COLUMN created_at TYPE BIGINT USING (EXTRACT(EPOCH FROM created_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.devices ALTER COLUMN updated_at TYPE BIGINT USING (EXTRACT(EPOCH FROM updated_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.devices ALTER COLUMN last_sync_at TYPE BIGINT USING (EXTRACT(EPOCH FROM last_sync_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.devices ADD CONSTRAINT uq_device_identifier UNIQUE (device_identifier);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_devices_identifier ON public.devices (device_identifier);

-- 3. PEOPLE TABLE (Canonical CRM Person)
CREATE TABLE IF NOT EXISTS public.people (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    company_name TEXT,
    notes TEXT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
);

DO $$
BEGIN
    ALTER TABLE public.people ALTER COLUMN created_at TYPE BIGINT USING (EXTRACT(EPOCH FROM created_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.people ALTER COLUMN updated_at TYPE BIGINT USING (EXTRACT(EPOCH FROM updated_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_people_display_name ON public.people (display_name);

-- 4. PHONE_NUMBERS TABLE
CREATE TABLE IF NOT EXISTS public.phone_numbers (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    phone_type TEXT NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
);

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS normalized_number TEXT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ALTER COLUMN created_at TYPE BIGINT USING (EXTRACT(EPOCH FROM created_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD CONSTRAINT uq_phone_numbers_normalized UNIQUE (normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_phone_numbers_person_id ON public.phone_numbers (person_id);
CREATE INDEX IF NOT EXISTS idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);

-- 5. CONTACT_ALIASES TABLE
CREATE TABLE IF NOT EXISTS public.contact_aliases (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    android_contact_id TEXT NOT NULL DEFAULT '',
    alias_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT
);

DO $$
BEGIN
    ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS normalized_number TEXT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.contact_aliases ALTER COLUMN created_at TYPE BIGINT USING (EXTRACT(EPOCH FROM created_at::timestamptz) * 1000)::BIGINT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

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
-- 6. ATOMIC RPC: resolve_and_attach_contact
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
    v_person_id TEXT;
    v_phone_id TEXT;
    v_alias_id TEXT;
    v_now BIGINT := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
    v_clean_alias TEXT;
    v_clean_norm TEXT;
BEGIN
    -- Input sanitization
    v_clean_norm := trim(p_normalized_number);
    v_clean_alias := trim(COALESCE(p_alias_name, ''));
    IF v_clean_alias = '' THEN
        v_clean_alias := 'Unknown Contact';
    END IF;

    IF v_clean_norm = '' THEN
        RAISE EXCEPTION 'Normalized phone number cannot be blank';
    END IF;

    -- 1. Ensure Device is registered (upsert)
    IF p_device_id IS NOT NULL AND p_device_id <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            p_device_id,
            COALESCE(NULLIF(p_device_name, ''), 'Android Device'),
            COALESCE(p_device_phone, ''),
            COALESCE(NULLIF(p_device_identifier, ''), p_device_id),
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
        -- Generate canonical Person ID: e.g. P1A2B3C4D
        v_person_id := 'P' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
        
        -- Insert Person record
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, v_now, v_now);

        -- Insert Phone Number with ON CONFLICT resolution for concurrent inserts
        v_phone_id := gen_random_uuid()::text;
        INSERT INTO public.phone_numbers (id, person_id, phone_number, normalized_number, phone_type, is_primary, created_at)
        VALUES (v_phone_id, v_person_id, p_phone_number, v_clean_norm, 'PRIMARY', true, v_now)
        ON CONFLICT (normalized_number) DO UPDATE
        SET phone_number = EXCLUDED.phone_number
        RETURNING person_id, id INTO v_person_id, v_phone_id;
    END IF;

    -- 4. Idempotently attach/update Contact Alias for this Device
    v_alias_id := gen_random_uuid()::text;
    INSERT INTO public.contact_aliases (id, person_id, device_id, android_contact_id, alias_name, phone_number, normalized_number, created_at)
    VALUES (
        v_alias_id,
        v_person_id,
        p_device_id,
        COALESCE(p_android_contact_id, ''),
        v_clean_alias,
        p_phone_number,
        v_clean_norm,
        v_now
    )
    ON CONFLICT (device_id, normalized_number) DO UPDATE
    SET alias_name = EXCLUDED.alias_name,
        android_contact_id = EXCLUDED.android_contact_id,
        phone_number = EXCLUDED.phone_number,
        person_id = v_person_id
    RETURNING id INTO v_alias_id;

    RETURN jsonb_build_object(
        'person_id', v_person_id,
        'phone_number_id', v_phone_id,
        'alias_id', v_alias_id
    );
END;
$$;

-- ====================================================================
-- 7. ENABLE PERMISSIONS & ROW LEVEL SECURITY (RLS)
-- ====================================================================
ALTER TABLE public.sales_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.people ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_aliases ENABLE ROW LEVEL SECURITY;

-- Allow anon and authenticated full access (or adjust according to your auth model)
DROP POLICY IF EXISTS "Allow anon all on sales_calls" ON public.sales_calls;
CREATE POLICY "Allow anon all on sales_calls" ON public.sales_calls FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on devices" ON public.devices;
CREATE POLICY "Allow anon all on devices" ON public.devices FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on people" ON public.people;
CREATE POLICY "Allow anon all on people" ON public.people FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on phone_numbers" ON public.phone_numbers;
CREATE POLICY "Allow anon all on phone_numbers" ON public.phone_numbers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on contact_aliases" ON public.contact_aliases;
CREATE POLICY "Allow anon all on contact_aliases" ON public.contact_aliases FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- Grant schema permissions
GRANT ALL ON TABLE public.sales_calls TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.people TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.phone_numbers TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.contact_aliases TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;

-- 8. RELOAD POSTGREST SCHEMA CACHE
NOTIFY pgrst, 'reload schema';
