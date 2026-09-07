package com.example.callog.data.repository

import com.example.callog.data.local.dao.ConversationDao
import com.example.callog.data.local.entity.ConversationEntity
import com.example.callog.data.local.entity.ConversationMessageEntity
import com.example.callog.domain.model.*
import com.example.callog.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao
) : ConversationRepository {

    override fun getAllConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversationsFlow().map { list -> list.map { it.toDomain() } }
    }

    override fun getConversationByPersonIdFlow(personId: String): Flow<Conversation?> {
        return conversationDao.getConversationByPersonIdFlow(personId).map { it?.toDomain() }
    }

    override suspend fun getConversationById(id: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(id)?.toDomain()
    }

    override suspend fun getConversationByPersonId(personId: String): Conversation? = withContext(Dispatchers.IO) {
        conversationDao.getConversationByPersonId(personId)?.toDomain()
    }

    override fun getMessagesFlow(conversationId: String): Flow<List<ConversationMessage>> {
        return conversationDao.getMessagesFlow(conversationId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun createOrGetConversation(personId: String, whatsappNumber: String): Conversation = withContext(Dispatchers.IO) {
        val existing = conversationDao.getConversationByPersonId(personId)
        if (existing != null) {
            return@withContext existing.toDomain()
        }

        val newConversation = Conversation(
            id = "CONV_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            personId = personId,
            whatsappNumber = whatsappNumber,
            status = ConversationStatus.AI_HANDLING,
            lastMessage = "Conversation initialized with AI Agent",
            lastMessageAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )
        conversationDao.insertConversation(ConversationEntity.fromDomain(newConversation))

        // Create initial system/greeting message
        val initialMsg = ConversationMessage(
            id = "MSG_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            conversationId = newConversation.id,
            senderType = SenderType.AI,
            senderName = "AllSet AI Assistant",
            messageText = "Hello! I am your AllSet AI Assistant. How can I help you today?",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            syncStatus = "PENDING"
        )
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(initialMsg))

        newConversation
    }

    override suspend fun handoverToHuman(
        conversationId: String,
        reason: HandoverReason,
        assignedUserId: String?,
        assignedUserName: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId) ?: return@withContext false
        val repName = assignedUserName ?: "Sales Rep"
        
        conversationDao.handoverToHuman(
            id = conversationId,
            reason = reason.name,
            assignedUserId = assignedUserId ?: "USER_CURRENT",
            assignedUserName = repName,
            updatedAt = System.currentTimeMillis()
        )

        // Add escalation audit message to conversation timeline
        val escalationMsg = ConversationMessage(
            id = "MSG_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            conversationId = conversationId,
            senderType = SenderType.SYSTEM,
            senderName = "System Escalation",
            messageText = "🚨 Handover triggered: Reason [${reason.name}]. Assigned to $repName. AI responses paused.",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            syncStatus = "PENDING"
        )
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(escalationMsg))
        conversationDao.updateLastMessage(conversationId, escalationMsg.messageText)
        true
    }

    override suspend fun assignToUser(
        conversationId: String,
        userId: String,
        userName: String
    ): Boolean = withContext(Dispatchers.IO) {
        conversationDao.assignUser(conversationId, userId, userName)
        
        val assignmentMsg = ConversationMessage(
            id = "MSG_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            conversationId = conversationId,
            senderType = SenderType.SYSTEM,
            senderName = "System",
            messageText = "👤 Conversation assigned to $userName.",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            syncStatus = "PENDING"
        )
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(assignmentMsg))
        true
    }

    override suspend fun releaseToAi(conversationId: String): Boolean = withContext(Dispatchers.IO) {
        conversationDao.releaseToAi(conversationId)
        
        val releaseMsg = ConversationMessage(
            id = "MSG_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            conversationId = conversationId,
            senderType = SenderType.SYSTEM,
            senderName = "System",
            messageText = "🤖 Human handling completed. Returned control to AllSet AI Agent.",
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            syncStatus = "PENDING"
        )
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(releaseMsg))
        conversationDao.updateLastMessage(conversationId, releaseMsg.messageText)
        true
    }

    override suspend fun updateStatus(
        conversationId: String,
        status: ConversationStatus
    ): Boolean = withContext(Dispatchers.IO) {
        conversationDao.updateStatus(conversationId, status.name)
        true
    }

    override suspend fun sendHumanMessage(
        conversationId: String,
        text: String,
        senderName: String
    ): ConversationMessage = withContext(Dispatchers.IO) {
        val newMsg = ConversationMessage(
            id = "MSG_" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase(),
            conversationId = conversationId,
            senderType = SenderType.HUMAN,
            senderName = senderName,
            messageText = text,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = MessageDeliveryStatus.SENT,
            syncStatus = "PENDING"
        )
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(newMsg))
        conversationDao.updateLastMessage(conversationId, text)
        newMsg
    }

    override suspend fun insertMessage(message: ConversationMessage): Boolean = withContext(Dispatchers.IO) {
        conversationDao.insertMessage(ConversationMessageEntity.fromDomain(message))
        conversationDao.updateLastMessage(message.conversationId, message.messageText)
        true
    }
}
