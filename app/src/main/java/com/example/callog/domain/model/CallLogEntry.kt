package com.example.callog.domain.model

data class CallLogEntry(
    val id: Long,
    val name: String?,
    val number: String,
    val duration: Int,
    val timestamp: Long,
    val callType: String, // INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED
    val recordingPath: String?,
    val isFavorite: Boolean,
    val notes: String?,
    val tags: List<String>,
    
    // Matched contact info
    val contactPhotoUri: String?,
    val contactEmails: List<String> = emptyList(),
    val contactAllNumbers: List<String> = emptyList(),
    val isContactFavorite: Boolean = false,
    val syncStatus: String = "PENDING",
    val recordingLocalPath: String? = null,
    val recordingCloudPath: String? = null,
    val recordingUploadStatus: String = "PENDING",
    val recordingUploadedAt: Long? = null,
    val retryCount: Int = 0,
    val uploadedAt: Long? = null,
    val syncError: String? = null,
    val lastAttempt: Long? = null
) {
    // Helper to get initials or placeholder name
    val displayName: String
        get() = name ?: number

    val initials: String
        get() {
            val cleanName = name ?: return "#"
            val parts = cleanName.trim().split("\\s+".toRegex())
            return if (parts.size >= 2) {
                "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            } else {
                cleanName.take(1).uppercase()
            }
        }
}
