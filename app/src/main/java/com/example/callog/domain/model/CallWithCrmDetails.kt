package com.example.callog.domain.model

import com.example.callog.data.local.entity.SalesCallEntity

/**
 * Composite domain model linking telephony call details with CRM context:
 * - [call]: Base call log entry with recording path, notes, tags, and telephony metadata.
 * - [person]: Canonical CRM Person record (company name, canonical name, multi-device phone numbers/aliases).
 * - [lead]: Current lead pipeline stage, priority, next follow-up date, and CRM feedback rating.
 * - [salesCall]: Sales interaction outcome and salesperson-buyer logging details.
 * - [totalCallCountWithContact]: Lifetime total calls exchanged with this contact.
 * - [totalDurationWithContact]: Cumulative duration (seconds) spent on calls with this contact.
 */
data class CallWithCrmDetails(
    val call: CallLogEntry,
    val person: Person? = null,
    val lead: Lead? = null,
    val salesCall: SalesCallEntity? = null,
    val totalCallCountWithContact: Int = 0,
    val totalDurationWithContact: Int = 0
)
