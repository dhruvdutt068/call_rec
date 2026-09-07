-- ====================================================================
-- PHASE 7: WHATSAPP CONVERSATIONS & HUMAN HANDOVER MIGRATION
-- (TIMESTAMPTZ / DATETIME DATATYPES + RLS POLICIES + STORED PROCEDURES)
-- ====================================================================

-- 1. Create conversations Table
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

-- 2. Create conversation_messages Table
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

-- 3. Create Indexes for fast querying
CREATE INDEX IF NOT EXISTS idx_conversations_person_id ON public.conversations (person_id);
CREATE INDEX IF NOT EXISTS idx_conversations_whatsapp_number ON public.conversations (whatsapp_number);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON public.conversations (status);
CREATE INDEX IF NOT EXISTS idx_conversations_assigned_user_id ON public.conversations (assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON public.conversations (updated_at);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation_id ON public.conversation_messages (conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_timestamp ON public.conversation_messages (timestamp);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_sender_type ON public.conversation_messages (sender_type);

-- 4. Stored Procedure: Atomic Handover to Human
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
BEGIN
    -- Update conversation state to HUMAN_HANDLING
    UPDATE public.conversations
    SET status = 'HUMAN_HANDLING',
        handover_reason = p_reason,
        assigned_user_id = p_user_id,
        assigned_user_name = p_user_name,
        updated_at = NOW()
    WHERE id = p_conversation_id
    RETURNING * INTO v_updated_conv;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Conversation not found');
    END IF;

    -- Insert system audit message
    v_msg_id := 'MSG_' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 8));
    INSERT INTO public.conversation_messages (
        id,
        conversation_id,
        sender_type,
        sender_name,
        message_text,
        timestamp,
        delivery_status
    ) VALUES (
        v_msg_id,
        p_conversation_id,
        'SYSTEM',
        'System Escalation',
        '🚨 Handover triggered: Reason [' || p_reason || ']. Assigned to ' || p_user_name || '.',
        NOW(),
        'DELIVERED'
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

-- 5. Row Level Security Policies
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow anon all on conversations" ON public.conversations;
CREATE POLICY "Allow anon all on conversations" ON public.conversations 
    FOR ALL TO anon, authenticated 
    USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon all on conversation_messages" ON public.conversation_messages;
CREATE POLICY "Allow anon all on conversation_messages" ON public.conversation_messages 
    FOR ALL TO anon, authenticated 
    USING (true) WITH CHECK (true);

-- 6. Table Permissions
GRANT ALL ON TABLE public.conversations TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.conversation_messages TO anon, authenticated, service_role;

-- 7. Verification Check
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name = 'conversations'
    ) AND EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_name = 'conversation_messages'
    ) THEN
        RAISE NOTICE 'SUCCESS: Phase 7 conversations tables exist with RLS and handover function.';
    ELSE
        RAISE EXCEPTION 'ERROR: Conversations tables were not created.';
    END IF;
END $$;

-- 8. Reload schema cache for PostgREST
NOTIFY pgrst, 'reload schema';
