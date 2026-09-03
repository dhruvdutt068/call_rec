-- ============================================================================
-- Phase 3 Migration: Connect Call Logs to Canonical Person Identity
-- Authoritative Server-Side Identity Linkage & Backfill
-- ============================================================================

-- 1. Helper Function: Canonical Phone Normalization (Identical to Android PhoneNumberNormalizer)
-- Strips non-digits and extracts standard canonical national subscriber number (last 10 digits).
CREATE OR REPLACE FUNCTION public.normalize_phone_number(raw TEXT)
RETURNS TEXT AS $$
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
$$ LANGUAGE plpgsql IMMUTABLE;

-- 2. Add person_id column to sales_calls referencing public.people(id) with ON DELETE SET NULL
ALTER TABLE public.sales_calls 
ADD COLUMN IF NOT EXISTS person_id TEXT REFERENCES public.people(id) ON DELETE SET NULL;

-- 3. Create index on person_id for fast interaction history & 360 CRM queries
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);

-- 4. Safe Backfill: Match existing sales_calls.buyer_phone to phone_numbers using canonical normalization
UPDATE public.sales_calls sc
SET person_id = pn.person_id
FROM public.phone_numbers pn
WHERE sc.person_id IS NULL
  AND pn.normalized_number = public.normalize_phone_number(sc.buyer_phone);

-- 5. Schema & Foreign Key Verification Check
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'sales_calls' 
          AND column_name = 'person_id'
    ) THEN
        RAISE NOTICE 'SUCCESS: sales_calls.person_id column exists and is linked.';
    ELSE
        RAISE EXCEPTION 'ERROR: sales_calls.person_id column was not created.';
    END IF;
END $$;
