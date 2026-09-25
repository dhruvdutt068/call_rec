-- ====================================================================
-- MASTER IDEMPOTENT SUPABASE MIGRATION SCRIPT
-- ====================================================================
-- Safe to run on ANY database:
-- 1. IF TABLE/COLUMN EXISTS: KEEPS existing tables, columns, and data intact.
-- 2. IF NOT EXISTS: Safely creates missing tables, columns, constraints, and indexes.
-- 3. Idempotently updates functions, triggers, RLS policies, and grants.
-- ====================================================================

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ====================================================================
-- 1. CANONICAL PHONE NORMALIZATION FUNCTION (Required for Triggers & RPCs)
-- ====================================================================
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

-- ====================================================================
-- 2. CORE IDENTITY TABLES
-- ====================================================================

-- 2.1 PEOPLE TABLE (Canonical CRM Person)
CREATE TABLE IF NOT EXISTS public.people (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    company_name TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.people ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS company_name TEXT;
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.people ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_people_display_name ON public.people (display_name);

-- 2.2 DEVICES TABLE
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL DEFAULT 'Android Device',
    device_phone TEXT NOT NULL DEFAULT '',
    device_identifier TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sync_at TIMESTAMPTZ
);

ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_name TEXT NOT NULL DEFAULT 'Android Device';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_phone TEXT NOT NULL DEFAULT '';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS device_identifier TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMPTZ;

DO $$
BEGIN
    ALTER TABLE public.devices ADD CONSTRAINT uq_device_identifier UNIQUE (device_identifier);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_devices_identifier ON public.devices (device_identifier);

-- 2.3 PHONE_NUMBERS TABLE
CREATE TABLE IF NOT EXISTS public.phone_numbers (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    phone_type TEXT NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS normalized_number TEXT;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS phone_type TEXT NOT NULL DEFAULT 'PRIMARY';
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD CONSTRAINT uq_phone_numbers_normalized UNIQUE (normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_phone_numbers_person_id ON public.phone_numbers (person_id);
CREATE INDEX IF NOT EXISTS idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);

-- 2.4 CONTACT_ALIASES TABLE
CREATE TABLE IF NOT EXISTS public.contact_aliases (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    android_contact_id TEXT NOT NULL DEFAULT '',
    alias_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS device_id TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS android_contact_id TEXT NOT NULL DEFAULT '';
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS alias_name TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS normalized_number TEXT;
ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

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
-- 3. SALES_CALLS TABLE
-- ====================================================================
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
    person_id TEXT REFERENCES public.people(id) ON DELETE SET NULL,
    CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id)
);

ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS salesperson_phone TEXT;
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS salesperson_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS buyer_phone TEXT;
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS buyer_name TEXT;
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS call_type TEXT NOT NULL DEFAULT 'INCOMING';
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS call_id BIGINT;
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.sales_calls ADD COLUMN IF NOT EXISTS person_id TEXT;

-- Drop legacy conflicting constraints if present
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS uq_sales_call_natural;
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS sales_calls_salesperson_phone_buyer_phone_created_at_key;

