-- ====================================================================
-- PHONE NUMBER DEDUPLICATION AUDIT & DIAGNOSTIC QUERIES
-- ====================================================================
-- Run these queries in Supabase SQL Editor to inspect database state
-- ====================================================================

-- A. Exact normalized duplicates in phone_numbers
SELECT normalized_number, COUNT(*) AS count, array_agg(id) AS phone_ids, array_agg(person_id) AS person_ids
FROM public.phone_numbers
WHERE normalized_number <> ''
GROUP BY normalized_number
HAVING COUNT(*) > 1;

-- B. Formatting duplicates (raw phone_number has different representations that normalize to same digits)
SELECT public.normalize_phone_number(phone_number) AS canonical_norm, 
       COUNT(DISTINCT phone_number) AS distinct_formats,
       array_agg(DISTINCT phone_number) AS raw_formats,
       COUNT(*) AS total_rows
FROM public.phone_numbers
GROUP BY public.normalize_phone_number(phone_number)
HAVING COUNT(DISTINCT phone_number) > 1 OR COUNT(*) > 1;

-- C. Same phone number linked to multiple people
SELECT pn.normalized_number, COUNT(DISTINCT pn.person_id) AS distinct_people, array_agg(DISTINCT p.display_name) AS person_names
FROM public.phone_numbers pn
JOIN public.people p ON p.id = pn.person_id
GROUP BY pn.normalized_number
HAVING COUNT(DISTINCT pn.person_id) > 1;

-- D. Duplicate contact aliases per (device_id, normalized_number)
SELECT device_id, normalized_number, COUNT(*) AS alias_count, array_agg(id) AS alias_ids, array_agg(alias_name) AS alias_names
FROM public.contact_aliases
GROUP BY device_id, normalized_number
HAVING COUNT(*) > 1;

-- E. Orphaned phone numbers (person_id does not exist in people table)
SELECT pn.*
FROM public.phone_numbers pn
LEFT JOIN public.people p ON p.id = pn.person_id
WHERE p.id IS NULL;

-- F. Orphaned aliases (person_id does not exist in people table OR device_id does not exist in devices)
SELECT ca.*
FROM public.contact_aliases ca
LEFT JOIN public.people p ON p.id = ca.person_id
LEFT JOIN public.devices d ON d.id = ca.deviceId
WHERE p.id IS NULL OR d.id IS NULL;

-- G. Orphaned people (people with no phone numbers, no aliases, and no sales calls)
SELECT p.*
FROM public.people p
LEFT JOIN public.phone_numbers pn ON pn.person_id = p.id
LEFT JOIN public.contact_aliases ca ON ca.person_id = p.id
LEFT JOIN public.sales_calls sc ON sc.person_id = p.id
WHERE pn.id IS NULL AND ca.id IS NULL AND sc.id IS NULL;
