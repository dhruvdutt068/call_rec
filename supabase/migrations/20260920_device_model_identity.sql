-- ====================================================================
-- PHASE 9: DEVICE MODEL IDENTITY & UNINSTALL-SAFE CONTACT RESOLUTION
-- ====================================================================
-- This migration:
-- 1. Establishes the device model number (e.g. 'Pixel 8 Pro', 'SM-S928B') as
--    the authoritative device identity in public.devices and public.contact_aliases.
-- 2. Ensures resolve_and_attach_contact RPC deterministically upserts devices
--    using the model number, preventing duplicate device or alias creation on
--    app uninstalls or reinstalls.
-- ====================================================================

-- 1. Ensure public.devices has unique device_identifier and proper primary key
DO $$
BEGIN
    ALTER TABLE public.devices ADD CONSTRAINT uq_device_identifier UNIQUE (device_identifier);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_devices_identifier ON public.devices (device_identifier);

-- 2. Ensure public.contact_aliases has unique constraint on (device_id, normalized_number)
DO $$
BEGIN
    ALTER TABLE public.contact_aliases ADD CONSTRAINT uq_contact_aliases_device_phone UNIQUE (device_id, normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 3. Canonical RPC resolve_and_attach_contact accepting device model as p_device_id
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
    v_now TIMESTAMPTZ;
    v_device_key TEXT;
BEGIN
    v_now := clock_timestamp();
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

    -- 1. Ensure Device Model is registered (upsert)
    IF v_device_key IS NOT NULL AND v_device_key <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            v_device_key,
            COALESCE(NULLIF(TRIM(p_device_name), ''), v_device_key),
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
        -- Generate canonical Person ID: e.g. P1A2B3C4D
        v_person_id := 'P' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
        
        -- Insert Person record
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, v_now, v_now);

        -- Insert Phone Number with ON CONFLICT resolution for concurrent inserts
        v_phone_id := gen_random_uuid()::text;
        INSERT INTO public.phone_numbers (id, person_id, phone_number, normalized_number, phone_type, is_primary, created_at)
        VALUES (v_phone_id, v_person_id, v_raw_phone, v_clean_norm, 'PRIMARY', true, v_now)
        ON CONFLICT (normalized_number) DO UPDATE
        SET phone_number = EXCLUDED.phone_number
        RETURNING person_id, id INTO v_person_id, v_phone_id;

        -- Clean up orphaned person record if another concurrent transaction inserted the phone first
        DELETE FROM public.people
        WHERE id <> v_person_id
          AND NOT EXISTS (SELECT 1 FROM public.phone_numbers pn WHERE pn.person_id = people.id)
          AND NOT EXISTS (SELECT 1 FROM public.contact_aliases ca WHERE ca.person_id = people.id)
          AND NOT EXISTS (SELECT 1 FROM public.sales_calls sc WHERE sc.person_id = people.id);
    END IF;

    -- 4. Idempotently attach/update Contact Alias for this Device Model
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
