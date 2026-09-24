-- ====================================================================
-- FIX SALES_CALLS UNIQUE CONSTRAINTS
-- ====================================================================
-- Resolves error 23505: duplicate key value violates unique constraint "uq_sales_call_natural"
-- Ensures the table's ON CONFLICT target (salesperson_phone, call_id) operates cleanly.
-- ====================================================================

-- 1. Drop the conflicting natural key constraint if present
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS uq_sales_call_natural;
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS sales_calls_salesperson_phone_buyer_phone_created_at_key;

-- 2. Ensure standard upsert constraint on (salesperson_phone, call_id)
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS uq_salesperson_call;
ALTER TABLE public.sales_calls ADD CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id);

-- 3. Verify performance indexes exist
CREATE INDEX IF NOT EXISTS idx_sales_calls_salesperson ON public.sales_calls(salesperson_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_buyer ON public.sales_calls(buyer_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);
CREATE INDEX IF NOT EXISTS idx_sales_calls_created_at ON public.sales_calls(created_at);
