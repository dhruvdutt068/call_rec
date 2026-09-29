


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA IF NOT EXISTS "public";


ALTER SCHEMA "public" OWNER TO "pg_database_owner";


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE OR REPLACE FUNCTION "public"."cleanup_orphaned_people"() RETURNS integer
    LANGUAGE "plpgsql"
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


ALTER FUNCTION "public"."cleanup_orphaned_people"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."custom_access_token_hook"("event" "jsonb") RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
declare
    profile      public.user_profiles;
    claims       jsonb;
    app_metadata jsonb;
begin
    select * into profile
    from public.user_profiles
    where user_id = (event->>'user_id')::uuid;

    claims := coalesce(event->'claims', '{}'::jsonb);
    app_metadata := coalesce(claims->'app_metadata', '{}'::jsonb);

    if profile.user_id is null then
        -- An auth.users row with no profile: fail closed. The gateway will see
        -- an empty role set plus is_active false and deny both apps, rather
        -- than treating a half-provisioned account as a valid login.
        app_metadata := app_metadata || jsonb_build_object(
            'roles', '[]'::jsonb,
            'is_active', false
        );
    else
        app_metadata := app_metadata || jsonb_build_object(
            'roles', to_jsonb(profile.roles),
            'is_active', profile.is_active,
            'full_name', profile.full_name
        );
    end if;

    claims := jsonb_set(claims, '{app_metadata}', app_metadata);
    return jsonb_set(event, '{claims}', claims);
end;
$$;


ALTER FUNCTION "public"."custom_access_token_hook"("event" "jsonb") OWNER TO "postgres";


COMMENT ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") IS 'Stamps roles/is_active into every issued JWT so token introspection needs no DB read.';



