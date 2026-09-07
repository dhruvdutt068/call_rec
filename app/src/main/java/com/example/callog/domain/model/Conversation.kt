package com.example.callog.domain.model

enum class ConversationStatus {
    AI_HANDLING,
    HUMAN_HANDLING,
    RESOLVED;

    companion object {
        fun fromString(value: String?): ConversationStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: AI_HANDLING
        }
    }
}

enum class HandoverReason {
    USER_REQUESTED,
    LOW_CONFIDENCE,
    SENTIMENT_ALERT,
    VIP_CUSTOMER,
    COMPLEX_QUERY,
    MANUAL;

    companion object {
        fun fromString(value: String?): HandoverReason {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: MANUAL
        }
    }
}

enum class SenderType {
    CUSTOMER,
    AI,
    HUMAN,
    SYSTEM;

    companion object {
        fun fromString(value: String?): SenderType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: CUSTOMER
        }
    }
}

enum class MessageDeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED;

    companion object {
        fun fromString(value: String?): MessageDeliveryStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: SENT
        }
    }
}

data class Conversation(
    val id: String,
    val personId: String,
    val whatsappNumber: String,
    val status: ConversationStatus = ConversationStatus.AI_HANDLING,
    val assignedUserId: String? = null,
    val assignedUserName: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: Long = System.currentTimeMillis(),
    val handoverReason: HandoverReason? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
)

data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val senderType: SenderType,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val syncStatus: String = "PENDING"
)
