package com.example.callog.data.local.dao

import androidx.room.*
import com.example.callog.data.local.entity.ConversationEntity
import com.example.callog.data.local.entity.ConversationMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // ==========================================
    // CONVERSATIONS
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE personId = :personId LIMIT 1")
    fun getConversationByPersonIdFlow(personId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE personId = :personId LIMIT 1")
    suspend fun getConversationByPersonId(personId: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversationsFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingConversations(): List<ConversationEntity>

    @Query("""
        UPDATE conversations 
        SET status = 'HUMAN_HANDLING', 
            handoverReason = :reason, 
            assignedUserId = :assignedUserId, 
            assignedUserName = :assignedUserName, 
            updatedAt = :updatedAt, 
            syncStatus = 'PENDING' 
        WHERE id = :id
    """)
    suspend fun handoverToHuman(
        id: String,
        reason: String,
        assignedUserId: String?,
        assignedUserName: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE conversations 
        SET status = 'AI_HANDLING', 
            handoverReason = NULL, 
            updatedAt = :updatedAt, 
            syncStatus = 'PENDING' 
        WHERE id = :id
    """)
    suspend fun releaseToAi(
        id: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE conversations 
        SET assignedUserId = :assignedUserId, 
            assignedUserName = :assignedUserName, 
            updatedAt = :updatedAt, 
            syncStatus = 'PENDING' 
        WHERE id = :id
    """)
    suspend fun assignUser(
        id: String,
        assignedUserId: String,
        assignedUserName: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE conversations 
        SET status = :status, 
            updatedAt = :updatedAt, 
            syncStatus = 'PENDING' 
        WHERE id = :id
    """)
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE conversations 
        SET lastMessage = :lastMessage, 
            lastMessageAt = :lastMessageAt, 
            updatedAt = :updatedAt, 
            syncStatus = 'PENDING' 
        WHERE id = :id
    """)
    suspend fun updateLastMessage(
        id: String,
        lastMessage: String,
        lastMessageAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    // ==========================================
    // MESSAGES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ConversationMessageEntity>)

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesFlow(conversationId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessages(conversationId: String): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversation_messages WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMessages(): List<ConversationMessageEntity>
}