CREATE OR REPLACE FUNCTION "public"."handover_conversation"("p_conversation_id" "text", "p_reason" "text", "p_user_id" "text", "p_user_name" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
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


ALTER FUNCTION "public"."handover_conversation"("p_conversation_id" "text", "p_reason" "text", "p_user_id" "text", "p_user_name" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."identity_revoke_user_sessions"("target_user_id" "uuid") RETURNS integer
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
declare
    removed_refresh integer := 0;
    removed_sessions integer := 0;
begin
    -- auth.refresh_tokens.user_id is varchar in GoTrue's schema, not uuid.
    delete from auth.refresh_tokens where user_id = target_user_id::text;
    get diagnostics removed_refresh = row_count;

    -- auth.sessions arrived in later GoTrue versions; tolerate its absence so
    -- this function works across versions rather than erroring on one.
    begin
        delete from auth.sessions where user_id = target_user_id;
        get diagnostics removed_sessions = row_count;
    exception
        when undefined_table then removed_sessions := 0;
    end;

    return removed_refresh + removed_sessions;
end;
$$;


ALTER FUNCTION "public"."identity_revoke_user_sessions"("target_user_id" "uuid") OWNER TO "postgres";


COMMENT ON FUNCTION "public"."identity_revoke_user_sessions"("target_user_id" "uuid") IS 'Revokes every session for one user. Called by the identity service on role change and deactivation, because GoTrue exposes no admin logout endpoint.';



CREATE OR REPLACE FUNCTION "public"."identity_set_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
    new.updated_at := now();
    return new;
end;
$$;


ALTER FUNCTION "public"."identity_set_updated_at"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."log_lead_state"() RETURNS "trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public'
    AS $$
BEGIN
    INSERT INTO lead_state_history
        (lead_id, fk_lead_status, fk_lead_stage, prev_lead_status, prev_lead_stage, change_kind)
    VALUES (
        NEW.id, NEW.fk_lead_status, NEW.fk_lead_stage,
        CASE WHEN TG_OP = 'UPDATE' THEN OLD.fk_lead_status END,
        CASE WHEN TG_OP = 'UPDATE' THEN OLD.fk_lead_stage END,
        lower(TG_OP)
    );
    RETURN NULL;
END;
$$;


ALTER FUNCTION "public"."log_lead_state"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."normalize_phone_number"("raw" "text") RETURNS "text"
    LANGUAGE "plpgsql" IMMUTABLE
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


ALTER FUNCTION "public"."normalize_phone_number"("raw" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."normalize_phone_with_country_code"("p_phone" "text") RETURNS "text"
    LANGUAGE "sql" IMMUTABLE
    AS $_$
    SELECT CASE
        WHEN p_phone IS NULL              THEN NULL
        WHEN btrim(p_phone) = ''          THEN NULL
        -- Exactly ten digits means the country code is missing. Anything else -
        -- already 91-prefixed, or malformed - passes through untouched.
        WHEN btrim(p_phone) ~ '^\d{10}$'  THEN '91' || btrim(p_phone)
        ELSE btrim(p_phone)
    END;
$_$;


ALTER FUNCTION "public"."normalize_phone_with_country_code"("p_phone" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."resolve_and_attach_contact"("p_device_id" "text", "p_android_contact_id" "text", "p_alias_name" "text", "p_phone_number" "text", "p_normalized_number" "text", "p_device_name" "text" DEFAULT NULL::"text", "p_device_phone" "text" DEFAULT NULL::"text", "p_device_identifier" "text" DEFAULT NULL::"text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
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
        v_person_id := 'P' || UPPER(SUBSTR(MD5(RANDOM()::TEXT || clock_timestamp()::TEXT), 1, 8));
        
        -- Insert Person record
        INSERT INTO public.people (id, display_name, created_at, updated_at)
        VALUES (v_person_id, v_clean_alias, v_now, v_now);

        -- Insert Phone Number with ON CONFLICT resolution
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


ALTER FUNCTION "public"."resolve_and_attach_contact"("p_device_id" "text", "p_android_contact_id" "text", "p_alias_name" "text", "p_phone_number" "text", "p_normalized_number" "text", "p_device_name" "text", "p_device_phone" "text", "p_device_identifier" "text") OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."rls_auto_enable"() RETURNS "event_trigger"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


ALTER FUNCTION "public"."rls_auto_enable"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_lead_source_fk"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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
            INSERT INTO public.lead_sources (name, code, channel, sort_order, is_active)
            VALUES (TRIM(NEW.lead_source), LOWER(REGEXP_REPLACE(TRIM(NEW.lead_source), '\s+', '_', 'g')), 'direct', 1, true)
            RETURNING id INTO v_source_id;
        END IF;

        NEW.fk_lead_source := v_source_id;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."set_lead_source_fk"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."set_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."set_updated_at"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."trg_after_alias_cleanup_orphans"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    PERFORM public.cleanup_orphaned_people();
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."trg_after_alias_cleanup_orphans"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."trg_enforce_canonical_person_on_alias"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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


ALTER FUNCTION "public"."trg_enforce_canonical_person_on_alias"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."trg_enforce_canonical_person_on_call"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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


ALTER FUNCTION "public"."trg_enforce_canonical_person_on_call"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."trg_lead_communications_link_lead"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF NEW.fk_lead_id IS NULL AND NEW.phone_number IS NOT NULL AND NEW.phone_number <> '' THEN
        SELECT id INTO NEW.fk_lead_id
        FROM public.leads_master
        WHERE lead_mobile::TEXT = public.normalize_phone_number(NEW.phone_number)
           OR RIGHT(lead_mobile::TEXT, 10) = public.normalize_phone_number(NEW.phone_number)
        ORDER BY updated_at DESC
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION "public"."trg_lead_communications_link_lead"() OWNER TO "postgres";


CREATE OR REPLACE FUNCTION "public"."trg_normalize_phone_number"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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


ALTER FUNCTION "public"."trg_normalize_phone_number"() OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."lead_site_visits" (
    "visit_id" integer NOT NULL,
    "fk_lead_id" integer NOT NULL,
    "fk_team_member_id" integer NOT NULL,
    "property_ids" integer[] NOT NULL,
    "visit_time" timestamp with time zone NOT NULL,
    "fk_reminder_type_id" integer NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "status" "text" DEFAULT 'scheduled'::"text" NOT NULL,
    "outcome" "text" DEFAULT ''::"text" NOT NULL,
    "properties" "jsonb" DEFAULT '[]'::"jsonb" NOT NULL,
    "created_by_name" "text" DEFAULT ''::"text" NOT NULL,
    "created_by_role" "text" DEFAULT ''::"text" NOT NULL,
    "wa_delivery_status" "text" DEFAULT ''::"text" NOT NULL,
    "wa_message_id" "text" DEFAULT ''::"text" NOT NULL,
    "wa_error" "text" DEFAULT ''::"text" NOT NULL
);


ALTER TABLE "public"."lead_site_visits" OWNER TO "postgres";


CREATE OR REPLACE VIEW "public"."LeadSiteVisit" AS
 SELECT "visit_id",
    "fk_lead_id",
    "fk_team_member_id",
    "property_ids",
    "visit_time",
    "fk_reminder_type_id",
    "created_at",
    "updated_at",
    "status",
    "outcome",
    "properties",
    "created_by_name",
    "created_by_role",
    "wa_delivery_status",
    "wa_message_id",
    "wa_error"
   FROM "public"."lead_site_visits";


ALTER VIEW "public"."LeadSiteVisit" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."LeadSiteVisit_backup_20260918" (
    "visit_id" integer,
    "fk_lead_id" integer,
    "fk_team_member_id" integer,
    "property_ids" integer[],
    "visit_time" timestamp with time zone,
    "fk_reminder_type_id" integer,
    "created_at" timestamp with time zone,
    "updated_at" timestamp with time zone,
    "status" "text",
    "outcome" "text",
    "properties" "jsonb",
    "created_by_name" "text",
    "created_by_role" "text",
    "wa_delivery_status" "text",
    "wa_message_id" "text",
    "wa_error" "text"
);


ALTER TABLE "public"."LeadSiteVisit_backup_20260918" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."calls_log" (
    "phone_number" character varying(32) NOT NULL,
    "lead_name" character varying(255),
    "journey_stage" character varying(64),
    "bot_status" character varying(32),
    "pause_reason" character varying(64),
    "bhk" integer[],
    "locality" character varying(255),
    "budget_min" numeric(14,2),
    "budget_max" numeric(14,2),
    "timeline" character varying(64),
    "purpose" character varying(64),
    "amenities" "text"[],
    "requirements_complete" boolean DEFAULT false,
    "pdf_status" character varying(32),
    "pdf_sent_at" timestamp with time zone,
    "meeting_scheduled_at" timestamp with time zone,
    "campaign_name" character varying(255),
    "broker_name" character varying(255),
    "last_message_at" timestamp with time zone,
    "last_message_text" "text",
    "created_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    "updated_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE "public"."calls_log" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."communication_channels" (
    "id" bigint NOT NULL,
    "name" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."communication_channels" OWNER TO "postgres";


ALTER TABLE "public"."communication_channels" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."communication_channels_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."contact_aliases" (
    "id" "text" NOT NULL,
    "person_id" "text" NOT NULL,
    "device_id" "text" NOT NULL,
    "android_contact_id" "text" DEFAULT ''::"text" NOT NULL,
    "alias_name" "text" NOT NULL,
    "phone_number" "text" NOT NULL,
    "normalized_number" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."contact_aliases" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."conversation_messages" (
    "id" "text" NOT NULL,
    "conversation_id" "text" NOT NULL,
    "sender_type" "text" NOT NULL,
    "sender_name" "text" NOT NULL,
    "message_text" "text" NOT NULL,
    "timestamp" timestamp with time zone DEFAULT "now"() NOT NULL,
    "delivery_status" "text" DEFAULT 'SENT'::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "conversation_messages_delivery_status_check" CHECK (("delivery_status" = ANY (ARRAY['PENDING'::"text", 'SENT'::"text", 'DELIVERED'::"text", 'READ'::"text", 'FAILED'::"text"]))),
    CONSTRAINT "conversation_messages_sender_type_check" CHECK (("sender_type" = ANY (ARRAY['CUSTOMER'::"text", 'AI'::"text", 'HUMAN'::"text", 'SYSTEM'::"text"])))
);


ALTER TABLE "public"."conversation_messages" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."conversations" (
    "id" "text" NOT NULL,
    "person_id" "text" NOT NULL,
    "whatsapp_number" "text" NOT NULL,
    "status" "text" DEFAULT 'AI_HANDLING'::"text" NOT NULL,
    "assigned_user_id" "text",
    "assigned_user_name" "text",
    "last_message" "text",
    "last_message_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "handover_reason" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "conversations_handover_reason_check" CHECK (("handover_reason" = ANY (ARRAY['USER_REQUESTED'::"text", 'LOW_CONFIDENCE'::"text", 'SENTIMENT_ALERT'::"text", 'VIP_CUSTOMER'::"text", 'COMPLEX_QUERY'::"text", 'MANUAL'::"text"]))),
    CONSTRAINT "conversations_status_check" CHECK (("status" = ANY (ARRAY['AI_HANDLING'::"text", 'HUMAN_HANDLING'::"text", 'RESOLVED'::"text"])))
);


ALTER TABLE "public"."conversations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."devices" (
    "id" "text" NOT NULL,
    "device_name" "text" DEFAULT 'Android Device'::"text" NOT NULL,
    "device_phone" "text" DEFAULT ''::"text" NOT NULL,
    "device_identifier" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "last_sync_at" timestamp with time zone
);


ALTER TABLE "public"."devices" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."django_migrations" (
    "id" bigint NOT NULL,
    "app" character varying(255) NOT NULL,
    "name" character varying(255) NOT NULL,
    "applied" timestamp with time zone NOT NULL
);


ALTER TABLE "public"."django_migrations" OWNER TO "postgres";


ALTER TABLE "public"."django_migrations" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."django_migrations_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_actions" (
    "id" bigint NOT NULL,
    "action_id" bigint NOT NULL,
    "action_name" "text",
    "action_type" "text"
);


ALTER TABLE "public"."lead_actions" OWNER TO "postgres";


ALTER TABLE "public"."lead_actions" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_actions_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_ad_routing" (
    "id" bigint NOT NULL,
    "ad_id" character varying(64) NOT NULL,
    "ad_name" character varying(255) NOT NULL,
    "is_active" boolean NOT NULL,
    "created_at" timestamp with time zone NOT NULL,
    "updated_at" timestamp with time zone NOT NULL,
    "fk_presales_owner" bigint
);


ALTER TABLE "public"."lead_ad_routing" OWNER TO "postgres";


ALTER TABLE "public"."lead_ad_routing" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_ad_routing_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_chatbot_status" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "status" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."lead_chatbot_status" OWNER TO "postgres";


ALTER TABLE "public"."lead_chatbot_status" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_chatbot_status_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_communications" (
    "id" bigint NOT NULL,
    "fk_lead_id" bigint,
    "fk_sender_id" bigint,
    "fk_channel_id" bigint,
    "subject" "text" DEFAULT ''::"text" NOT NULL,
    "message" "text" DEFAULT ''::"text" NOT NULL,
    "source_category" "text" DEFAULT ''::"text" NOT NULL,
    "source_name" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "phone_number" "text",
    "lead_name" "text",
    "communication_type" "text" DEFAULT 'CALL'::"text" NOT NULL,
    "call_type" "text" DEFAULT 'INCOMING'::"text" NOT NULL,
    "duration" integer DEFAULT 0 NOT NULL,
    "agent_phone" "text",
    "agent_name" "text",
    "recording_url" "text",
    "notes" "text",
    "call_id" bigint
);


ALTER TABLE "public"."lead_communications" OWNER TO "postgres";


ALTER TABLE "public"."lead_communications" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_communications_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_consultations" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "scheduled_at" timestamp with time zone NOT NULL,
    "duration_minutes" integer,
    "mode" "text" DEFAULT 'office'::"text" NOT NULL,
    "location" "text" DEFAULT ''::"text" NOT NULL,
    "status" "text" DEFAULT 'scheduled'::"text" NOT NULL,
    "outcome" "text" DEFAULT ''::"text" NOT NULL,
    "fk_consultant" bigint,
    "created_by_name" "text" DEFAULT ''::"text" NOT NULL,
    "created_by_role" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "wa_delivery_status" "text" DEFAULT ''::"text" NOT NULL,
    "wa_message_id" "text" DEFAULT ''::"text" NOT NULL,
    "wa_error" "text" DEFAULT ''::"text" NOT NULL
);


ALTER TABLE "public"."lead_consultations" OWNER TO "postgres";


ALTER TABLE "public"."lead_consultations" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_consultations_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_distribution_settings" (
    "id" smallint DEFAULT 1 NOT NULL,
    "is_enabled" boolean DEFAULT false NOT NULL,
    "enabled_at" timestamp with time zone,
    "fk_last_assigned_member" bigint,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "lead_distribution_settings_single_row" CHECK (("id" = 1))
);


ALTER TABLE "public"."lead_distribution_settings" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."lead_notes" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "note_text" "text" NOT NULL,
    "author_name" "text" NOT NULL,
    "author_role" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."lead_notes" OWNER TO "postgres";


ALTER TABLE "public"."lead_notes" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_notes_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_requirements" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "locality" "jsonb",
    "bhk" "jsonb",
    "budget_min_lakhs" numeric,
    "budget_max_lakhs" numeric,
    "possession" "text",
    "carpet_min" numeric,
    "carpet_max" numeric,
    "notes" "text",
    "captured_by_name" "text" NOT NULL,
    "captured_by_role" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "property_id" bigint[]
);


ALTER TABLE "public"."lead_requirements" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."lead_requirements_duplicate" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "locality" "jsonb",
    "bhk" "jsonb",
    "budget_min_lakhs" numeric,
    "budget_max_lakhs" numeric,
    "possession" "text",
    "carpet_min" numeric,
    "carpet_max" numeric,
    "notes" "text",
    "captured_by_name" "text" NOT NULL,
    "captured_by_role" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "property_id" bigint[]
);


ALTER TABLE "public"."lead_requirements_duplicate" OWNER TO "postgres";


COMMENT ON TABLE "public"."lead_requirements_duplicate" IS 'This is a duplicate of lead_requirements';



ALTER TABLE "public"."lead_requirements_duplicate" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_requirements_duplicate_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