DO $$
BEGIN
    ALTER TABLE public.sales_calls ADD CONSTRAINT uq_salesperson_call UNIQUE (salesperson_phone, call_id);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_sales_calls_salesperson ON public.sales_calls(salesperson_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_buyer ON public.sales_calls(buyer_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);
CREATE INDEX IF NOT EXISTS idx_sales_calls_created_at ON public.sales_calls(created_at);

-- ====================================================================
-- 4. CRM LEADS TABLE (Supabase App Direct Leads)
-- ====================================================================
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

ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS priority TEXT NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS feedback TEXT;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS feedback_rating INTEGER;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS owner_id TEXT;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'MANUAL';
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS next_follow_up_at TIMESTAMPTZ;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.leads ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    ALTER TABLE public.leads ADD CONSTRAINT uq_leads_person UNIQUE (person_id);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_leads_person_id ON public.leads (person_id);
CREATE INDEX IF NOT EXISTS idx_leads_status ON public.leads (status);
CREATE INDEX IF NOT EXISTS idx_leads_priority ON public.leads (priority);
CREATE INDEX IF NOT EXISTS idx_leads_next_follow_up ON public.leads (next_follow_up_at);
CREATE INDEX IF NOT EXISTS idx_leads_updated_at ON public.leads (updated_at);

-- ====================================================================
-- 5. EXTERNAL CLIENT CRM INTEGRATION TABLES (calls_log & lead_communications)
-- ====================================================================

-- 5.1 CALLS_LOG TABLE (Aggregated state by phone number)
CREATE TABLE IF NOT EXISTS public.calls_log (
    phone_number TEXT PRIMARY KEY,
    lead_name TEXT,
    journey_stage TEXT,
    bot_status TEXT,
    pause_reason TEXT,
    bhk TEXT,
    locality TEXT,
    budget_min DOUBLE PRECISION,
    budget_max DOUBLE PRECISION,
    timeline TEXT,
    purpose TEXT,
    amenities TEXT,
    requirements_complete BOOLEAN,
    pdf_status TEXT,
    pdf_sent_at TIMESTAMPTZ,
    meeting_scheduled_at TIMESTAMPTZ,
    campaign_name TEXT,
    broker_name TEXT,
    last_message_at TIMESTAMPTZ,
    last_message_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS lead_name TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS journey_stage TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS bot_status TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS pause_reason TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS bhk TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS locality TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS budget_min DOUBLE PRECISION;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS budget_max DOUBLE PRECISION;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS timeline TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS purpose TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS amenities TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS requirements_complete BOOLEAN;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS pdf_status TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS pdf_sent_at TIMESTAMPTZ;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS meeting_scheduled_at TIMESTAMPTZ;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS campaign_name TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS broker_name TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS last_message_text TEXT;
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.calls_log ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_calls_log_journey_stage ON public.calls_log (journey_stage);
CREATE INDEX IF NOT EXISTS idx_calls_log_bot_status ON public.calls_log (bot_status);

-- 5.2 LEAD_COMMUNICATIONS TABLE (Multi-call log history)
CREATE TABLE IF NOT EXISTS public.lead_communications (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    phone_number TEXT NOT NULL,
    lead_name TEXT,
    communication_type TEXT NOT NULL DEFAULT 'CALL',
    call_type TEXT NOT NULL DEFAULT 'INCOMING',
    duration INTEGER NOT NULL DEFAULT 0,
    agent_phone TEXT,
    agent_name TEXT,
    recording_url TEXT,
    notes TEXT,
    call_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS phone_number TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS lead_name TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS communication_type TEXT NOT NULL DEFAULT 'CALL';
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS call_type TEXT NOT NULL DEFAULT 'INCOMING';
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS agent_phone TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS agent_name TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS recording_url TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS call_id BIGINT;
ALTER TABLE public.lead_communications ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_lead_comm_phone ON public.lead_communications (phone_number);
CREATE INDEX IF NOT EXISTS idx_lead_comm_call_id ON public.lead_communications (call_id);
CREATE INDEX IF NOT EXISTS idx_lead_comm_created_at ON public.lead_communications (created_at);

-- ====================================================================
-- 6. LOOKUP TABLES & LEADS_MASTER
-- ====================================================================

-- 6.1 LEAD_STATUSES
CREATE TABLE IF NOT EXISTS public.lead_statuses (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.lead_statuses ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.lead_statuses ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

-- 6.2 LEAD_STAGES
CREATE TABLE IF NOT EXISTS public.lead_stages (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    kind TEXT NOT NULL DEFAULT 'pipeline' CHECK (kind IN ('pipeline', 'holding', 'terminal')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'pipeline';

-- 6.3 NEXT_ACTIONS
CREATE TABLE IF NOT EXISTS public.next_actions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6.4 LEAD_SOURCES
CREATE TABLE IF NOT EXISTS public.lead_sources (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6.5 TEAM_MEMBERS
CREATE TABLE IF NOT EXISTS public.team_members (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed initial status / stage if empty
INSERT INTO public.lead_statuses (id, name, sort_order, is_active) VALUES (1, 'New Lead', 1, true) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.lead_stages (id, name, sort_order, is_active, kind) VALUES (1, 'Initial Contact', 1, true, 'pipeline') ON CONFLICT (id) DO NOTHING;

-- 6.6 LEADS_MASTER TABLE
CREATE TABLE IF NOT EXISTS public.leads_master (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    fk_hash_key CHARACTER VARYING NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_wa_contacted_at TIMESTAMPTZ NULL,
    last_call_contacted_at TIMESTAMPTZ NULL,
    lead_name TEXT NULL,
    lead_mobile NUMERIC NULL,
    fk_lead_status BIGINT NULL DEFAULT 1 REFERENCES public.lead_statuses(id) ON DELETE SET NULL,
    fk_lead_stage BIGINT NULL DEFAULT 1 REFERENCES public.lead_stages(id) ON DELETE SET NULL,
    fk_presales_owner BIGINT NULL REFERENCES public.team_members(id) ON DELETE SET NULL,
    fk_sales_owner BIGINT NULL REFERENCES public.team_members(id) ON DELETE SET NULL,
    fk_next_action BIGINT NULL REFERENCES public.next_actions(id) ON DELETE SET NULL,
    next_action_at TIMESTAMPTZ NULL,
    requirement_captured BOOLEAN NULL,
    requirement_captured_at TIMESTAMPTZ NULL,
    options_shared BOOLEAN NULL,
    options_shared_at TIMESTAMPTZ NULL,
    visit_scheduled_at TIMESTAMPTZ NULL,
    visit_status BOOLEAN NULL,
    lead_source TEXT NULL,
    fk_lead_communication_id BIGINT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ad_id CHARACTER VARYING(64) NULL,
    fk_lead_source BIGINT NULL REFERENCES public.lead_sources(id) ON DELETE SET NULL
);

ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_hash_key CHARACTER VARYING NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS last_wa_contacted_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS last_call_contacted_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS lead_name TEXT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS lead_mobile NUMERIC NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_lead_status BIGINT NULL DEFAULT 1;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_lead_stage BIGINT NULL DEFAULT 1;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_presales_owner BIGINT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_sales_owner BIGINT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_next_action BIGINT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS next_action_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS requirement_captured BOOLEAN NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS requirement_captured_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS options_shared BOOLEAN NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS options_shared_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS visit_scheduled_at TIMESTAMPTZ NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS visit_status BOOLEAN NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS lead_source TEXT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_lead_communication_id BIGINT NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS ad_id CHARACTER VARYING(64) NULL;
ALTER TABLE public.leads_master ADD COLUMN IF NOT EXISTS fk_lead_source BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_leads_master_fk_lead_source ON public.leads_master (fk_lead_source);
CREATE INDEX IF NOT EXISTS idx_leads_master_lead_mobile ON public.leads_master (lead_mobile);
CREATE INDEX IF NOT EXISTS idx_leads_master_created_at ON public.leads_master (created_at);
CREATE INDEX IF NOT EXISTS idx_leads_master_updated_at ON public.leads_master (updated_at);
CREATE INDEX IF NOT EXISTS idx_leads_master_ad_id ON public.leads_master (ad_id);

-- Trigger function to resolve/insert lead source FK from text
CREATE OR REPLACE FUNCTION public.set_lead_source_fk()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_source_id BIGINT;
BEGIN
    IF NEW.lead_source IS NOT NULL AND TRIM(NEW.lead_source) <> '' THEN
        SELECT id INTO v_source_id 
        FROM public.lead_sources 
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(NEW.lead_source))
        LIMIT 1;

        IF v_source_id IS NULL THEN
            INSERT INTO public.lead_sources (name)
            VALUES (TRIM(NEW.lead_source))
            RETURNING id INTO v_source_id;
        END IF;

        NEW.fk_lead_source := v_source_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_set_lead_source_fk_insert ON public.leads_master;
CREATE TRIGGER trg_set_lead_source_fk_insert BEFORE INSERT ON public.leads_master 
FOR EACH ROW WHEN (
    new.fk_lead_source IS NULL
    AND new.lead_source IS NOT NULL
    AND new.lead_source <> ''::TEXT
)
EXECUTE FUNCTION public.set_lead_source_fk ();

DROP TRIGGER IF EXISTS trg_set_lead_source_fk_update ON public.leads_master;
CREATE TRIGGER trg_set_lead_source_fk_update BEFORE UPDATE ON public.leads_master 
FOR EACH ROW WHEN (
    new.fk_lead_source IS NULL
    AND new.lead_source IS NOT NULL
    AND new.lead_source <> ''::TEXT
    AND new.lead_source IS DISTINCT FROM old.lead_source
)
EXECUTE FUNCTION public.set_lead_source_fk ();

-- ====================================================================
-- 7. CONVERSATIONS & CONVERSATION_MESSAGES (Handover & WhatsApp)
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.conversations (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    whatsapp_number TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'AI_HANDLING' CHECK (status IN ('AI_HANDLING', 'HUMAN_HANDLING', 'RESOLVED')),
    assigned_user_id TEXT,
    assigned_user_name TEXT,
    last_message TEXT,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    handover_reason TEXT CHECK (handover_reason IN ('USER_REQUESTED', 'LOW_CONFIDENCE', 'SENTIMENT_ALERT', 'VIP_CUSTOMER', 'COMPLEX_QUERY', 'MANUAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversations_person UNIQUE (person_id)
);

ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS person_id TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS whatsapp_number TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'AI_HANDLING';
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS assigned_user_id TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS assigned_user_name TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS last_message TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS handover_reason TEXT;
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.conversations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_conversations_person_id ON public.conversations (person_id);
CREATE INDEX IF NOT EXISTS idx_conversations_whatsapp_number ON public.conversations (whatsapp_number);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON public.conversations (status);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON public.conversations (updated_at);

CREATE TABLE IF NOT EXISTS public.conversation_messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_type TEXT NOT NULL CHECK (sender_type IN ('CUSTOMER', 'AI', 'HUMAN', 'SYSTEM')),
    sender_name TEXT NOT NULL,
    message_text TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivery_status TEXT NOT NULL DEFAULT 'SENT' CHECK (delivery_status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS conversation_id TEXT;
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS sender_type TEXT NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS sender_name TEXT NOT NULL DEFAULT '';
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS message_text TEXT NOT NULL DEFAULT '';
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS delivery_status TEXT NOT NULL DEFAULT 'SENT';
ALTER TABLE public.conversation_messages ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation_id ON public.conversation_messages (conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_timestamp ON public.conversation_messages (timestamp);

-- ====================================================================
-- 8. TRIGGERS & PROCEDURES (Canonical Deduplication & Cleanups)
-- ====================================================================

-- 8.1 Auto-normalization Triggers
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

-- 8.2 Canonical Enforcer on contact_aliases (Prevents duplicate person creation)
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
        SELECT person_id INTO v_canonical_person_id
        FROM public.phone_numbers
        WHERE normalized_number = v_clean_norm
        LIMIT 1;

        IF v_canonical_person_id IS NOT NULL AND v_canonical_person_id <> NEW.person_id THEN
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

-- 8.3 Canonical Enforcer on sales_calls
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

-- 8.4 Orphaned People Cleaner Function and Trigger
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
-- 9. ATOMIC RPC: resolve_and_attach_contact (Device & Identity Upsert)
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
    v_raw_phone TEXT;
    v_clean_norm TEXT;
    v_clean_alias TEXT;
    v_person_id TEXT;
    v_phone_id TEXT;
    v_alias_id TEXT;
    v_now TIMESTAMPTZ := clock_timestamp();
    v_device_key TEXT;
BEGIN
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

    -- 1. Ensure Device is registered (upsert)
    IF v_device_key IS NOT NULL AND v_device_key <> '' THEN
        INSERT INTO public.devices (id, device_name, device_phone, device_identifier, created_at, updated_at, last_sync_at)
        VALUES (
            v_device_key,
            COALESCE(NULLIF(TRIM(p_device_name), ''), 'Android Device'),
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
        PERFORM public.cleanup_orphaned_people();
    END IF;

    -- 4. Idempotently attach/update Contact Alias for this Device
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

-- ====================================================================
-- 10. RPC: handover_conversation (Human Escalation)
-- ====================================================================
CREATE OR REPLACE FUNCTION public.handover_conversation(
    p_conversation_id TEXT,
    p_reason TEXT,
    p_user_id TEXT,
    p_user_name TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_updated_conv RECORD;
    v_msg_id TEXT;
    v_now TIMESTAMPTZ := clock_timestamp();
BEGIN
    UPDATE public.conversations
    SET status = 'HUMAN_HANDLING',
        handover_reason = p_reason,
        assigned_user_id = p_user_id,
        assigned_user_name = p_user_name,
        updated_at = v_now
    WHERE id = p_conversation_id
    RETURNING * INTO v_updated_conv;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Conversation not found');
    END IF;

    v_msg_id := 'MSG_' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 8));
    INSERT INTO public.conversation_messages (
        id,
        conversation_id,
        sender_type,
        sender_name,
        message_text,
        timestamp,
        delivery_status,
        created_at
    ) VALUES (
        v_msg_id,
        p_conversation_id,
        'SYSTEM',
        'System Escalation',
        '🚨 Handover triggered: Reason [' || p_reason || ']. Assigned to ' || p_user_name || '.',
        v_now,
        'DELIVERED',
        v_now
    );

    RETURN jsonb_build_object(
        'success', true,
        'conversation_id', p_conversation_id,
        'status', v_updated_conv.status,
        'assigned_user_id', v_updated_conv.assigned_user_id,
        'assigned_user_name', v_updated_conv.assigned_user_name
    );
END;
$$;

-- ====================================================================
-- 11. ROW LEVEL SECURITY (RLS) & POLICIES
-- ====================================================================
ALTER TABLE public.sales_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.people ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_aliases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calls_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_communications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_stages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.next_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.leads_master ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_messages ENABLE ROW LEVEL SECURITY;

-- Permissive policies for mobile app and client syncing
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

DROP POLICY IF EXISTS "Allow anon all on leads" ON public.leads;
CREATE POLICY "Allow anon all on leads" ON public.leads FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on calls_log" ON public.calls_log;
CREATE POLICY "Allow anon all on calls_log" ON public.calls_log FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on lead_communications" ON public.lead_communications;
CREATE POLICY "Allow anon all on lead_communications" ON public.lead_communications FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on lead_statuses" ON public.lead_statuses;
CREATE POLICY "Allow anon all on lead_statuses" ON public.lead_statuses FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on lead_stages" ON public.lead_stages;
CREATE POLICY "Allow anon all on lead_stages" ON public.lead_stages FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on next_actions" ON public.next_actions;
CREATE POLICY "Allow anon all on next_actions" ON public.next_actions FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on lead_sources" ON public.lead_sources;
CREATE POLICY "Allow anon all on lead_sources" ON public.lead_sources FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on team_members" ON public.team_members;
CREATE POLICY "Allow anon all on team_members" ON public.team_members FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on leads_master" ON public.leads_master;
CREATE POLICY "Allow anon all on leads_master" ON public.leads_master FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on conversations" ON public.conversations;
CREATE POLICY "Allow anon all on conversations" ON public.conversations FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on conversation_messages" ON public.conversation_messages;
CREATE POLICY "Allow anon all on conversation_messages" ON public.conversation_messages FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- ====================================================================
-- 12. TABLE & FUNCTION PERMISSIONS
-- ====================================================================
GRANT ALL ON TABLE public.sales_calls TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.people TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.phone_numbers TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.contact_aliases TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.leads TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.calls_log TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_communications TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_statuses TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_stages TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.next_actions TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_sources TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.team_members TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.leads_master TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.conversations TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.conversation_messages TO anon, authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.normalize_phone_number TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cleanup_orphaned_people TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.handover_conversation TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_lead_source_fk TO anon, authenticated, service_role;

-- ====================================================================
-- 13. RELOAD SCHEMA CACHE
-- ====================================================================
NOTIFY pgrst, 'reload schema';
