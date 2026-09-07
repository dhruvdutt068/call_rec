package com.example.callog.domain.repository

import com.example.callog.domain.model.Conversation
import com.example.callog.domain.model.ConversationMessage
import com.example.callog.domain.model.ConversationStatus
import com.example.callog.domain.model.HandoverReason
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getAllConversationsFlow(): Flow<List<Conversation>>
    fun getConversationByPersonIdFlow(personId: String): Flow<Conversation?>
    suspend fun getConversationById(id: String): Conversation?
    suspend fun getConversationByPersonId(personId: String): Conversation?
    fun getMessagesFlow(conversationId: String): Flow<List<ConversationMessage>>
    
    suspend fun createOrGetConversation(personId: String, whatsappNumber: String): Conversation
    suspend fun handoverToHuman(conversationId: String, reason: HandoverReason, assignedUserId: String?, assignedUserName: String?): Boolean
    suspend fun assignToUser(conversationId: String, userId: String, userName: String): Boolean
    suspend fun releaseToAi(conversationId: String): Boolean
    suspend fun updateStatus(conversationId: String, status: ConversationStatus): Boolean
    suspend fun sendHumanMessage(conversationId: String, text: String, senderName: String): ConversationMessage
    suspend fun insertMessage(message: ConversationMessage): Boolean
}
