package com.example.callog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.callog.domain.model.Conversation
import com.example.callog.domain.model.ConversationStatus
import com.example.callog.domain.model.HandoverReason

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["personId"], unique = true),
        Index(value = ["whatsappNumber"]),
        Index(value = ["status"]),
        Index(value = ["assignedUserId"]),
        Index(value = ["updatedAt"])
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val whatsappNumber: String,
    val status: String = "AI_HANDLING",
    val assignedUserId: String? = null,
    val assignedUserName: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: Long = System.currentTimeMillis(),
    val handoverReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDING"
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        personId = personId,
        whatsappNumber = whatsappNumber,
        status = ConversationStatus.fromString(status),
        assignedUserId = assignedUserId,
        assignedUserName = assignedUserName,
        lastMessage = lastMessage,
        lastMessageAt = lastMessageAt,
        handoverReason = handoverReason?.let { HandoverReason.fromString(it) },
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus
    )

    companion object {
        fun fromDomain(conversation: Conversation): ConversationEntity = ConversationEntity(
            id = conversation.id,
            personId = conversation.personId,
            whatsappNumber = conversation.whatsappNumber,
            status = conversation.status.name,
            assignedUserId = conversation.assignedUserId,
            assignedUserName = conversation.assignedUserName,
            lastMessage = conversation.lastMessage,
            lastMessageAt = conversation.lastMessageAt,
            handoverReason = conversation.handoverReason?.name,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            syncStatus = conversation.syncStatus
        )
    }
}
