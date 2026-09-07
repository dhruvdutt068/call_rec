-- ====================================================================
-- PHASE 4: CRM LEADS & LIFECYCLE MIGRATION
-- (TIMESTAMPTZ / DATETIME DATATYPES + RLS POLICIES)
-- ====================================================================

-- 1. Create leads Table
CREATE TABLE IF NOT EXISTS public.leads (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (status IN ('HOT', 'WARM', 'COLD', 'UNKNOWN')),
    priority TEXT NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    feedback TEXT,
    feedback_rating INTEGER,
    notes TEXT,
    owner_id TEXT,
    source TEXT NOT NULL DEFAULT 'MANUAL',
    next_follow_up_at TIMESTAMPTZ,
    is_archived BOOLEAN NOT NULL DEFAULT false,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_leads_person UNIQUE (person_id)
);

-- 2. Create Indexes for fast querying
CREATE INDEX IF NOT EXISTS idx_leads_person_id ON public.leads (person_id);
CREATE INDEX IF NOT EXISTS idx_leads_status ON public.leads (status);
CREATE INDEX IF NOT EXISTS idx_leads_priority ON public.leads (priority);
CREATE INDEX IF NOT EXISTS idx_leads_next_follow_up ON public.leads (next_follow_up_at);
CREATE INDEX IF NOT EXISTS idx_leads_updated_at ON public.leads (updated_at);

-- 3. Row Level Security Policies
ALTER TABLE public.leads ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow anon all on leads" ON public.leads;
CREATE POLICY "Allow anon all on leads" ON public.leads 
    FOR ALL TO anon, authenticated 
    USING (true) WITH CHECK (true);

-- 4. Table Permissions
GRANT ALL ON TABLE public.leads TO anon, authenticated, service_role;

-- 5. Verification Check
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name = 'leads'
    ) THEN
        RAISE NOTICE 'SUCCESS: public.leads table exists with RLS.';
    ELSE
        RAISE EXCEPTION 'ERROR: public.leads table was not created.';
    END IF;
END $$;

-- 6. Reload schema cache for PostgREST
NOTIFY pgrst, 'reload schema';