ALTER TABLE "public"."lead_requirements" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_requirements_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_shared_options" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "options" "jsonb" DEFAULT '[]'::"jsonb" NOT NULL,
    "property_ids" bigint[] DEFAULT '{}'::bigint[] NOT NULL,
    "channel" "text" DEFAULT 'whatsapp'::"text" NOT NULL,
    "document_url" "text",
    "document_path" "text",
    "document_filename" "text",
    "document_expires_at" timestamp with time zone,
    "delivery_status" "text" DEFAULT 'sent'::"text" NOT NULL,
    "delivery_error" "text",
    "wa_request_id" "text",
    "wa_message_id" "text",
    "source" "text" DEFAULT ''::"text" NOT NULL,
    "shared_by_name" "text" DEFAULT ''::"text" NOT NULL,
    "shared_by_role" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."lead_shared_options" OWNER TO "postgres";


ALTER TABLE "public"."lead_shared_options" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_shared_options_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE SEQUENCE IF NOT EXISTS "public"."lead_site_visits_visit_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."lead_site_visits_visit_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."lead_site_visits_visit_id_seq" OWNED BY "public"."lead_site_visits"."visit_id";



CREATE TABLE IF NOT EXISTS "public"."lead_sources" (
    "id" bigint NOT NULL,
    "code" "text" NOT NULL,
    "name" "text" NOT NULL,
    "channel" "text" NOT NULL,
    "sort_order" integer NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "is_distributed" boolean DEFAULT false NOT NULL
);


ALTER TABLE "public"."lead_sources" OWNER TO "postgres";


ALTER TABLE "public"."lead_sources" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_sources_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_stages" (
    "id" bigint NOT NULL,
    "name" "text" NOT NULL,
    "sort_order" integer NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "kind" "text" DEFAULT 'pipeline'::"text" NOT NULL,
    CONSTRAINT "lead_stages_kind_check" CHECK (("kind" = ANY (ARRAY['pipeline'::"text", 'holding'::"text", 'terminal'::"text"])))
);


ALTER TABLE "public"."lead_stages" OWNER TO "postgres";


ALTER TABLE "public"."lead_stages" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_stages_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_state_history" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "fk_lead_status" bigint,
    "fk_lead_stage" bigint,
    "prev_lead_status" bigint,
    "prev_lead_stage" bigint,
    "change_kind" "text" NOT NULL,
    "changed_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "lead_state_history_change_kind_check" CHECK (("change_kind" = ANY (ARRAY['baseline'::"text", 'insert'::"text", 'update'::"text"])))
);


ALTER TABLE "public"."lead_state_history" OWNER TO "postgres";


ALTER TABLE "public"."lead_state_history" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_state_history_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_statuses" (
    "id" bigint NOT NULL,
    "name" "text" NOT NULL,
    "sort_order" integer NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "counts_as_active" boolean DEFAULT false NOT NULL
);


ALTER TABLE "public"."lead_statuses" OWNER TO "postgres";


ALTER TABLE "public"."lead_statuses" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_statuses_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."lead_tasks" (
    "id" bigint NOT NULL,
    "lead_id" bigint NOT NULL,
    "task_type" "text" NOT NULL,
    "title" "text" DEFAULT ''::"text" NOT NULL,
    "fk_assignee" bigint,
    "due_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "status" "text" DEFAULT 'pending'::"text" NOT NULL,
    "outcome" "text" DEFAULT ''::"text" NOT NULL,
    "source" "text" DEFAULT 'auto'::"text" NOT NULL,
    "completed_at" timestamp with time zone,
    "completed_by_name" "text" DEFAULT ''::"text" NOT NULL,
    "completed_by_role" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "outcome_code" "text" DEFAULT ''::"text" NOT NULL,
    "parent_key" "text" DEFAULT ''::"text" NOT NULL,
    "reverts" "jsonb" DEFAULT '{}'::"jsonb" NOT NULL
);


ALTER TABLE "public"."lead_tasks" OWNER TO "postgres";


ALTER TABLE "public"."lead_tasks" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."lead_tasks_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."leads" (
    "id" "text" NOT NULL,
    "person_id" "text" NOT NULL,
    "status" "text" DEFAULT 'UNKNOWN'::"text" NOT NULL,
    "priority" "text" DEFAULT 'MEDIUM'::"text" NOT NULL,
    "feedback" "text",
    "feedback_rating" integer,
    "notes" "text",
    "owner_id" "text",
    "source" "text" DEFAULT 'MANUAL'::"text" NOT NULL,
    "next_follow_up_at" timestamp with time zone,
    "is_archived" boolean DEFAULT false NOT NULL,
    "archived_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "leads_priority_check" CHECK (("priority" = ANY (ARRAY['LOW'::"text", 'MEDIUM'::"text", 'HIGH'::"text", 'URGENT'::"text"]))),
    CONSTRAINT "leads_status_check" CHECK (("status" = ANY (ARRAY['HOT'::"text", 'WARM'::"text", 'COLD'::"text", 'UNKNOWN'::"text"])))
);


ALTER TABLE "public"."leads" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."leads_master" (
    "id" bigint NOT NULL,
    "fk_hash_key" character varying,
    "created_at" timestamp with time zone NOT NULL,
    "last_wa_contacted_at" timestamp with time zone,
    "last_call_contacted_at" timestamp with time zone,
    "lead_name" "text",
    "lead_mobile" numeric,
    "fk_lead_status" bigint DEFAULT '1'::bigint,
    "fk_lead_stage" bigint DEFAULT '1'::bigint,
    "fk_presales_owner" bigint,
    "fk_sales_owner" bigint,
    "fk_next_action" bigint,
    "next_action_at" timestamp with time zone,
    "requirement_captured" boolean,
    "requirement_captured_at" timestamp with time zone,
    "options_shared" boolean,
    "options_shared_at" timestamp with time zone,
    "visit_scheduled_at" timestamp with time zone,
    "visit_status" boolean,
    "lead_source" "text",
    "fk_lead_communication_id" bigint,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "ad_id" character varying(64),
    "fk_lead_source" bigint
);


ALTER TABLE "public"."leads_master" OWNER TO "postgres";


COMMENT ON TABLE "public"."leads_master" IS 'Msater table for all leads data';



CREATE TABLE IF NOT EXISTS "public"."leads_master_duplicate" (
    "id" bigint NOT NULL,
    "fk_hash_key" character varying,
    "created_at" timestamp with time zone NOT NULL,
    "last_wa_contacted_at" timestamp with time zone,
    "last_call_contacted_at" timestamp with time zone,
    "lead_name" "text",
    "lead_mobile" numeric,
    "fk_lead_status" bigint DEFAULT '1'::bigint,
    "fk_lead_stage" bigint DEFAULT '1'::bigint,
    "fk_presales_owner" bigint,
    "fk_sales_owner" bigint,
    "fk_next_action" bigint,
    "next_action_at" timestamp with time zone,
    "requirement_captured" boolean,
    "requirement_captured_at" timestamp with time zone,
    "options_shared" boolean,
    "options_shared_at" timestamp with time zone,
    "visit_scheduled_at" timestamp with time zone,
    "visit_status" boolean,
    "lead_source" "text",
    "fk_lead_communication_id" bigint,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."leads_master_duplicate" OWNER TO "postgres";


COMMENT ON TABLE "public"."leads_master_duplicate" IS 'This is a duplicate of leads_master';



ALTER TABLE "public"."leads_master_duplicate" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."leads_master_duplicate_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



ALTER TABLE "public"."leads_master" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."leads_master_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."message_queue" (
    "message_queue_id" integer NOT NULL,
    "recipient_type" character varying(30) NOT NULL,
    "scheduled_send_at" timestamp with time zone NOT NULL,
    "status" character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    "read_receipt" boolean,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    "status_reason" "text",
    "status_changed_at" timestamp with time zone,
    "sent_at" timestamp with time zone,
    "fk_source_type_id" bigint NOT NULL,
    "source_id" bigint NOT NULL,
    "fk_message_template_id" bigint NOT NULL,
    "recipient_phone" character varying(30) NOT NULL,
    "recipient_name" "text",
    "attempt_count" integer DEFAULT 0 NOT NULL,
    "last_attempt_at" timestamp with time zone,
    "next_retry_at" timestamp with time zone,
    "expires_at" timestamp with time zone,
    "metadata" "jsonb" DEFAULT '{}'::"jsonb" NOT NULL,
    CONSTRAINT "message_queue_attempt_count_check" CHECK (("attempt_count" >= 0)),
    CONSTRAINT "message_queue_recipient_phone_check" CHECK (("btrim"(("recipient_phone")::"text") <> ''::"text")),
    CONSTRAINT "message_queue_recipient_type_check" CHECK ((("recipient_type")::"text" = ANY (ARRAY[('CLIENT'::character varying)::"text", ('SALESPERSON'::character varying)::"text", ('MANAGER'::character varying)::"text"]))),
    CONSTRAINT "message_queue_status_check" CHECK ((("status")::"text" = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'DISPATCHED'::character varying, 'CANCELLED'::character varying, 'FAILED'::character varying])::"text"[])))
);


