-- ====================================================================
-- ROOT-LEVEL FIX: PREVENT DUPLICATE PEOPLE AND ENFORCE CANONICAL IDENTITY
-- ====================================================================
-- Problem: When a client resolves an address book contact while offline,
-- it creates a temporary local person ID. If that client later syncs its
-- contact_aliases, it can create a duplicate person record even though
-- the phone_number was already claimed by an existing canonical person.
--
-- Solution:
-- 1. Database trigger BEFORE INSERT/UPDATE on contact_aliases to automatically
--    route the alias to the canonical person_id from phone_numbers.
-- 2. Database trigger BEFORE INSERT/UPDATE on sales_calls to automatically
--    link person_id to the canonical person_id from phone_numbers.
-- 3. Automatic cleanup of orphaned duplicate people who have no phone numbers,
--    no aliases, and no calls.
-- 4. One-time deduplication cleanup for existing records (e.g. P2237E59F -> P7637EE6C).
-- ====================================================================

-- 1. Trigger Function: Enforce canonical person_id on contact_aliases
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
        -- Find existing canonical person who owns this normalized phone number
        SELECT person_id INTO v_canonical_person_id
        FROM public.phone_numbers
        WHERE normalized_number = v_clean_norm
        LIMIT 1;

        IF v_canonical_person_id IS NOT NULL AND v_canonical_person_id <> NEW.person_id THEN
            -- Reassign alias to the true canonical person
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


-- 2. Trigger Function: Enforce canonical person_id on sales_calls
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


-- 3. Procedure: Auto-cleanup of orphaned people
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


-- 4. Trigger AFTER INSERT/UPDATE on contact_aliases to clean up newly orphaned people
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
-- 5. IMMEDIATE DATA CLEANUP: Merge existing duplicates
-- ====================================================================
-- Reassign any contact aliases pointing to duplicate person IDs
UPDATE public.contact_aliases ca
SET person_id = pn.person_id
FROM public.phone_numbers pn
WHERE public.normalize_phone_number(ca.normalized_number) = pn.normalized_number
  AND ca.person_id <> pn.person_id;

-- Reassign any sales calls pointing to duplicate or NULL person IDs
UPDATE public.sales_calls sc
SET person_id = pn.person_id
FROM public.phone_numbers pn
WHERE public.normalize_phone_number(sc.buyer_phone) = pn.normalized_number
  AND (sc.person_id IS NULL OR sc.person_id <> pn.person_id);

-- Remove all orphaned people without phones, aliases, or calls (including P2237E59F)
SELECT public.cleanup_orphaned_people();
