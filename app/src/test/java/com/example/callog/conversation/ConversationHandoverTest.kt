package com.example.callog.conversation

import com.example.callog.data.local.dao.ConversationDao
import com.example.callog.data.local.entity.ConversationEntity
import com.example.callog.data.local.entity.ConversationMessageEntity
import com.example.callog.data.repository.ConversationRepositoryImpl
import com.example.callog.domain.model.ConversationStatus
import com.example.callog.domain.model.HandoverReason
import com.example.callog.domain.model.SenderType
import com.example.callog.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConversationHandoverTest {

    private lateinit var fakeConversationDao: FakeConversationDao
    private lateinit var conversationRepository: ConversationRepository

    @Before
    fun setUp() {
        fakeConversationDao = FakeConversationDao()
        conversationRepository = ConversationRepositoryImpl(fakeConversationDao)
    }

    @Test
    fun testInitializeConversation_CreatesAiActiveState() = runBlocking {
        val personId = "PERSON_1001"
        val phone = "+919876543210"

        val conv = conversationRepository.createOrGetConversation(personId, phone)

        assertNotNull(conv)
        assertEquals(personId, conv.personId)
        assertEquals(phone, conv.whatsappNumber)
        assertEquals(ConversationStatus.AI_HANDLING, conv.status)
        assertEquals(1, fakeConversationDao.conversations.size)
        // Initial AI greeting created
        assertEquals(1, fakeConversationDao.messages.size)
        assertEquals(SenderType.AI, fakeConversationDao.messages.first().toDomain().senderType)
    }

    @Test
    fun testHandoverToHuman_UpdatesStateAndAddsEscalationMessage() = runBlocking {
        val personId = "PERSON_1002"
        val conv = conversationRepository.createOrGetConversation(personId, "+919999888877")

        val handoverSuccess = conversationRepository.handoverToHuman(
            conversationId = conv.id,
            reason = HandoverReason.COMPLEX_QUERY,
            assignedUserId = "USER_VIKRAM",
            assignedUserName = "Vikram Malhotra"
        )

        assertTrue(handoverSuccess)
        val updated = conversationRepository.getConversationById(conv.id)
        assertNotNull(updated)
        assertEquals(ConversationStatus.HUMAN_HANDLING, updated?.status)
        assertEquals(HandoverReason.COMPLEX_QUERY, updated?.handoverReason)
        assertEquals("USER_VIKRAM", updated?.assignedUserId)
        assertEquals("Vikram Malhotra", updated?.assignedUserName)

        // System escalation audit message inserted
        val messages = fakeConversationDao.messages.filter { it.conversationId == conv.id }
        assertTrue(messages.any { it.senderType == "SYSTEM" && it.messageText.contains("🚨 Handover triggered") })
    }

    @Test
    fun testSendHumanMessage_UpdatesConversationTimeline() = runBlocking {
        val personId = "PERSON_1003"
        val conv = conversationRepository.createOrGetConversation(personId, "+919123456780")
        conversationRepository.handoverToHuman(conv.id, HandoverReason.USER_REQUESTED, "USER_REP", "Ananya Roy")

        val sentMsg = conversationRepository.sendHumanMessage(
            conversationId = conv.id,
            text = "Hi Rahul, I am Ananya from sales. Let's discuss your enterprise discount.",
            senderName = "Ananya Roy"
        )

        assertNotNull(sentMsg)
        assertEquals(SenderType.HUMAN, sentMsg.senderType)
        assertEquals("Ananya Roy", sentMsg.senderName)
        assertEquals("Hi Rahul, I am Ananya from sales. Let's discuss your enterprise discount.", sentMsg.messageText)

        val updatedConv = conversationRepository.getConversationById(conv.id)
        assertEquals("Hi Rahul, I am Ananya from sales. Let's discuss your enterprise discount.", updatedConv?.lastMessage)
    }

    @Test
    fun testReleaseToAi_RestoresAiActiveState() = runBlocking {
        val personId = "PERSON_1004"
        val conv = conversationRepository.createOrGetConversation(personId, "+919555666777")
        conversationRepository.handoverToHuman(conv.id, HandoverReason.MANUAL, "USER_REP", "Test Rep")

        val releaseSuccess = conversationRepository.releaseToAi(conv.id)
        assertTrue(releaseSuccess)

        val restoredConv = conversationRepository.getConversationById(conv.id)
        assertEquals(ConversationStatus.AI_HANDLING, restoredConv?.status)
        assertNull(restoredConv?.handoverReason)

        val messages = fakeConversationDao.messages.filter { it.conversationId == conv.id }
        assertTrue(messages.any { it.senderType == "SYSTEM" && it.messageText.contains("Returned control to AllSet AI Agent") })
    }
}

