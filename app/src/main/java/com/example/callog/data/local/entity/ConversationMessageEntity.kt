package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.domain.model.ConversationMessage
import com.example.callog.domain.model.MessageDeliveryStatus
import com.example.callog.domain.model.SenderType

@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
        Index(value = ["senderType"])
    ]
)
data class ConversationMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderType: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "SENT",
    val syncStatus: String = "PENDING"
) {
    fun toDomain(): ConversationMessage = ConversationMessage(
        id = id,
        conversationId = conversationId,
        senderType = SenderType.fromString(senderType),
        senderName = senderName,
        messageText = messageText,
        timestamp = timestamp,
        deliveryStatus = MessageDeliveryStatus.fromString(deliveryStatus),
        syncStatus = syncStatus
    )

    companion object {
        fun fromDomain(message: ConversationMessage): ConversationMessageEntity = ConversationMessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            senderType = message.senderType.name,
            senderName = message.senderName,
            messageText = message.messageText,
            timestamp = message.timestamp,
            deliveryStatus = message.deliveryStatus.name,
            syncStatus = message.syncStatus
        )
    }
}