ALTER TABLE "public"."message_queue" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."message_queue_message_queue_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."message_queue_message_queue_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."message_queue_message_queue_id_seq" OWNED BY "public"."message_queue"."message_queue_id";



CREATE TABLE IF NOT EXISTS "public"."message_template" (
    "id" bigint NOT NULL,
    "message_type" character varying(50) NOT NULL,
    "message_body" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."message_template" OWNER TO "postgres";


ALTER TABLE "public"."message_template" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."message_template_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."next_actions" (
    "id" bigint NOT NULL,
    "name" "text" NOT NULL,
    "sort_order" integer NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."next_actions" OWNER TO "postgres";


ALTER TABLE "public"."next_actions" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."next_actions_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."people" (
    "id" "text" NOT NULL,
    "display_name" "text" NOT NULL,
    "company_name" "text",
    "notes" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."people" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."phone_numbers" (
    "id" "text" NOT NULL,
    "person_id" "text" NOT NULL,
    "phone_number" "text" NOT NULL,
    "normalized_number" "text" NOT NULL,
    "phone_type" "text" DEFAULT 'PRIMARY'::"text" NOT NULL,
    "is_primary" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."phone_numbers" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."reminder_type_config" (
    "reminder_type_id" integer NOT NULL,
    "reminder_type" character varying(50) NOT NULL,
    "offset_minutes" integer NOT NULL,
    "description" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "reminder_type_config_offset_minutes_check" CHECK (("offset_minutes" >= 0))
);


ALTER TABLE "public"."reminder_type_config" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."reminder_type_config_reminder_type_id_seq"
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."reminder_type_config_reminder_type_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."reminder_type_config_reminder_type_id_seq" OWNED BY "public"."reminder_type_config"."reminder_type_id";



CREATE TABLE IF NOT EXISTS "public"."sales_calls" (
    "id" bigint NOT NULL,
    "salesperson_phone" "text" NOT NULL,
    "salesperson_name" "text",
    "buyer_phone" "text" NOT NULL,
    "buyer_name" "text",
    "call_type" "text" NOT NULL,
    "call_id" bigint NOT NULL,
    "duration" integer NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "person_id" "text"
);


ALTER TABLE "public"."sales_calls" OWNER TO "postgres";


CREATE SEQUENCE IF NOT EXISTS "public"."sales_calls_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE "public"."sales_calls_id_seq" OWNER TO "postgres";


ALTER SEQUENCE "public"."sales_calls_id_seq" OWNED BY "public"."sales_calls"."id";



CREATE TABLE IF NOT EXISTS "public"."source_type" (
    "id" bigint NOT NULL,
    "source_type" character varying(50) NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."source_type" OWNER TO "postgres";


ALTER TABLE "public"."source_type" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."source_type_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."team_member_daily_remarks" (
    "id" bigint NOT NULL,
    "fk_team_member" bigint NOT NULL,
    "report_date" "date" NOT NULL,
    "remarks" "text" DEFAULT ''::"text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


ALTER TABLE "public"."team_member_daily_remarks" OWNER TO "postgres";


ALTER TABLE "public"."team_member_daily_remarks" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."team_member_daily_remarks_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."team_members" (
    "id" bigint NOT NULL,
    "name" "text" NOT NULL,
    "role" "text" NOT NULL,
    "phone" "text" NOT NULL,
    "email" "text" NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "status" "text" DEFAULT 'active'::"text" NOT NULL,
    "mobile" "text" DEFAULT ''::"text" NOT NULL,
    "personal_info" "jsonb" DEFAULT '{}'::"jsonb",
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "supabase_user_id" "uuid",
    "in_lead_distribution" boolean DEFAULT false NOT NULL,
    CONSTRAINT "team_members_role_check" CHECK (("role" = ANY (ARRAY['admin'::"text", 'tech'::"text", 'lead_manager'::"text", 'presales'::"text", 'sales'::"text"])))
);


ALTER TABLE "public"."team_members" OWNER TO "postgres";


ALTER TABLE "public"."team_members" ALTER COLUMN "id" ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "public"."team_members_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);



CREATE TABLE IF NOT EXISTS "public"."user_profiles" (
    "user_id" "uuid" NOT NULL,
    "email" "public"."citext" NOT NULL,
    "full_name" "text" DEFAULT ''::"text" NOT NULL,
    "roles" "text"[] DEFAULT '{}'::"text"[] NOT NULL,
    "phone" "text" DEFAULT ''::"text" NOT NULL,
    "is_active" boolean DEFAULT true NOT NULL,
    "legacy_migrated_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "roles_no_nulls" CHECK (("array_position"("roles", NULL::"text") IS NULL)),
    CONSTRAINT "roles_one_desk" CHECK ((NOT ("roles" @> ARRAY['presales'::"text", 'sales'::"text"]))),
    CONSTRAINT "roles_valid" CHECK (("roles" <@ ARRAY['admin'::"text", 'tech'::"text", 'manager'::"text", 'editor'::"text", 'viewer'::"text", 'lead_manager'::"text", 'presales'::"text", 'sales'::"text"]))
);


ALTER TABLE "public"."user_profiles" OWNER TO "postgres";


COMMENT ON TABLE "public"."user_profiles" IS 'One row per person. Roles combine as a union; see app/capabilities.py for the matrix.';



COMMENT ON COLUMN "public"."user_profiles"."roles" IS 'Union-combined role set. Empty means the account exists with no app access (the offboarding state), which is distinct from is_active = false.';



ALTER TABLE ONLY "public"."lead_site_visits" ALTER COLUMN "visit_id" SET DEFAULT "nextval"('"public"."lead_site_visits_visit_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."message_queue" ALTER COLUMN "message_queue_id" SET DEFAULT "nextval"('"public"."message_queue_message_queue_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."reminder_type_config" ALTER COLUMN "reminder_type_id" SET DEFAULT "nextval"('"public"."reminder_type_config_reminder_type_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."sales_calls" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."sales_calls_id_seq"'::"regclass");



ALTER TABLE ONLY "public"."calls_log"
    ADD CONSTRAINT "calls_log_pkey" PRIMARY KEY ("phone_number");



ALTER TABLE ONLY "public"."communication_channels"
    ADD CONSTRAINT "communication_channels_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."communication_channels"
    ADD CONSTRAINT "communication_channels_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."contact_aliases"
    ADD CONSTRAINT "contact_aliases_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."conversation_messages"
    ADD CONSTRAINT "conversation_messages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."devices"
    ADD CONSTRAINT "devices_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."django_migrations"
    ADD CONSTRAINT "django_migrations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_actions"
    ADD CONSTRAINT "lead_actions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_ad_routing"
    ADD CONSTRAINT "lead_ad_routing_ad_id_key" UNIQUE ("ad_id");



ALTER TABLE ONLY "public"."lead_ad_routing"
    ADD CONSTRAINT "lead_ad_routing_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_chatbot_status"
    ADD CONSTRAINT "lead_chatbot_status_lead_id_key" UNIQUE ("lead_id");



ALTER TABLE ONLY "public"."lead_chatbot_status"
    ADD CONSTRAINT "lead_chatbot_status_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_communications"
    ADD CONSTRAINT "lead_communications_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_consultations"
    ADD CONSTRAINT "lead_consultations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_distribution_settings"
    ADD CONSTRAINT "lead_distribution_settings_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_notes"
    ADD CONSTRAINT "lead_notes_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_requirements_duplicate"
    ADD CONSTRAINT "lead_requirements_duplicate_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_requirements"
    ADD CONSTRAINT "lead_requirements_lead_id_09024d40_uniq" UNIQUE ("lead_id");



ALTER TABLE ONLY "public"."lead_requirements"
    ADD CONSTRAINT "lead_requirements_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_shared_options"
    ADD CONSTRAINT "lead_shared_options_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_site_visits"
    ADD CONSTRAINT "lead_site_visits_pkey" PRIMARY KEY ("visit_id");



ALTER TABLE ONLY "public"."lead_sources"
    ADD CONSTRAINT "lead_sources_code_key" UNIQUE ("code");



ALTER TABLE ONLY "public"."lead_sources"
    ADD CONSTRAINT "lead_sources_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."lead_sources"
    ADD CONSTRAINT "lead_sources_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_stages"
    ADD CONSTRAINT "lead_stages_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."lead_stages"
    ADD CONSTRAINT "lead_stages_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_state_history"
    ADD CONSTRAINT "lead_state_history_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_statuses"
    ADD CONSTRAINT "lead_statuses_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."lead_statuses"
    ADD CONSTRAINT "lead_statuses_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."lead_tasks"
    ADD CONSTRAINT "lead_tasks_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_hash_key_key" UNIQUE ("fk_hash_key");



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "leads_master_fk_hash_key_key" UNIQUE ("fk_hash_key");



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "leads_master_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."leads"
    ADD CONSTRAINT "leads_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."message_queue"
    ADD CONSTRAINT "message_queue_pkey" PRIMARY KEY ("message_queue_id");



ALTER TABLE ONLY "public"."message_template"
    ADD CONSTRAINT "message_template_message_type_key" UNIQUE ("message_type");



ALTER TABLE ONLY "public"."message_template"
    ADD CONSTRAINT "message_template_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."next_actions"
    ADD CONSTRAINT "next_actions_name_key" UNIQUE ("name");



ALTER TABLE ONLY "public"."next_actions"
    ADD CONSTRAINT "next_actions_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."people"
    ADD CONSTRAINT "people_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."phone_numbers"
    ADD CONSTRAINT "phone_numbers_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."reminder_type_config"
    ADD CONSTRAINT "reminder_type_config_pkey" PRIMARY KEY ("reminder_type_id");



ALTER TABLE ONLY "public"."reminder_type_config"
    ADD CONSTRAINT "reminder_type_config_reminder_type_key" UNIQUE ("reminder_type");



ALTER TABLE ONLY "public"."sales_calls"
    ADD CONSTRAINT "sales_calls_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."sales_calls"
    ADD CONSTRAINT "sales_calls_salesperson_call_unique" UNIQUE ("salesperson_phone", "call_id");



ALTER TABLE ONLY "public"."sales_calls"
    ADD CONSTRAINT "sales_calls_salesperson_phone_call_id_key" UNIQUE ("salesperson_phone", "call_id");



ALTER TABLE ONLY "public"."source_type"
    ADD CONSTRAINT "source_type_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."source_type"
    ADD CONSTRAINT "source_type_source_type_key" UNIQUE ("source_type");



ALTER TABLE ONLY "public"."team_member_daily_remarks"
    ADD CONSTRAINT "team_member_daily_remarks_member_day_key" UNIQUE ("fk_team_member", "report_date");



ALTER TABLE ONLY "public"."team_member_daily_remarks"
    ADD CONSTRAINT "team_member_daily_remarks_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."team_members"
    ADD CONSTRAINT "team_members_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."team_members"
    ADD CONSTRAINT "team_members_phone_key" UNIQUE ("phone");



ALTER TABLE ONLY "public"."team_members"
    ADD CONSTRAINT "team_members_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."team_members"
    ADD CONSTRAINT "team_members_supabase_user_id_key" UNIQUE ("supabase_user_id");



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "uq_conversations_person" UNIQUE ("person_id");



ALTER TABLE ONLY "public"."contact_aliases"
    ADD CONSTRAINT "uq_device_alias_phone" UNIQUE ("device_id", "normalized_number");



ALTER TABLE ONLY "public"."devices"
    ADD CONSTRAINT "uq_device_identifier" UNIQUE ("device_identifier");



ALTER TABLE ONLY "public"."leads"
    ADD CONSTRAINT "uq_leads_person" UNIQUE ("person_id");



ALTER TABLE ONLY "public"."phone_numbers"
    ADD CONSTRAINT "uq_phone_numbers_normalized" UNIQUE ("normalized_number");



ALTER TABLE ONLY "public"."sales_calls"
    ADD CONSTRAINT "uq_salesperson_call" UNIQUE ("salesperson_phone", "call_id");



ALTER TABLE ONLY "public"."user_profiles"
    ADD CONSTRAINT "user_profiles_email_key" UNIQUE ("email");



ALTER TABLE ONLY "public"."user_profiles"
    ADD CONSTRAINT "user_profiles_pkey" PRIMARY KEY ("user_id");



CREATE INDEX "idx_contact_aliases_alias_name" ON "public"."contact_aliases" USING "btree" ("alias_name");



CREATE INDEX "idx_contact_aliases_device_id" ON "public"."contact_aliases" USING "btree" ("device_id");



CREATE INDEX "idx_contact_aliases_normalized" ON "public"."contact_aliases" USING "btree" ("normalized_number");



CREATE INDEX "idx_contact_aliases_person_id" ON "public"."contact_aliases" USING "btree" ("person_id");



CREATE INDEX "idx_conversation_messages_conversation_id" ON "public"."conversation_messages" USING "btree" ("conversation_id");



CREATE INDEX "idx_conversation_messages_timestamp" ON "public"."conversation_messages" USING "btree" ("timestamp");



CREATE INDEX "idx_conversations_person_id" ON "public"."conversations" USING "btree" ("person_id");



CREATE INDEX "idx_conversations_status" ON "public"."conversations" USING "btree" ("status");



CREATE INDEX "idx_conversations_updated_at" ON "public"."conversations" USING "btree" ("updated_at");



CREATE INDEX "idx_conversations_whatsapp_number" ON "public"."conversations" USING "btree" ("whatsapp_number");



CREATE INDEX "idx_devices_identifier" ON "public"."devices" USING "btree" ("device_identifier");



CREATE INDEX "idx_lead_comm_call_id" ON "public"."lead_communications" USING "btree" ("call_id");



CREATE INDEX "idx_lead_comm_created_at" ON "public"."lead_communications" USING "btree" ("created_at");



CREATE INDEX "idx_lead_comm_phone" ON "public"."lead_communications" USING "btree" ("phone_number");



CREATE INDEX "idx_lead_communications_created_at" ON "public"."lead_communications" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_lead_communications_lead_id" ON "public"."lead_communications" USING "btree" ("fk_lead_id");



CREATE INDEX "idx_lead_consultations_lead_id" ON "public"."lead_consultations" USING "btree" ("lead_id", "scheduled_at" DESC);



CREATE INDEX "idx_lead_notes_created_at" ON "public"."lead_notes" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_lead_notes_lead_id" ON "public"."lead_notes" USING "btree" ("lead_id");



CREATE INDEX "idx_lead_requirements_created_at" ON "public"."lead_requirements" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_lead_requirements_lead_id" ON "public"."lead_requirements" USING "btree" ("lead_id");



CREATE INDEX "idx_lead_shared_options_lead_id" ON "public"."lead_shared_options" USING "btree" ("lead_id", "created_at" DESC);



CREATE INDEX "idx_lead_shared_options_property_ids" ON "public"."lead_shared_options" USING "gin" ("property_ids");



CREATE INDEX "idx_lead_site_visits_fk_lead_id" ON "public"."lead_site_visits" USING "btree" ("fk_lead_id");



CREATE INDEX "idx_lead_site_visits_fk_reminder_type_id" ON "public"."lead_site_visits" USING "btree" ("fk_reminder_type_id");



CREATE INDEX "idx_lead_site_visits_fk_team_member_id" ON "public"."lead_site_visits" USING "btree" ("fk_team_member_id");



CREATE INDEX "idx_lead_site_visits_visit_time" ON "public"."lead_site_visits" USING "btree" ("visit_time");



CREATE INDEX "idx_lead_state_history_changed" ON "public"."lead_state_history" USING "btree" ("changed_at");



CREATE INDEX "idx_lead_state_history_lead_changed" ON "public"."lead_state_history" USING "btree" ("lead_id", "changed_at");



CREATE INDEX "idx_lead_tasks_assignee_due" ON "public"."lead_tasks" USING "btree" ("fk_assignee", "due_at") WHERE ("status" = 'pending'::"text");



CREATE INDEX "idx_lead_tasks_lead_id" ON "public"."lead_tasks" USING "btree" ("lead_id", "task_type");



CREATE INDEX "idx_lead_tasks_parent_key" ON "public"."lead_tasks" USING "btree" ("parent_key") WHERE ("parent_key" <> ''::"text");



CREATE INDEX "idx_leads_master_fk_lead_source" ON "public"."leads_master" USING "btree" ("fk_lead_source");



CREATE INDEX "idx_leads_next_follow_up" ON "public"."leads" USING "btree" ("next_follow_up_at");



CREATE INDEX "idx_leads_person_id" ON "public"."leads" USING "btree" ("person_id");



CREATE INDEX "idx_leads_priority" ON "public"."leads" USING "btree" ("priority");



CREATE INDEX "idx_leads_status" ON "public"."leads" USING "btree" ("status");



CREATE INDEX "idx_leads_updated_at" ON "public"."leads" USING "btree" ("updated_at");



CREATE INDEX "idx_message_queue_due" ON "public"."message_queue" USING "btree" ("scheduled_send_at") WHERE (("status")::"text" = 'PENDING'::"text");



CREATE INDEX "idx_message_queue_expiring" ON "public"."message_queue" USING "btree" ("expires_at") WHERE (("status")::"text" = 'PENDING'::"text");



CREATE INDEX "idx_message_queue_next_retry" ON "public"."message_queue" USING "btree" ("next_retry_at") WHERE ("next_retry_at" IS NOT NULL);



CREATE INDEX "idx_message_queue_source" ON "public"."message_queue" USING "btree" ("fk_source_type_id", "source_id");



CREATE INDEX "idx_message_queue_status_scheduled" ON "public"."message_queue" USING "btree" ("status", "scheduled_send_at");



CREATE INDEX "idx_people_display_name" ON "public"."people" USING "btree" ("display_name");



CREATE INDEX "idx_phone_numbers_normalized" ON "public"."phone_numbers" USING "btree" ("normalized_number");



CREATE INDEX "idx_phone_numbers_person_id" ON "public"."phone_numbers" USING "btree" ("person_id");



CREATE INDEX "idx_sales_calls_buyer" ON "public"."sales_calls" USING "btree" ("buyer_phone");



CREATE INDEX "idx_sales_calls_buyer_phone_last10" ON "public"."sales_calls" USING "btree" ("right"("buyer_phone", 10));



CREATE INDEX "idx_sales_calls_created_at" ON "public"."sales_calls" USING "btree" ("created_at");



CREATE INDEX "idx_sales_calls_outgoing_by_caller" ON "public"."sales_calls" USING "btree" ("salesperson_name", "created_at") WHERE ("call_type" = 'OUTGOING'::"text");



CREATE INDEX "idx_sales_calls_outgoing_first_call" ON "public"."sales_calls" USING "btree" ("salesperson_name", COALESCE("right"("buyer_phone", 10), ''::"text"), "created_at", "id") WHERE ("call_type" = 'OUTGOING'::"text");



CREATE INDEX "idx_sales_calls_person_id" ON "public"."sales_calls" USING "btree" ("person_id");



CREATE INDEX "idx_sales_calls_salesperson" ON "public"."sales_calls" USING "btree" ("salesperson_phone");



CREATE INDEX "lead_ad_routing_ad_id_122b2ed1_like" ON "public"."lead_ad_routing" USING "btree" ("ad_id" "varchar_pattern_ops");



CREATE INDEX "lead_ad_routing_fk_presales_owner_830f40df" ON "public"."lead_ad_routing" USING "btree" ("fk_presales_owner");



CREATE INDEX "lead_requirements_duplicate_created_at_idx" ON "public"."lead_requirements_duplicate" USING "btree" ("created_at" DESC);



CREATE INDEX "lead_requirements_duplicate_lead_id_idx" ON "public"."lead_requirements_duplicate" USING "btree" ("lead_id");



CREATE UNIQUE INDEX "lead_tasks_one_open_per_type" ON "public"."lead_tasks" USING "btree" ("lead_id", "task_type") WHERE ("status" = 'pending'::"text");



CREATE INDEX "leads_master_ad_id_f6be183b" ON "public"."leads_master" USING "btree" ("ad_id");



CREATE INDEX "leads_master_ad_id_f6be183b_like" ON "public"."leads_master" USING "btree" ("ad_id" "varchar_pattern_ops");



CREATE UNIQUE INDEX "uq_message_queue_one_open_per_source" ON "public"."message_queue" USING "btree" ("fk_source_type_id", "source_id", "fk_message_template_id", "recipient_type", COALESCE(("metadata" ->> 'reminder_type'::"text"), ''::"text")) WHERE (("status")::"text" = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying])::"text"[]));



