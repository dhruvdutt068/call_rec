-- ====================================================================
-- MASTER SUPABASE SETUP (DATETIME / TIMESTAMPTZ DATATYPES)
-- ====================================================================
-- Recreates identity tables using PostgreSQL TIMESTAMPTZ (datetime)
-- columns with default NOW() and full compatibility with the mobile app.
-- (sales_calls is preserved if it already exists!)
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    person_id TEXT,
    CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id)
);

CREATE INDEX IF NOT EXISTS idx_sales_calls_salesperson ON public.sales_calls(salesperson_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_buyer ON public.sales_calls(buyer_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);

-- 2. DROP OLD IDENTITY TABLES
DROP TABLE IF EXISTS public.contact_aliases CASCADE;
DROP TABLE IF EXISTS public.phone_numbers CASCADE;
DROP TABLE IF EXISTS public.people CASCADE;
DROP TABLE IF EXISTS public.devices CASCADE;

-- 3. RECREATE DEVICES TABLE (DATETIME / TIMESTAMPTZ)
CREATE TABLE public.devices (
    id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL DEFAULT 'Android Device',
    device_phone TEXT NOT NULL DEFAULT '',
    device_identifier TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sync_at TIMESTAMPTZ
);

CREATE INDEX idx_devices_identifier ON public.devices (device_identifier);

-- 4. RECREATE PEOPLE TABLE (Canonical CRM Person)
CREATE TABLE public.people (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    company_name TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_people_display_name ON public.people (display_name);

-- 5. RECREATE PHONE_NUMBERS TABLE
CREATE TABLE public.phone_numbers (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL UNIQUE,
    phone_type TEXT NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_phone_numbers_person_id ON public.phone_numbers (person_id);
CREATE INDEX idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);

-- 6. RECREATE CONTACT_ALIASES TABLE
CREATE TABLE public.contact_aliases (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    android_contact_id TEXT NOT NULL DEFAULT '',
    alias_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_device_alias_phone UNIQUE (device_id, normalized_number)
);

CREATE INDEX idx_contact_aliases_person_id ON public.contact_aliases (person_id);
CREATE INDEX idx_contact_aliases_device_id ON public.contact_aliases (device_id);
CREATE INDEX idx_contact_aliases_normalized ON public.contact_aliases (normalized_number);
CREATE INDEX idx_contact_aliases_alias_name ON public.contact_aliases (alias_name);

-- 7. RECREATE ATOMIC RPC FUNCTION
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
    v_clean_alias TEXT;
    v_clean_norm TEXT;
BEGIN
    v_clean_norm := trim(p_normalized_number);
    v_clean_alias := trim(COALESCE(p_alias_name, ''));
    IF v_clean_alias = '' THEN v_clean_alias := 'Unknown Contact'; END IF;
    IF v_clean_norm = '' THEN RAISE EXCEPTION 'Normalized phone number cannot be blank'; END IF;

    IF p_device_id IS NOT NULL AND p_device_id <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            p_device_id,
            COALESCE(NULLIF(p_device_name, ''), 'Android Device'),
            COALESCE(p_device_phone, ''),
            COALESCE(NULLIF(p_device_identifier, ''), p_device_id),
            NOW(),
            NOW(),
            NOW()
        )
        ON CONFLICT (id) DO UPDATE
        SET last_sync_at = NOW(),
            device_name = COALESCE(NULLIF(EXCLUDED.device_name, 'Android Device'), public.devices.device_name);
    END IF;

    SELECT person_id, id INTO v_person_id, v_phone_id
    FROM public.phone_numbers
    WHERE normalized_number = v_clean_norm;

    IF v_person_id IS NULL THEN
        v_person_id := 'P' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
        
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, NOW(), NOW());

        v_phone_id := gen_random_uuid()::text;
        INSERT INTO public.phone_numbers (id, person_id, phone_number, normalized_number, phone_type, is_primary, created_at)
        VALUES (v_phone_id, v_person_id, p_phone_number, v_clean_norm, 'PRIMARY', true, NOW())
        ON CONFLICT (normalized_number) DO UPDATE
        SET phone_number = EXCLUDED.phone_number
        RETURNING person_id, id INTO v_person_id, v_phone_id;
    END IF;

    v_alias_id := gen_random_uuid()::text;
    INSERT INTO public.contact_aliases (id, person_id, device_id, android_contact_id, alias_name, phone_number, normalized_number, created_at)
    VALUES (
        v_alias_id, v_person_id, p_device_id,
        COALESCE(p_android_contact_id, ''),
        v_clean_alias, p_phone_number, v_clean_norm, NOW()
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

-- 8. ROW LEVEL SECURITY POLICIES
ALTER TABLE public.sales_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.people ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_aliases ENABLE ROW LEVEL SECURITY;

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

-- 9. PERMISSIONS
GRANT ALL ON TABLE public.sales_calls TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.people TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.phone_numbers TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.contact_aliases TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;

-- 10. NOTIFY POSTGREST
NOTIFY pgrst, 'reload schema';
