package com.example.callog.domain.model

import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState
import com.example.callog.sim.SimInfo

/**
 * Pre-configured simulation templates for 1-tap developer testing.
 */
enum class SimulationPreset(
    val title: String,
    val description: String,
    val direction: CallDirection,
    val defaultCallerName: String,
    val defaultPhoneNumber: String,
    val defaultCompanyName: String?,
    val defaultLeadStatus: LeadStatus,
    val defaultPriority: LeadPriority,
    val defaultAutoAnswerSec: Int,
    val defaultAutoHangupSec: Int
) {
    VIP_INCOMING(
        title = "VIP Client Inbound",
        description = "High-priority client call with VIP ringtone, CRM Hot Lead badge, and interactive answer/decline.",
        direction = CallDirection.INCOMING,
        defaultCallerName = "Sophia Patel (NexaCorp)",
        defaultPhoneNumber = "+1 (555) 349-8821",
        defaultCompanyName = "NexaCorp Global",
        defaultLeadStatus = LeadStatus.HOT,
        defaultPriority = LeadPriority.URGENT,
        defaultAutoAnswerSec = 0, // manual user interaction
        defaultAutoHangupSec = 0
    ),
    SALES_OUTBOUND(
        title = "Sales Prospect Outbound",
        description = "Outbound call to a potential enterprise lead with simulated connecting tone and remote answer.",
        direction = CallDirection.OUTGOING,
        defaultCallerName = "David Kim (Apex Retail)",
        defaultPhoneNumber = "+1 (555) 782-9014",
        defaultCompanyName = "Apex Retail Group",
        defaultLeadStatus = LeadStatus.WARM,
        defaultPriority = LeadPriority.HIGH,
        defaultAutoAnswerSec = 3, // remote answers after 3s
        defaultAutoHangupSec = 20
    ),
    NEW_UNKNOWN_LEAD(
        title = "Unsaved Inbound Lead",
        description = "Inbound call from an unsaved number to test first-time CRM contact capture and post-call onboarding.",
        direction = CallDirection.INCOMING,
        defaultCallerName = "Unknown Caller",
        defaultPhoneNumber = "+1 (555) 914-4320",
        defaultCompanyName = null,
        defaultLeadStatus = LeadStatus.UNKNOWN,
        defaultPriority = LeadPriority.MEDIUM,
        defaultAutoAnswerSec = 0,
        defaultAutoHangupSec = 0
    ),
    MISSED_CALL_TEST(
        title = "Missed Call Scenario",
        description = "Inbound call that rings for 4 seconds then terminates, generating missed call alerts and badges.",
        direction = CallDirection.INCOMING,
        defaultCallerName = "Marcus Vance (Logistics)",
        defaultPhoneNumber = "+1 (555) 438-1928",
        defaultCompanyName = "Vance Freight & Logistics",
        defaultLeadStatus = LeadStatus.COLD,
        defaultPriority = LeadPriority.LOW,
        defaultAutoAnswerSec = 0,
        defaultAutoHangupSec = 4 // rings for 4s then hangs up unanswered
    ),
    CALL_WAITING_DUAL(
        title = "Call Waiting / Dual Call",
        description = "Spawns an active call followed by a 2nd incoming call to test hold, swap, and multi-call line card.",
        direction = CallDirection.INCOMING,
        defaultCallerName = "Elena Rostova",
        defaultPhoneNumber = "+1 (555) 671-2900",
        defaultCompanyName = "Nordic Energy",
        defaultLeadStatus = LeadStatus.HOT,
        defaultPriority = LeadPriority.HIGH,
        defaultAutoAnswerSec = 0,
        defaultAutoHangupSec = 0
    )
}

/**
 * Configuration parameters used to generate a simulated incoming or outgoing call session.
 */
data class SimulatedCallConfig(
    val callId: String = "sim_${System.currentTimeMillis()}",
    val direction: CallDirection = CallDirection.INCOMING,
    val callerName: String = "Test Caller",
    val phoneNumber: String = "+1 (555) 000-1234",
    val companyName: String? = null,
    val crmStatus: LeadStatus = LeadStatus.HOT,
    val priority: LeadPriority = LeadPriority.HIGH,
    val personId: String? = null,
    val simSlot: Int = 0,
    val simDisplayName: String = "SIM 1",
    val autoAnswerDelaySec: Int = 0, // 0 = wait for user to click answer
    val autoHangupDurationSec: Int = 0, // 0 = wait for user to click hangup
    val recordToDatabase: Boolean = true,
    val preset: SimulationPreset? = null
)

/**
 * Real-time status of the simulation engine.
 */
data class SimulatedEngineState(
    val isRunning: Boolean = false,
    val activeConfig: SimulatedCallConfig? = null,
    val currentCallState: CallState = CallState.DISCONNECTED,
    val elapsedActiveSeconds: Int = 0,
    val isSecondaryCallActive: Boolean = false,
    val secondaryConfig: SimulatedCallConfig? = null,
    val lastCompletedCallSummary: String? = null
)