CREATE OR REPLACE TRIGGER "trg_contact_aliases_cleanup_orphans" AFTER INSERT OR UPDATE ON "public"."contact_aliases" FOR EACH STATEMENT EXECUTE FUNCTION "public"."trg_after_alias_cleanup_orphans"();



CREATE OR REPLACE TRIGGER "trg_contact_aliases_enforce_canonical" BEFORE INSERT OR UPDATE ON "public"."contact_aliases" FOR EACH ROW EXECUTE FUNCTION "public"."trg_enforce_canonical_person_on_alias"();



CREATE OR REPLACE TRIGGER "trg_contact_aliases_normalize" BEFORE INSERT OR UPDATE ON "public"."contact_aliases" FOR EACH ROW EXECUTE FUNCTION "public"."trg_normalize_phone_number"();



CREATE OR REPLACE TRIGGER "trg_lead_comm_link_lead" BEFORE INSERT OR UPDATE ON "public"."lead_communications" FOR EACH ROW EXECUTE FUNCTION "public"."trg_lead_communications_link_lead"();



CREATE OR REPLACE TRIGGER "trg_lead_site_visits_updated_at" BEFORE UPDATE ON "public"."lead_site_visits" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_log_lead_state_insert" AFTER INSERT ON "public"."leads_master" FOR EACH ROW EXECUTE FUNCTION "public"."log_lead_state"();