class FakeConversationDao : ConversationDao {
    val conversations = mutableListOf<ConversationEntity>()
    val messages = mutableListOf<ConversationMessageEntity>()

    override suspend fun insertConversation(conversation: ConversationEntity) {
        conversations.removeIf { it.id == conversation.id || it.personId == conversation.personId }
        conversations.add(conversation)
    }

    override suspend fun updateConversation(conversation: ConversationEntity) {
        insertConversation(conversation)
    }

    override suspend fun getConversationById(id: String): ConversationEntity? {
        return conversations.find { it.id == id }
    }

    override fun getConversationByPersonIdFlow(personId: String): Flow<ConversationEntity?> {
        return flowOf(conversations.find { it.personId == personId })
    }

    override suspend fun getConversationByPersonId(personId: String): ConversationEntity? {
        return conversations.find { it.personId == personId }
    }

    override fun getAllConversationsFlow(): Flow<List<ConversationEntity>> {
        return flowOf(conversations.toList())
    }

    override suspend fun getPendingConversations(): List<ConversationEntity> {
        return conversations.filter { it.syncStatus != "SYNCED" }
    }

    override suspend fun handoverToHuman(
        id: String,
        reason: String,
        assignedUserId: String?,
        assignedUserName: String?,
        updatedAt: Long
    ) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                status = "HUMAN_HANDLING",
                handoverReason = reason,
                assignedUserId = assignedUserId,
                assignedUserName = assignedUserName,
                updatedAt = updatedAt,
                syncStatus = "PENDING"
            )
        }
    }

    override suspend fun releaseToAi(id: String, updatedAt: Long) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                status = "AI_HANDLING",
                handoverReason = null,
                updatedAt = updatedAt,
                syncStatus = "PENDING"
            )
        }
    }

    override suspend fun assignUser(
        id: String,
        assignedUserId: String,
        assignedUserName: String,
        updatedAt: Long
    ) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                assignedUserId = assignedUserId,
                assignedUserName = assignedUserName,
                updatedAt = updatedAt,
                syncStatus = "PENDING"
            )
        }
    }

    override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(status = status, updatedAt = updatedAt, syncStatus = "PENDING")
        }
    }

    override suspend fun updateLastMessage(
        id: String,
        lastMessage: String,
        lastMessageAt: Long,
        updatedAt: Long
    ) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = conversations[index]
            conversations[index] = old.copy(
                lastMessage = lastMessage,
                lastMessageAt = lastMessageAt,
                updatedAt = updatedAt,
                syncStatus = "PENDING"
            )
        }
    }

    override suspend fun insertMessage(message: ConversationMessageEntity) {
        messages.removeIf { it.id == message.id }
        messages.add(message)
    }

    override suspend fun insertMessages(messages: List<ConversationMessageEntity>) {
        messages.forEach { insertMessage(it) }
    }

    override fun getMessagesFlow(conversationId: String): Flow<List<ConversationMessageEntity>> {
        return flowOf(messages.filter { it.conversationId == conversationId })
    }

    override suspend fun getMessages(conversationId: String): List<ConversationMessageEntity> {
        return messages.filter { it.conversationId == conversationId }
    }

    override suspend fun getPendingMessages(): List<ConversationMessageEntity> {
        return messages.filter { it.syncStatus != "SYNCED" }
    }
}
