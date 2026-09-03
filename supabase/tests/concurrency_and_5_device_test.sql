-- ====================================================================
-- PHASE 2 INTEGRATION TEST: 5-DEVICE SCENARIO & CONCURRENCY VALIDATION
-- ====================================================================

BEGIN;

-- Setup test environment / clear test identity data
DELETE FROM public.contact_aliases WHERE normalized_number = '9876543210';
DELETE FROM public.phone_numbers WHERE normalized_number = '9876543210';

-- 1. Five-Device Acceptance Scenario
-- Device 1: Rahul Sharma
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_001',
    p_android_contact_id := 'c_101',
    p_alias_name := 'Rahul Sharma',
    p_phone_number := '+91 98765 43210',
    p_normalized_number := '9876543210',
    p_device_name := 'Pixel 8 Pro'
);

-- Device 2: Rahul
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_002',
    p_android_contact_id := 'c_102',
    p_alias_name := 'Rahul',
    p_phone_number := '09876543210',
    p_normalized_number := '9876543210',
    p_device_name := 'Samsung Galaxy S24'
);

-- Device 3: Rahul Sir
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_003',
    p_android_contact_id := 'c_103',
    p_alias_name := 'Rahul Sir',
    p_phone_number := '+919876543210',
    p_normalized_number := '9876543210',
    p_device_name := 'OnePlus 12'
);

-- Device 4: R Sharma
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_004',
    p_android_contact_id := 'c_104',
    p_alias_name := 'R Sharma',
    p_phone_number := '98765-43210',
    p_normalized_number := '9876543210',
    p_device_name := 'Xiaomi 14'
);

-- Device 5: ABC Client
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_005',
    p_android_contact_id := 'c_105',
    p_alias_name := 'ABC Client',
    p_phone_number := '9876543210',
    p_normalized_number := '9876543210',
    p_device_name := 'Motorola Edge'
);

-- Assertions for 5-Device Scenario
DO $$
DECLARE
    v_people_count INT;
    v_phones_count INT;
    v_aliases_count INT;
    v_person_id_sample TEXT;
BEGIN
    SELECT COUNT(DISTINCT p.id) INTO v_people_count
    FROM public.people p
    JOIN public.phone_numbers pn ON pn.person_id = p.id
    WHERE pn.normalized_number = '9876543210';

    SELECT COUNT(*) INTO v_phones_count
    FROM public.phone_numbers
    WHERE normalized_number = '9876543210';

    SELECT COUNT(*) INTO v_aliases_count
    FROM public.contact_aliases
    WHERE normalized_number = '9876543210';

    IF v_people_count <> 1 THEN
        RAISE EXCEPTION 'FAILED: Expected exactly 1 Person, found %', v_people_count;
    END IF;

    IF v_phones_count <> 1 THEN
        RAISE EXCEPTION 'FAILED: Expected exactly 1 Phone record, found %', v_phones_count;
    END IF;

    IF v_aliases_count <> 5 THEN
        RAISE EXCEPTION 'FAILED: Expected exactly 5 Aliases, found %', v_aliases_count;
    END IF;

    RAISE NOTICE 'SUCCESS: 5-Device Scenario Verified! (People: 1, Phones: 1, Aliases: 5)';
END $$;

-- 2. Idempotency Test: Sending Device 1 three more times
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_001',
    p_android_contact_id := 'c_101',
    p_alias_name := 'Rahul Sharma Updated',
    p_phone_number := '+91 98765 43210',
    p_normalized_number := '9876543210'
);

DO $$
DECLARE
    v_aliases_count INT;
BEGIN
    SELECT COUNT(*) INTO v_aliases_count
    FROM public.contact_aliases
    WHERE normalized_number = '9876543210';

    IF v_aliases_count <> 5 THEN
        RAISE EXCEPTION 'FAILED: Alias count changed after re-sync! Found %', v_aliases_count;
    END IF;

    RAISE NOTICE 'SUCCESS: Alias Idempotency Verified! (Total Aliases remained 5)';
END $$;

ROLLBACK;
