-- ====================================================================
-- MASTER SUPABASE SETUP SCRIPT (Run this in Supabase SQL Editor)
-- ====================================================================
-- This script safely creates or updates all required tables, columns,
-- constraints, RLS policies, and RPC functions for AllSet / Callog.
-- ALL timestamps are standardized to PostgreSQL TIMESTAMPTZ (UTC).
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

-- Drop conflicting legacy constraints if present
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS uq_sales_call_natural;
ALTER TABLE public.sales_calls DROP CONSTRAINT IF EXISTS sales_calls_salesperson_phone_buyer_phone_created_at_key;

-- Safely alter column type if table previously existed with TEXT / BIGINT
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'sales_calls' 
          AND column_name = 'created_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.sales_calls ALTER COLUMN created_at TYPE TIMESTAMPTZ USING to_timestamp(created_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'sales_calls' 
          AND column_name = 'created_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.sales_calls ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_sales_calls_salesperson ON public.sales_calls(salesperson_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_buyer ON public.sales_calls(buyer_phone);
CREATE INDEX IF NOT EXISTS idx_sales_calls_person_id ON public.sales_calls(person_id);
CREATE INDEX IF NOT EXISTS idx_sales_calls_created_at ON public.sales_calls(created_at);

-- 2. DEVICES TABLE
CREATE TABLE IF NOT EXISTS public.devices (
    id TEXT PRIMARY KEY,
    device_name TEXT NOT NULL DEFAULT 'Android Device',
    device_phone TEXT NOT NULL DEFAULT '',
    device_identifier TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sync_at TIMESTAMPTZ
);

-- Safely alter column types to TIMESTAMPTZ if table previously existed with BIGINT / TEXT
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'created_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN created_at TYPE TIMESTAMPTZ USING to_timestamp(created_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'created_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'updated_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING to_timestamp(updated_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'updated_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'last_sync_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN last_sync_at TYPE TIMESTAMPTZ USING to_timestamp(last_sync_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'devices' 
          AND column_name = 'last_sync_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.devices ALTER COLUMN last_sync_at TYPE TIMESTAMPTZ USING last_sync_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.devices ADD CONSTRAINT uq_device_identifier UNIQUE (device_identifier);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_devices_identifier ON public.devices (device_identifier);

-- 3. PEOPLE TABLE (Canonical CRM Person)
CREATE TABLE IF NOT EXISTS public.people (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    company_name TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'people' 
          AND column_name = 'created_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.people ALTER COLUMN created_at TYPE TIMESTAMPTZ USING to_timestamp(created_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'people' 
          AND column_name = 'created_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.people ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'people' 
          AND column_name = 'updated_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.people ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING to_timestamp(updated_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'people' 
          AND column_name = 'updated_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.people ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_people_display_name ON public.people (display_name);

-- 4. PHONE_NUMBERS TABLE
CREATE TABLE IF NOT EXISTS public.phone_numbers (
    id TEXT PRIMARY KEY,
    person_id TEXT NOT NULL REFERENCES public.people(id) ON DELETE CASCADE,
    phone_number TEXT NOT NULL,
    normalized_number TEXT NOT NULL,
    phone_type TEXT NOT NULL DEFAULT 'PRIMARY',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD COLUMN IF NOT EXISTS normalized_number TEXT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'phone_numbers' 
          AND column_name = 'created_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.phone_numbers ALTER COLUMN created_at TYPE TIMESTAMPTZ USING to_timestamp(created_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'phone_numbers' 
          AND column_name = 'created_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.phone_numbers ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.phone_numbers ADD CONSTRAINT uq_phone_numbers_normalized UNIQUE (normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_phone_numbers_person_id ON public.phone_numbers (person_id);
CREATE INDEX IF NOT EXISTS idx_phone_numbers_normalized ON public.phone_numbers (normalized_number);

-- 5. CONTACT_ALIASES TABLE
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

DO $$
BEGIN
    ALTER TABLE public.contact_aliases ADD COLUMN IF NOT EXISTS normalized_number TEXT;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'contact_aliases' 
          AND column_name = 'created_at' AND data_type = 'bigint'
    ) THEN
        ALTER TABLE public.contact_aliases ALTER COLUMN created_at TYPE TIMESTAMPTZ USING to_timestamp(created_at / 1000.0);
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'contact_aliases' 
          AND column_name = 'created_at' AND data_type = 'text'
    ) THEN
        ALTER TABLE public.contact_aliases ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz;
    END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.contact_aliases ADD CONSTRAINT uq_device_alias_phone UNIQUE (device_id, normalized_number);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_contact_aliases_person_id ON public.contact_aliases (person_id);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_device_id ON public.contact_aliases (device_id);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_normalized ON public.contact_aliases (normalized_number);
