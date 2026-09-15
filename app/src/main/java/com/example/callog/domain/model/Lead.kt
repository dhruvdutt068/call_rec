package com.example.callog.domain.model

enum class LeadStatus {
    HOT,
    WARM,
    COLD,
    NEW,
    CUSTOMER,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): LeadStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class LeadPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    companion object {
        fun fromString(value: String?): LeadPriority {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class LeadSource {
    CONTACT,
    CALL,
    REFERRAL,
    WEBSITE,
    WHATSAPP,
    MANUAL,
    OTHER;

    companion object {
        fun fromString(value: String?): LeadSource {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: MANUAL
        }
    }
}

data class Lead(
    val id: String,
    val personId: String,
    val status: LeadStatus = LeadStatus.UNKNOWN,
    val priority: LeadPriority = LeadPriority.MEDIUM,
    val feedback: String? = null,
    val feedbackRating: Int? = null,
    val notes: String? = null,
    val ownerId: String? = null,
    val source: LeadSource = LeadSource.MANUAL,
    val nextFollowUpAt: Long? = null,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)