CREATE OR REPLACE TRIGGER "trg_log_lead_state_update" AFTER UPDATE OF "fk_lead_status", "fk_lead_stage" ON "public"."leads_master" FOR EACH ROW WHEN ((("new"."fk_lead_status" IS DISTINCT FROM "old"."fk_lead_status") OR ("new"."fk_lead_stage" IS DISTINCT FROM "old"."fk_lead_stage"))) EXECUTE FUNCTION "public"."log_lead_state"();



CREATE OR REPLACE TRIGGER "trg_message_queue_updated_at" BEFORE UPDATE ON "public"."message_queue" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_message_template_updated_at" BEFORE UPDATE ON "public"."message_template" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_phone_numbers_normalize" BEFORE INSERT OR UPDATE ON "public"."phone_numbers" FOR EACH ROW EXECUTE FUNCTION "public"."trg_normalize_phone_number"();



CREATE OR REPLACE TRIGGER "trg_reminder_type_config_updated_at" BEFORE UPDATE ON "public"."reminder_type_config" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "trg_sales_calls_enforce_canonical" BEFORE INSERT OR UPDATE ON "public"."sales_calls" FOR EACH ROW EXECUTE FUNCTION "public"."trg_enforce_canonical_person_on_call"();



CREATE OR REPLACE TRIGGER "trg_set_lead_source_fk_insert" BEFORE INSERT ON "public"."leads_master" FOR EACH ROW WHEN ((("new"."fk_lead_source" IS NULL) AND ("new"."lead_source" IS NOT NULL) AND ("new"."lead_source" <> ''::"text"))) EXECUTE FUNCTION "public"."set_lead_source_fk"();



CREATE OR REPLACE TRIGGER "trg_set_lead_source_fk_update" BEFORE UPDATE ON "public"."leads_master" FOR EACH ROW WHEN ((("new"."fk_lead_source" IS NULL) AND ("new"."lead_source" IS NOT NULL) AND ("new"."lead_source" <> ''::"text") AND ("new"."lead_source" IS DISTINCT FROM "old"."lead_source"))) EXECUTE FUNCTION "public"."set_lead_source_fk"();



CREATE OR REPLACE TRIGGER "trg_source_type_updated_at" BEFORE UPDATE ON "public"."source_type" FOR EACH ROW EXECUTE FUNCTION "public"."set_updated_at"();



CREATE OR REPLACE TRIGGER "user_profiles_set_updated_at" BEFORE UPDATE ON "public"."user_profiles" FOR EACH ROW EXECUTE FUNCTION "public"."identity_set_updated_at"();



ALTER TABLE ONLY "public"."contact_aliases"
    ADD CONSTRAINT "contact_aliases_device_id_fkey" FOREIGN KEY ("device_id") REFERENCES "public"."devices"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."contact_aliases"
    ADD CONSTRAINT "contact_aliases_person_id_fkey" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversation_messages"
    ADD CONSTRAINT "conversation_messages_conversation_id_fkey" FOREIGN KEY ("conversation_id") REFERENCES "public"."conversations"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."conversations"
    ADD CONSTRAINT "conversations_person_id_fkey" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_distribution_settings"
    ADD CONSTRAINT "fk_lead_distribution_settings_last_member" FOREIGN KEY ("fk_last_assigned_member") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_lead_source" FOREIGN KEY ("fk_lead_source") REFERENCES "public"."lead_sources"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_lead_stage" FOREIGN KEY ("fk_lead_stage") REFERENCES "public"."lead_stages"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_lead_status" FOREIGN KEY ("fk_lead_status") REFERENCES "public"."lead_statuses"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_next_action" FOREIGN KEY ("fk_next_action") REFERENCES "public"."next_actions"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_presales_owner" FOREIGN KEY ("fk_presales_owner") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master"
    ADD CONSTRAINT "fk_leads_master_sales_owner" FOREIGN KEY ("fk_sales_owner") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."sales_calls"
    ADD CONSTRAINT "fk_sales_calls_person_id" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."lead_ad_routing"
    ADD CONSTRAINT "lead_ad_routing_fk_presales_owner_830f40df_fk_team_members_id" FOREIGN KEY ("fk_presales_owner") REFERENCES "public"."team_members"("id") DEFERRABLE INITIALLY DEFERRED;