CREATE INDEX IF NOT EXISTS idx_contact_aliases_alias_name ON public.contact_aliases (alias_name);

-- 6. LOOKUP TABLES FOR LEADS_MASTER
CREATE TABLE IF NOT EXISTS public.lead_statuses (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT lead_statuses_pkey PRIMARY KEY (id),
  CONSTRAINT lead_statuses_name_key UNIQUE (name)
);

DO $$
BEGIN
    ALTER TABLE public.lead_statuses ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
    ALTER TABLE public.lead_statuses ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
    ALTER TABLE public.lead_statuses ADD CONSTRAINT lead_statuses_name_key UNIQUE (name);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS public.lead_stages (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  kind TEXT NOT NULL DEFAULT 'pipeline'::TEXT,
  CONSTRAINT lead_stages_pkey PRIMARY KEY (id),
  CONSTRAINT lead_stages_name_key UNIQUE (name),
  CONSTRAINT lead_stages_kind_check CHECK (
    (
      kind = ANY (
        ARRAY[
          'pipeline'::TEXT,
          'holding'::TEXT,
          'terminal'::TEXT
        ]
      )
    )
  )
);

DO $$
BEGIN
    ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
    ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;
    ALTER TABLE public.lead_stages ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'pipeline';
    ALTER TABLE public.lead_stages ADD CONSTRAINT lead_stages_name_key UNIQUE (name);
    ALTER TABLE public.lead_stages ADD CONSTRAINT lead_stages_kind_check CHECK (kind = ANY (ARRAY['pipeline'::TEXT, 'holding'::TEXT, 'terminal'::TEXT]));
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS public.next_actions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.lead_sources (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.team_members (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.lead_statuses (id, name, sort_order, is_active) VALUES (1, 'New Lead', 1, true) ON CONFLICT (id) DO NOTHING;
INSERT INTO public.lead_stages (id, name, sort_order, is_active, kind) VALUES (1, 'Initial Contact', 1, true, 'pipeline') ON CONFLICT (id) DO NOTHING;

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

-- 6b. LEADS_MASTER TABLE
CREATE TABLE IF NOT EXISTS public.leads_master (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
  fk_hash_key CHARACTER VARYING NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  last_wa_contacted_at TIMESTAMP WITH TIME ZONE NULL,
  last_call_contacted_at TIMESTAMP WITH TIME ZONE NULL,
  lead_name TEXT NULL,
  lead_mobile NUMERIC NULL,
  fk_lead_status BIGINT NULL DEFAULT '1'::BIGINT,
  fk_lead_stage BIGINT NULL DEFAULT '1'::BIGINT,
  fk_presales_owner BIGINT NULL,
  fk_sales_owner BIGINT NULL,
  fk_next_action BIGINT NULL,
  next_action_at TIMESTAMP WITH TIME ZONE NULL,
  requirement_captured BOOLEAN NULL,
  requirement_captured_at TIMESTAMP WITH TIME ZONE NULL,
  options_shared BOOLEAN NULL,
  options_shared_at TIMESTAMP WITH TIME ZONE NULL,
  visit_scheduled_at TIMESTAMP WITH TIME ZONE NULL,
  visit_status BOOLEAN NULL,
  lead_source TEXT NULL,
  fk_lead_communication_id BIGINT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  ad_id CHARACTER VARYING(64) NULL,
  fk_lead_source BIGINT NULL,
  CONSTRAINT leads_master_pkey PRIMARY KEY (id),
  CONSTRAINT leads_master_fk_hash_key_key UNIQUE (fk_hash_key),
  CONSTRAINT fk_leads_master_lead_status FOREIGN KEY (fk_lead_status) REFERENCES public.lead_statuses (id) ON DELETE SET NULL,
  CONSTRAINT fk_leads_master_next_action FOREIGN KEY (fk_next_action) REFERENCES public.next_actions (id) ON DELETE SET NULL,
  CONSTRAINT fk_leads_master_lead_source FOREIGN KEY (fk_lead_source) REFERENCES public.lead_sources (id) ON DELETE SET NULL,
  CONSTRAINT fk_leads_master_sales_owner FOREIGN KEY (fk_sales_owner) REFERENCES public.team_members (id) ON DELETE SET NULL,
  CONSTRAINT fk_leads_master_presales_owner FOREIGN KEY (fk_presales_owner) REFERENCES public.team_members (id) ON DELETE SET NULL,
  CONSTRAINT fk_leads_master_lead_stage FOREIGN KEY (fk_lead_stage) REFERENCES public.lead_stages (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS leads_master_ad_id_f6be183b ON public.leads_master USING btree (ad_id);
CREATE INDEX IF NOT EXISTS leads_master_ad_id_f6be183b_like ON public.leads_master USING btree (ad_id varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_leads_master_fk_lead_source ON public.leads_master USING btree (fk_lead_source);
CREATE INDEX IF NOT EXISTS idx_leads_master_lead_mobile ON public.leads_master USING btree (lead_mobile);
CREATE INDEX IF NOT EXISTS idx_leads_master_created_at ON public.leads_master USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_leads_master_updated_at ON public.leads_master USING btree (updated_at);

DROP TRIGGER IF EXISTS trg_set_lead_source_fk_insert ON public.leads_master;
CREATE TRIGGER trg_set_lead_source_fk_insert BEFORE INSERT ON public.leads_master FOR EACH ROW WHEN (
  new.fk_lead_source IS NULL
  AND new.lead_source IS NOT NULL
  AND new.lead_source <> ''::TEXT
)
EXECUTE FUNCTION public.set_lead_source_fk ();

DROP TRIGGER IF EXISTS trg_set_lead_source_fk_update ON public.leads_master;
CREATE TRIGGER trg_set_lead_source_fk_update BEFORE UPDATE ON public.leads_master FOR EACH ROW WHEN (
  new.fk_lead_source IS NULL
  AND new.lead_source IS NOT NULL
  AND new.lead_source <> ''::TEXT
  AND new.lead_source IS DISTINCT FROM old.lead_source
)
EXECUTE FUNCTION public.set_lead_source_fk ();

-- 7. CONVERSATIONS & MESSAGES TABLES (WhatsApp / Multi-channel & Handover)
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

CREATE INDEX IF NOT EXISTS idx_conversations_person_id ON public.conversations (person_id);
CREATE INDEX IF NOT EXISTS idx_conversations_whatsapp_number ON public.conversations (whatsapp_number);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON public.conversations (status);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON public.conversations (updated_at);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation_id ON public.conversation_messages (conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_timestamp ON public.conversation_messages (timestamp);

-- 8. CANONICAL NORMALIZATION FUNCTION & TRIGGERS
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

-- ====================================================================
-- 9. ATOMIC RPC: resolve_and_attach_contact (UTC Timestamps)
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
        DELETE FROM public.people
        WHERE id <> v_person_id
          AND NOT EXISTS (SELECT 1 FROM public.phone_numbers pn WHERE pn.person_id = people.id)
          AND NOT EXISTS (SELECT 1 FROM public.contact_aliases ca WHERE ca.person_id = people.id)
          AND NOT EXISTS (SELECT 1 FROM public.sales_calls sc WHERE sc.person_id = people.id);
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
-- 10. RPC: handover_conversation (Human Handover)
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
-- 11. ENABLE PERMISSIONS & ROW LEVEL SECURITY (RLS)
-- ====================================================================
ALTER TABLE public.sales_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.people ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_aliases ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_statuses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_stages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.next_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lead_sources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.leads_master ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_messages ENABLE ROW LEVEL SECURITY;

-- Drop and recreate permissive policies for application access
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

-- Grant schema permissions
GRANT ALL ON TABLE public.sales_calls TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.devices TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.people TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.phone_numbers TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.contact_aliases TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_statuses TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_stages TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.next_actions TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.lead_sources TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.team_members TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.leads_master TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.conversations TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.conversation_messages TO anon, authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.resolve_and_attach_contact TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.normalize_phone_number TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_lead_source_fk TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.handover_conversation TO anon, authenticated, service_role;

-- 12. RELOAD POSTGREST SCHEMA CACHE
NOTIFY pgrst, 'reload schema';
