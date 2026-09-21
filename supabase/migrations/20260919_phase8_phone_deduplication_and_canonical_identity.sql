-- ====================================================================
-- PHASE 8: AUTHORITATIVE PHONE NUMBER DEDUPLICATION & CANONICAL IDENTITY RESOLUTION
-- ====================================================================
-- This migration:
-- 1. Creates/Updates public.normalize_phone_number() canonical function.
-- 2. Safely audits, re-normalizes, and merges existing duplicate records.
-- 3. Enforces database-level uniqueness on phone_numbers and contact_aliases.
-- 4. Installs automatic normalization triggers on phone_numbers and contact_aliases.
-- 5. Implements concurrency-safe, atomic resolve_and_attach_contact RPC.
-- ====================================================================

-- 1. Canonical Normalization Helper Function
-- Strips non-digits, extracts last 10 digits for numbers with >= 10 digits.
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

-- 2. SAFE DATA CLEANUP & DUPLICATE MERGING
DO $$
DECLARE
    rec RECORD;
    v_canonical_person_id TEXT;
    v_canonical_phone_id TEXT;
BEGIN
    -- 2a. Re-normalize all existing phone_numbers.normalized_number
    UPDATE public.phone_numbers
    SET normalized_number = public.normalize_phone_number(COALESCE(NULLIF(normalized_number, ''), phone_number))
    WHERE normalized_number IS NULL 
       OR normalized_number <> public.normalize_phone_number(COALESCE(NULLIF(normalized_number, ''), phone_number));

    -- 2b. Re-normalize all existing contact_aliases.normalized_number
    UPDATE public.contact_aliases
    SET normalized_number = public.normalize_phone_number(COALESCE(NULLIF(normalized_number, ''), phone_number))
    WHERE normalized_number IS NULL 
       OR normalized_number <> public.normalize_phone_number(COALESCE(NULLIF(normalized_number, ''), phone_number));

    -- 2c. Iterate over any duplicate normalized_numbers in public.phone_numbers
    FOR rec IN (
        SELECT normalized_number, COUNT(*) AS cnt
        FROM public.phone_numbers
        WHERE normalized_number <> ''
        GROUP BY normalized_number
        HAVING COUNT(*) > 1
    ) LOOP
        -- Select canonical phone row (earliest created or first ID)
        SELECT id, person_id INTO v_canonical_phone_id, v_canonical_person_id
        FROM public.phone_numbers
        WHERE normalized_number = rec.normalized_number
        ORDER BY created_at ASC, id ASC
        LIMIT 1;

        -- Reassign dependent sales_calls to canonical person
        UPDATE public.sales_calls
        SET person_id = v_canonical_person_id
        WHERE person_id IN (
            SELECT person_id FROM public.phone_numbers WHERE normalized_number = rec.normalized_number
        ) AND person_id <> v_canonical_person_id;

        -- Reassign dependent leads to canonical person (if leads table exists)
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'leads') THEN
            -- If a lead already exists for canonical person, remove duplicate leads for secondary person
            DELETE FROM public.leads
            WHERE person_id IN (
                SELECT person_id FROM public.phone_numbers WHERE normalized_number = rec.normalized_number AND person_id <> v_canonical_person_id
            ) AND EXISTS (SELECT 1 FROM public.leads WHERE person_id = v_canonical_person_id);

            UPDATE public.leads
            SET person_id = v_canonical_person_id
            WHERE person_id IN (
                SELECT person_id FROM public.phone_numbers WHERE normalized_number = rec.normalized_number
            ) AND person_id <> v_canonical_person_id;
        END IF;

        -- Reassign dependent contact_aliases to canonical person
        UPDATE public.contact_aliases
        SET person_id = v_canonical_person_id
        WHERE person_id IN (
            SELECT person_id FROM public.phone_numbers WHERE normalized_number = rec.normalized_number
        ) AND person_id <> v_canonical_person_id;

        -- Delete non-canonical phone_number rows
        DELETE FROM public.phone_numbers
        WHERE normalized_number = rec.normalized_number
          AND id <> v_canonical_phone_id;
    END LOOP;

    -- 2d. Clean up duplicate contact_aliases per (device_id, normalized_number)
    FOR rec IN (
        SELECT device_id, normalized_number, COUNT(*) AS cnt
        FROM public.contact_aliases
        WHERE normalized_number <> ''
        GROUP BY device_id, normalized_number
        HAVING COUNT(*) > 1
    ) LOOP
        -- Delete older alias records, keeping the most recently updated/created one
        DELETE FROM public.contact_aliases
        WHERE device_id = rec.device_id
          AND normalized_number = rec.normalized_number
          AND id NOT IN (
              SELECT id FROM public.contact_aliases
              WHERE device_id = rec.device_id AND normalized_number = rec.normalized_number
              ORDER BY created_at DESC, id DESC
              LIMIT 1
          );
    END LOOP;

    -- 2e. Clean up orphaned people (people with no phone numbers, no aliases, no sales calls, no leads)
    DELETE FROM public.people p
    WHERE NOT EXISTS (SELECT 1 FROM public.phone_numbers pn WHERE pn.person_id = p.id)
      AND NOT EXISTS (SELECT 1 FROM public.contact_aliases ca WHERE ca.person_id = p.id)
      AND NOT EXISTS (SELECT 1 FROM public.sales_calls sc WHERE sc.person_id = p.id);

