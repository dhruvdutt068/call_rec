package com.example.callog.data.security

import android.content.Context
import android.util.Log
import com.example.callog.core.diagnostics.DeveloperLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Utility functions for cryptographic hashing and atomic file operations on call recordings.
 */
object FileCryptoUtils {

    private const val TAG = "FileCryptoUtils"

    /**
     * Calculates the SHA-256 cryptographic checksum of a file.
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Copies a source recording file to app-private storage safely using an atomic temporary file.
     *
     * @param context Application context
     * @param sourceFile Source audio file discovered from MediaStore or OEM directory
     * @param destinationName Target file name in app-private storage
     * @return Result containing the final verified File and its SHA-256 checksum
     */
    fun copyToAppPrivateStorage(
        context: Context,
        sourceFile: File,
        destinationName: String
    ): Result<Pair<File, String>> {
        return try {
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                return Result.failure(IllegalArgumentException("Source file does not exist or cannot be read: ${sourceFile.absolutePath}"))
            }

            val targetDir = context.getExternalFilesDir("recordings")
                ?: File(context.filesDir, "recordings")

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val finalFile = File(targetDir, destinationName)
            val tempFile = File(targetDir, "${destinationName}.tmp_${System.currentTimeMillis()}")

            // Copy to temp file first
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Verify size of temp file
            if (tempFile.length() != sourceFile.length()) {
                tempFile.delete()
                return Result.failure(IllegalStateException("File size mismatch during copy: source=${sourceFile.length()}, copied=${tempFile.length()}"))
            }

            // Calculate checksum
            val checksum = calculateSha256(tempFile)

            // Atomic rename
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                // Fallback copy if rename fails across mount points
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            DeveloperLogger.info(
                "FILE_CRYPTO",
                "Copied recording to ${finalFile.absolutePath} (size=${finalFile.length()}, sha256=${checksum.take(12)}...)"
            )

            Result.success(Pair(finalFile, checksum))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to securely copy recording file", e)
            DeveloperLogger.error("FILE_CRYPTO", "Failed to copy recording: ${e.message}")
            Result.failure(e)
        }
    }
}
