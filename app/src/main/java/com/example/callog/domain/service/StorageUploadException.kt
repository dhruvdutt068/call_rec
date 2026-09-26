package com.example.callog.domain.service

enum class StorageErrorCategory(val isRetryable: Boolean, val description: String) {
    PERMISSION_DENIED(isRetryable = false, description = "Permission denied (HTTP 403)"),
    AUTHENTICATION_REQUIRED(isRetryable = false, description = "Authentication required (HTTP 401)"),
    BUCKET_OR_OBJECT_NOT_FOUND(isRetryable = false, description = "Bucket or object not found (HTTP 404)"),
    RETRYABLE_NETWORK_ERROR(isRetryable = true, description = "Retryable network error"),
    RETRYABLE_SERVER_ERROR(isRetryable = true, description = "Retryable server error (HTTP 5xx)"),
    UNKNOWN_ERROR(isRetryable = false, description = "Non-retryable unknown error");

    companion object {
        fun fromException(e: Throwable): StorageErrorCategory {
            if (e is StorageUploadException) return e.category

            if (e is com.google.firebase.storage.StorageException) {
                val byHttp = when (e.httpResultCode) {
                    403 -> PERMISSION_DENIED
                    401 -> AUTHENTICATION_REQUIRED
                    404 -> BUCKET_OR_OBJECT_NOT_FOUND
                    in 500..599 -> RETRYABLE_SERVER_ERROR
                    else -> null
                }
                if (byHttp != null) return byHttp

                val byCode = when (e.errorCode) {
                    com.google.firebase.storage.StorageException.ERROR_NOT_AUTHENTICATED -> AUTHENTICATION_REQUIRED
                    com.google.firebase.storage.StorageException.ERROR_NOT_AUTHORIZED -> PERMISSION_DENIED
                    com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND,
                    com.google.firebase.storage.StorageException.ERROR_BUCKET_NOT_FOUND,
                    com.google.firebase.storage.StorageException.ERROR_PROJECT_NOT_FOUND -> BUCKET_OR_OBJECT_NOT_FOUND
                    com.google.firebase.storage.StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> RETRYABLE_NETWORK_ERROR
                    else -> null
                }
                if (byCode != null) return byCode
            }

            if (isNetworkThrowable(e)) {
                return RETRYABLE_NETWORK_ERROR
            }

            val msg = e.message?.lowercase() ?: ""
            return when {
                msg.contains("permission denied") || msg.contains("403") || msg.contains("not authorized") -> PERMISSION_DENIED
                msg.contains("not authenticated") || msg.contains("401") || msg.contains("unauthenticated") -> AUTHENTICATION_REQUIRED
                msg.contains("404") || msg.contains("not found") -> BUCKET_OR_OBJECT_NOT_FOUND
                msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("server error") -> RETRYABLE_SERVER_ERROR
                msg.contains("network") || msg.contains("timeout") || msg.contains("connect") || msg.contains("socket") -> RETRYABLE_NETWORK_ERROR
                else -> UNKNOWN_ERROR
            }
        }

        fun isNetworkThrowable(t: Throwable): Boolean {
            var curr: Throwable? = t
            while (curr != null) {
                if (curr is java.net.SocketTimeoutException ||
                    curr is java.net.UnknownHostException ||
                    curr is java.net.ConnectException ||
                    curr is java.io.InterruptedIOException) {
                    return true
                }
                curr = curr.cause
            }
            return false
        }
    }
}

class StorageUploadException(
    val category: StorageErrorCategory,
    val errorCode: Int? = null,
    val httpResultCode: Int? = null,
    val uploadPath: String? = null,
    val bucketName: String? = null,
    val hasFirebaseAuth: Boolean = false,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    val isRetryable: Boolean get() = category.isRetryable
}