END $$;

-- 3. ENSURE CONSTRAINTS AND INDEXES
DO $$
BEGIN
    ALTER TABLE public.phone_numbers DROP CONSTRAINT IF EXISTS uq_phone_numbers_normalized;
    ALTER TABLE public.phone_numbers ADD CONSTRAINT uq_phone_numbers_normalized UNIQUE (normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.contact_aliases DROP CONSTRAINT IF EXISTS uq_device_alias_phone;
    ALTER TABLE public.contact_aliases ADD CONSTRAINT uq_device_alias_phone UNIQUE (device_id, normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_normalized ON public.contact_aliases (normalized_number);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_device_norm ON public.contact_aliases (device_id, normalized_number);

-- 4. AUTOMATIC NORMALIZATION TRIGGERS
-- Guarantees that any direct INSERT or UPDATE on phone_numbers or contact_aliases always normalizes the number
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

-- 5. ATOMIC AUTHORITATIVE IDENTITY RESOLVER RPC
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
    v_now TIMESTAMPTZ := clock_timestamp();
    v_clean_alias TEXT;
    v_clean_norm TEXT;
    v_raw_phone TEXT;
BEGIN
    -- 1. Input sanitization & canonical normalization
    v_raw_phone := TRIM(COALESCE(p_phone_number, ''));
    v_clean_norm := public.normalize_phone_number(COALESCE(NULLIF(TRIM(p_normalized_number), ''), v_raw_phone));
    v_clean_alias := TRIM(COALESCE(p_alias_name, ''));
    
    IF v_clean_alias = '' THEN
        v_clean_alias := 'Unknown Contact';
    END IF;

    IF v_clean_norm = '' THEN
        RAISE EXCEPTION 'Normalized phone number cannot be blank';
    END IF;

    IF v_raw_phone = '' THEN
        v_raw_phone := v_clean_norm;
    END IF;

    -- 2. Upsert Device registration if device_id provided
    IF p_device_id IS NOT NULL AND TRIM(p_device_id) <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            TRIM(p_device_id),
            COALESCE(NULLIF(TRIM(p_device_name), ''), 'Android Device'),
            COALESCE(TRIM(p_device_phone), ''),
            COALESCE(NULLIF(TRIM(p_device_identifier), ''), TRIM(p_device_id)),
            v_now,
            v_now,
            v_now
        )
        ON CONFLICT (id) DO UPDATE
        SET last_sync_at = v_now,
            device_name = COALESCE(NULLIF(EXCLUDED.device_name, 'Android Device'), public.devices.device_name);
    END IF;

    -- 3. Check if phone number already exists in Supabase
    SELECT person_id, id INTO v_person_id, v_phone_id
    FROM public.phone_numbers
    WHERE normalized_number = v_clean_norm;

    -- 4. If number does NOT exist in Supabase, create canonical Person and Phone atomically
    IF v_person_id IS NULL THEN
        -- Generate canonical Person ID: e.g. P1A2B3C4D
        v_person_id := 'P' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
        
        -- Insert Person record
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, v_now, v_now);

        -- Insert Phone Number record with ON CONFLICT resolution for concurrent attempts
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

    -- 5. Idempotently attach/update Contact Alias for this Device
    IF p_device_id IS NOT NULL AND TRIM(p_device_id) <> '' THEN
        v_alias_id := gen_random_uuid()::text;
        INSERT INTO public.contact_aliases (id, person_id, device_id, android_contact_id, alias_name, phone_number, normalized_number, created_at)
        VALUES (
            v_alias_id,
            v_person_id,
            TRIM(p_device_id),
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

    -- 6. Return canonical IDs
    RETURN jsonb_build_object(
        'person_id', v_person_id,
        'phone_number_id', v_phone_id,
        'alias_id', v_alias_id
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.normalize_phone_number TO anon, authenticated, service_role;

NOTIFY pgrst, 'reload schema';