ALTER TABLE ONLY "public"."lead_chatbot_status"
    ADD CONSTRAINT "lead_chatbot_status_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_communications"
    ADD CONSTRAINT "lead_communications_fk_channel_id_fkey" FOREIGN KEY ("fk_channel_id") REFERENCES "public"."communication_channels"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."lead_communications"
    ADD CONSTRAINT "lead_communications_fk_lead_id_fkey" FOREIGN KEY ("fk_lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_communications"
    ADD CONSTRAINT "lead_communications_fk_sender_id_fkey" FOREIGN KEY ("fk_sender_id") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."lead_consultations"
    ADD CONSTRAINT "lead_consultations_fk_consultant_fkey" FOREIGN KEY ("fk_consultant") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."lead_consultations"
    ADD CONSTRAINT "lead_consultations_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_notes"
    ADD CONSTRAINT "lead_notes_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_requirements_duplicate"
    ADD CONSTRAINT "lead_requirements_duplicate_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_requirements"
    ADD CONSTRAINT "lead_requirements_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_shared_options"
    ADD CONSTRAINT "lead_shared_options_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_site_visits"
    ADD CONSTRAINT "lead_site_visits_fk_lead_id_fkey" FOREIGN KEY ("fk_lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_site_visits"
    ADD CONSTRAINT "lead_site_visits_fk_reminder_type_id_fkey" FOREIGN KEY ("fk_reminder_type_id") REFERENCES "public"."reminder_type_config"("reminder_type_id");



ALTER TABLE ONLY "public"."lead_site_visits"
    ADD CONSTRAINT "lead_site_visits_fk_team_member_id_fkey" FOREIGN KEY ("fk_team_member_id") REFERENCES "public"."team_members"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_state_history"
    ADD CONSTRAINT "lead_state_history_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."lead_tasks"
    ADD CONSTRAINT "lead_tasks_fk_assignee_fkey" FOREIGN KEY ("fk_assignee") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."lead_tasks"
    ADD CONSTRAINT "lead_tasks_lead_id_fkey" FOREIGN KEY ("lead_id") REFERENCES "public"."leads_master"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_lead_stage_fkey" FOREIGN KEY ("fk_lead_stage") REFERENCES "public"."lead_stages"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_lead_status_fkey" FOREIGN KEY ("fk_lead_status") REFERENCES "public"."lead_statuses"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_next_action_fkey" FOREIGN KEY ("fk_next_action") REFERENCES "public"."next_actions"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_presales_owner_fkey" FOREIGN KEY ("fk_presales_owner") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads_master_duplicate"
    ADD CONSTRAINT "leads_master_duplicate_fk_sales_owner_fkey" FOREIGN KEY ("fk_sales_owner") REFERENCES "public"."team_members"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."leads"
    ADD CONSTRAINT "leads_person_id_fkey" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."message_queue"
    ADD CONSTRAINT "message_queue_fk_message_template_id_fkey" FOREIGN KEY ("fk_message_template_id") REFERENCES "public"."message_template"("id");



ALTER TABLE ONLY "public"."message_queue"
    ADD CONSTRAINT "message_queue_fk_source_type_id_fkey" FOREIGN KEY ("fk_source_type_id") REFERENCES "public"."source_type"("id");



ALTER TABLE ONLY "public"."phone_numbers"
    ADD CONSTRAINT "phone_numbers_person_id_fkey" FOREIGN KEY ("person_id") REFERENCES "public"."people"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."team_member_daily_remarks"
    ADD CONSTRAINT "team_member_daily_remarks_fk_team_member_fkey" FOREIGN KEY ("fk_team_member") REFERENCES "public"."team_members"("id") ON DELETE CASCADE;



ALTER TABLE ONLY "public"."user_profiles"
    ADD CONSTRAINT "user_profiles_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;



CREATE POLICY "Allow anon all on calls_log" ON "public"."calls_log" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on contact_aliases" ON "public"."contact_aliases" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on conversation_messages" ON "public"."conversation_messages" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on conversations" ON "public"."conversations" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on devices" ON "public"."devices" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on lead_communications" ON "public"."lead_communications" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on lead_sources" ON "public"."lead_sources" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on lead_stages" ON "public"."lead_stages" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on lead_statuses" ON "public"."lead_statuses" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on leads" ON "public"."leads" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on leads_master" ON "public"."leads_master" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on next_actions" ON "public"."next_actions" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on people" ON "public"."people" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on phone_numbers" ON "public"."phone_numbers" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on sales_calls" ON "public"."sales_calls" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anon all on team_members" ON "public"."team_members" TO "authenticated", "anon" USING (true) WITH CHECK (true);



CREATE POLICY "Allow anonymous inserts" ON "public"."sales_calls" FOR INSERT TO "anon" WITH CHECK (true);



CREATE POLICY "Allow anonymous select" ON "public"."sales_calls" FOR SELECT TO "anon" USING (true);



CREATE POLICY "Allow anonymous updates" ON "public"."sales_calls" FOR UPDATE TO "anon" USING (true) WITH CHECK (true);



ALTER TABLE "public"."LeadSiteVisit_backup_20260918" ENABLE ROW LEVEL SECURITY;


CREATE POLICY "auth_admin_reads_profiles" ON "public"."user_profiles" FOR SELECT TO "supabase_auth_admin" USING (true);



ALTER TABLE "public"."calls_log" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."communication_channels" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."contact_aliases" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."conversation_messages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."conversations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."devices" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."django_migrations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_actions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_ad_routing" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_chatbot_status" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_communications" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_consultations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_distribution_settings" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_notes" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_requirements" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_requirements_duplicate" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_shared_options" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_site_visits" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_sources" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_stages" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_state_history" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_statuses" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."lead_tasks" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."leads" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."leads_master" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."leads_master_duplicate" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."message_queue" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."message_template" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."next_actions" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."people" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."phone_numbers" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."reminder_type_config" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."sales_calls" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."source_type" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."team_member_daily_remarks" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."team_members" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."user_profiles" ENABLE ROW LEVEL SECURITY;


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";
GRANT USAGE ON SCHEMA "public" TO "supabase_auth_admin";



GRANT ALL ON FUNCTION "public"."cleanup_orphaned_people"() TO "anon";
GRANT ALL ON FUNCTION "public"."cleanup_orphaned_people"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."cleanup_orphaned_people"() TO "service_role";



REVOKE ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") TO "service_role";
GRANT ALL ON FUNCTION "public"."custom_access_token_hook"("event" "jsonb") TO "supabase_auth_admin";



GRANT ALL ON FUNCTION "public"."handover_conversation"("p_conversation_id" "text", "p_reason" "text", "p_user_id" "text", "p_user_name" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."handover_conversation"("p_conversation_id" "text", "p_reason" "text", "p_user_id" "text", "p_user_name" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."handover_conversation"("p_conversation_id" "text", "p_reason" "text", "p_user_id" "text", "p_user_name" "text") TO "service_role";



REVOKE ALL ON FUNCTION "public"."identity_revoke_user_sessions"("target_user_id" "uuid") FROM PUBLIC;
GRANT ALL ON FUNCTION "public"."identity_revoke_user_sessions"("target_user_id" "uuid") TO "service_role";



GRANT ALL ON FUNCTION "public"."identity_set_updated_at"() TO "anon";
GRANT ALL ON FUNCTION "public"."identity_set_updated_at"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."identity_set_updated_at"() TO "service_role";



GRANT ALL ON FUNCTION "public"."log_lead_state"() TO "anon";
GRANT ALL ON FUNCTION "public"."log_lead_state"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."log_lead_state"() TO "service_role";



GRANT ALL ON FUNCTION "public"."normalize_phone_number"("raw" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."normalize_phone_number"("raw" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."normalize_phone_number"("raw" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."normalize_phone_with_country_code"("p_phone" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."normalize_phone_with_country_code"("p_phone" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."normalize_phone_with_country_code"("p_phone" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."resolve_and_attach_contact"("p_device_id" "text", "p_android_contact_id" "text", "p_alias_name" "text", "p_phone_number" "text", "p_normalized_number" "text", "p_device_name" "text", "p_device_phone" "text", "p_device_identifier" "text") TO "anon";
GRANT ALL ON FUNCTION "public"."resolve_and_attach_contact"("p_device_id" "text", "p_android_contact_id" "text", "p_alias_name" "text", "p_phone_number" "text", "p_normalized_number" "text", "p_device_name" "text", "p_device_phone" "text", "p_device_identifier" "text") TO "authenticated";
GRANT ALL ON FUNCTION "public"."resolve_and_attach_contact"("p_device_id" "text", "p_android_contact_id" "text", "p_alias_name" "text", "p_phone_number" "text", "p_normalized_number" "text", "p_device_name" "text", "p_device_phone" "text", "p_device_identifier" "text") TO "service_role";



GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "anon";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."rls_auto_enable"() TO "service_role";



GRANT ALL ON FUNCTION "public"."set_lead_source_fk"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_lead_source_fk"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_lead_source_fk"() TO "service_role";



GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "anon";
GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."set_updated_at"() TO "service_role";



GRANT ALL ON FUNCTION "public"."trg_after_alias_cleanup_orphans"() TO "anon";
GRANT ALL ON FUNCTION "public"."trg_after_alias_cleanup_orphans"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trg_after_alias_cleanup_orphans"() TO "service_role";



GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_alias"() TO "anon";
GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_alias"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_alias"() TO "service_role";



GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_call"() TO "anon";
GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_call"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trg_enforce_canonical_person_on_call"() TO "service_role";



GRANT ALL ON FUNCTION "public"."trg_lead_communications_link_lead"() TO "anon";
GRANT ALL ON FUNCTION "public"."trg_lead_communications_link_lead"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trg_lead_communications_link_lead"() TO "service_role";



GRANT ALL ON FUNCTION "public"."trg_normalize_phone_number"() TO "anon";
GRANT ALL ON FUNCTION "public"."trg_normalize_phone_number"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."trg_normalize_phone_number"() TO "service_role";



GRANT ALL ON TABLE "public"."lead_site_visits" TO "anon";
GRANT ALL ON TABLE "public"."lead_site_visits" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_site_visits" TO "service_role";



GRANT ALL ON TABLE "public"."LeadSiteVisit" TO "anon";
GRANT ALL ON TABLE "public"."LeadSiteVisit" TO "authenticated";
GRANT ALL ON TABLE "public"."LeadSiteVisit" TO "service_role";



GRANT ALL ON TABLE "public"."LeadSiteVisit_backup_20260918" TO "anon";
GRANT ALL ON TABLE "public"."LeadSiteVisit_backup_20260918" TO "authenticated";
GRANT ALL ON TABLE "public"."LeadSiteVisit_backup_20260918" TO "service_role";



GRANT ALL ON TABLE "public"."calls_log" TO "anon";
GRANT ALL ON TABLE "public"."calls_log" TO "authenticated";
GRANT ALL ON TABLE "public"."calls_log" TO "service_role";



GRANT ALL ON TABLE "public"."communication_channels" TO "anon";
GRANT ALL ON TABLE "public"."communication_channels" TO "authenticated";
GRANT ALL ON TABLE "public"."communication_channels" TO "service_role";



GRANT ALL ON SEQUENCE "public"."communication_channels_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."communication_channels_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."communication_channels_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."contact_aliases" TO "anon";
GRANT ALL ON TABLE "public"."contact_aliases" TO "authenticated";
GRANT ALL ON TABLE "public"."contact_aliases" TO "service_role";



GRANT ALL ON TABLE "public"."conversation_messages" TO "anon";
GRANT ALL ON TABLE "public"."conversation_messages" TO "authenticated";
GRANT ALL ON TABLE "public"."conversation_messages" TO "service_role";



GRANT ALL ON TABLE "public"."conversations" TO "anon";
GRANT ALL ON TABLE "public"."conversations" TO "authenticated";
GRANT ALL ON TABLE "public"."conversations" TO "service_role";



GRANT ALL ON TABLE "public"."devices" TO "anon";
GRANT ALL ON TABLE "public"."devices" TO "authenticated";
GRANT ALL ON TABLE "public"."devices" TO "service_role";



GRANT ALL ON TABLE "public"."django_migrations" TO "anon";
GRANT ALL ON TABLE "public"."django_migrations" TO "authenticated";
GRANT ALL ON TABLE "public"."django_migrations" TO "service_role";



GRANT ALL ON SEQUENCE "public"."django_migrations_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."django_migrations_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."django_migrations_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_actions" TO "anon";
GRANT ALL ON TABLE "public"."lead_actions" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_actions" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_actions_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_actions_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_actions_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_ad_routing" TO "anon";
GRANT ALL ON TABLE "public"."lead_ad_routing" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_ad_routing" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_ad_routing_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_ad_routing_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_ad_routing_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_chatbot_status" TO "anon";
GRANT ALL ON TABLE "public"."lead_chatbot_status" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_chatbot_status" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_chatbot_status_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_chatbot_status_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_chatbot_status_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_communications" TO "anon";
GRANT ALL ON TABLE "public"."lead_communications" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_communications" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_communications_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_communications_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_communications_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_consultations" TO "anon";
GRANT ALL ON TABLE "public"."lead_consultations" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_consultations" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_consultations_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_consultations_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_consultations_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_distribution_settings" TO "anon";
GRANT ALL ON TABLE "public"."lead_distribution_settings" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_distribution_settings" TO "service_role";



GRANT ALL ON TABLE "public"."lead_notes" TO "anon";
GRANT ALL ON TABLE "public"."lead_notes" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_notes" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_notes_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_notes_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_notes_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_requirements" TO "anon";
GRANT ALL ON TABLE "public"."lead_requirements" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_requirements" TO "service_role";



GRANT ALL ON TABLE "public"."lead_requirements_duplicate" TO "anon";
GRANT ALL ON TABLE "public"."lead_requirements_duplicate" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_requirements_duplicate" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_requirements_duplicate_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_requirements_duplicate_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_requirements_duplicate_id_seq" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_requirements_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_requirements_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_requirements_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_shared_options" TO "anon";
GRANT ALL ON TABLE "public"."lead_shared_options" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_shared_options" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_shared_options_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_shared_options_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_shared_options_id_seq" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_site_visits_visit_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_site_visits_visit_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_site_visits_visit_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_sources" TO "anon";
GRANT ALL ON TABLE "public"."lead_sources" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_sources" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_sources_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_sources_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_sources_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_stages" TO "anon";
GRANT ALL ON TABLE "public"."lead_stages" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_stages" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_stages_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_stages_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_stages_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_state_history" TO "anon";
GRANT ALL ON TABLE "public"."lead_state_history" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_state_history" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_state_history_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_state_history_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_state_history_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_statuses" TO "anon";
GRANT ALL ON TABLE "public"."lead_statuses" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_statuses" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_statuses_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_statuses_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_statuses_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."lead_tasks" TO "anon";
GRANT ALL ON TABLE "public"."lead_tasks" TO "authenticated";
GRANT ALL ON TABLE "public"."lead_tasks" TO "service_role";



GRANT ALL ON SEQUENCE "public"."lead_tasks_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."lead_tasks_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."lead_tasks_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."leads" TO "anon";
GRANT ALL ON TABLE "public"."leads" TO "authenticated";
GRANT ALL ON TABLE "public"."leads" TO "service_role";



GRANT ALL ON TABLE "public"."leads_master" TO "anon";
GRANT ALL ON TABLE "public"."leads_master" TO "authenticated";
GRANT ALL ON TABLE "public"."leads_master" TO "service_role";



GRANT ALL ON TABLE "public"."leads_master_duplicate" TO "anon";
GRANT ALL ON TABLE "public"."leads_master_duplicate" TO "authenticated";
GRANT ALL ON TABLE "public"."leads_master_duplicate" TO "service_role";



GRANT ALL ON SEQUENCE "public"."leads_master_duplicate_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."leads_master_duplicate_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."leads_master_duplicate_id_seq" TO "service_role";



GRANT ALL ON SEQUENCE "public"."leads_master_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."leads_master_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."leads_master_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."message_queue" TO "anon";
GRANT ALL ON TABLE "public"."message_queue" TO "authenticated";
GRANT ALL ON TABLE "public"."message_queue" TO "service_role";



GRANT ALL ON SEQUENCE "public"."message_queue_message_queue_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."message_queue_message_queue_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."message_queue_message_queue_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."message_template" TO "anon";
GRANT ALL ON TABLE "public"."message_template" TO "authenticated";
GRANT ALL ON TABLE "public"."message_template" TO "service_role";



GRANT ALL ON SEQUENCE "public"."message_template_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."message_template_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."message_template_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."next_actions" TO "anon";
GRANT ALL ON TABLE "public"."next_actions" TO "authenticated";
GRANT ALL ON TABLE "public"."next_actions" TO "service_role";



GRANT ALL ON SEQUENCE "public"."next_actions_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."next_actions_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."next_actions_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."people" TO "anon";
GRANT ALL ON TABLE "public"."people" TO "authenticated";
GRANT ALL ON TABLE "public"."people" TO "service_role";



GRANT ALL ON TABLE "public"."phone_numbers" TO "anon";
GRANT ALL ON TABLE "public"."phone_numbers" TO "authenticated";
GRANT ALL ON TABLE "public"."phone_numbers" TO "service_role";



GRANT ALL ON TABLE "public"."reminder_type_config" TO "anon";
GRANT ALL ON TABLE "public"."reminder_type_config" TO "authenticated";
GRANT ALL ON TABLE "public"."reminder_type_config" TO "service_role";



GRANT ALL ON SEQUENCE "public"."reminder_type_config_reminder_type_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."reminder_type_config_reminder_type_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."reminder_type_config_reminder_type_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."sales_calls" TO "anon";
GRANT ALL ON TABLE "public"."sales_calls" TO "authenticated";
GRANT ALL ON TABLE "public"."sales_calls" TO "service_role";



GRANT ALL ON SEQUENCE "public"."sales_calls_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."sales_calls_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."sales_calls_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."source_type" TO "anon";
GRANT ALL ON TABLE "public"."source_type" TO "authenticated";
GRANT ALL ON TABLE "public"."source_type" TO "service_role";



GRANT ALL ON SEQUENCE "public"."source_type_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."source_type_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."source_type_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."team_member_daily_remarks" TO "anon";
GRANT ALL ON TABLE "public"."team_member_daily_remarks" TO "authenticated";
GRANT ALL ON TABLE "public"."team_member_daily_remarks" TO "service_role";



GRANT ALL ON SEQUENCE "public"."team_member_daily_remarks_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."team_member_daily_remarks_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."team_member_daily_remarks_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."team_members" TO "anon";
GRANT ALL ON TABLE "public"."team_members" TO "authenticated";
GRANT ALL ON TABLE "public"."team_members" TO "service_role";



GRANT ALL ON SEQUENCE "public"."team_members_id_seq" TO "anon";
GRANT ALL ON SEQUENCE "public"."team_members_id_seq" TO "authenticated";
GRANT ALL ON SEQUENCE "public"."team_members_id_seq" TO "service_role";



GRANT ALL ON TABLE "public"."user_profiles" TO "service_role";
GRANT SELECT ON TABLE "public"."user_profiles" TO "supabase_auth_admin";



ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";







