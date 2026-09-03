package com.example.callog.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.SalesCallEntity
import com.example.callog.core.utils.ConnectivityService
import com.example.callog.data.local.dao.SalesCallDao
import com.example.callog.data.local.dao.SyncLogDao
import com.example.callog.core.diagnostics.DeveloperLogger
import com.example.callog.data.remote.FirestoreService
import com.example.callog.domain.repository.CallRepository
import com.example.callog.domain.repository.RecordingRepository
import com.example.callog.domain.repository.SyncRepository
import com.example.callog.domain.service.UploadProgressState
import com.example.callog.domain.service.UploadService
import com.example.callog.data.remote.SupabaseService
import com.example.callog.data.repository.FirestoreRepositoryImpl
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collect

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "SyncWorker"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun syncRepository(): SyncRepository
        fun uploadService(): UploadService
        fun callRepository(): CallRepository
        fun recordingRepository(): RecordingRepository
        fun connectivityService(): ConnectivityService
        fun salesCallDao(): SalesCallDao
        fun syncLogDao(): SyncLogDao
        fun firestoreService(): FirestoreService
        fun supabaseService(): SupabaseService
        fun simManager(): com.example.callog.data.provider.SimManager
        fun personRepository(): com.example.callog.domain.repository.PersonRepository
        fun personDao(): com.example.callog.data.local.dao.PersonDao
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing background SyncWorker")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )

        val syncRepository = entryPoint.syncRepository()
        val uploadService = entryPoint.uploadService()
        val callRepository = entryPoint.callRepository()
        val recordingRepository = entryPoint.recordingRepository()
        val connectivityService = entryPoint.connectivityService()
        val salesCallDao = entryPoint.salesCallDao()
        val syncLogDao = entryPoint.syncLogDao()
        val firestoreService = entryPoint.firestoreService()
        val supabaseService = entryPoint.supabaseService()
        val simManager = entryPoint.simManager()
        val personRepository = entryPoint.personRepository()
        val personDao = entryPoint.personDao()

        // Start new logger session
        val syncId = DeveloperLogger.startNewSession()
        val network = if (connectivityService.isConnected()) {
            "ONLINE"
        } else {
            "OFFLINE"
        }

        // Abort background sync immediately if SIM card configuration mismatch is detected
        if (simManager.isSyncSuspendedDueToSimChange()) {
            DeveloperLogger.warning("SYNC_ABORTED", "Background sync aborted: SIM card configuration mismatch. Reconfiguration required.", network = network)
            return Result.success()
        }

        DeveloperLogger.info("SYNC_STARTED", "Background sync run started (Trigger: Auto, Network: $network)", network = network)

        // Step 1: Ensure we are online before initiating sync
        if (!connectivityService.isConnected()) {
            Log.w(TAG, "Device is offline. Suspending sync run.")
            DeveloperLogger.warning("SYNC_STARTED", "Device is offline. Suspending sync run.", network = network)
            return Result.retry()
        }

        try {
            // Step 2: Fetch and import call logs from provider, match files, etc.
            DeveloperLogger.info("READ_PENDING_CALLS", "Scanning system logs & local recordings to refresh database", network = network)
            callRepository.syncCallLogs()

            // Step 2b: Populate sales_calls table from refreshed call log
            val salespersonPhoneRaw = firestoreService.getDevicePhoneNumber()
            val salespersonName  = firestoreService.getDeviceOwnerName()
            if (salespersonPhoneRaw.isNotEmpty()) {
                val salespersonPhone = FirestoreRepositoryImpl.normalizePhoneNumber(salespersonPhoneRaw)
                val allCalls = callRepository.getAllCallsSnapshot()
                DeveloperLogger.info("SALES_CALL_CREATION_STARTED", "Generating SalesCallEntity cache. Found ${allCalls.size} calls.", network = network)
                var createdCount = 0
                for (call in allCalls) {
                    val existing = salesCallDao.getSalesCallByCallId(call.id)
                    if (existing == null) {
                        salesCallDao.insertSalesCall(
                            SalesCallEntity(
                                salespersonPhone = salespersonPhone,
                                salespersonName  = salespersonName,
                                buyerPhone       = call.number,
                                buyerName        = call.name,
                                callType         = call.callType,
                                callId           = call.id,
                                duration         = call.duration,
                                personId         = call.personId,
                                createdAt        = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(call.timestamp))
                            )
                        )
                        createdCount++
                    }
                }
                if (createdCount > 0) {
                    DeveloperLogger.success("SALES_CALL_CREATED", "Populated $createdCount new sales call logs locally.", network = network)
                }
            } else {
                DeveloperLogger.warning("SALES_CALL_CREATION_STARTED", "Device configurations not set, skipping sales calls population.", network = network)
            }

            // Step 2c: Sync pending sales_calls to Supabase
            try {
                val pendingSalesCalls = salesCallDao.getPendingSalesCalls()
                if (pendingSalesCalls.isNotEmpty()) {
                    DeveloperLogger.info("SUPABASE_UPLOAD_STARTED", "Syncing ${pendingSalesCalls.size} pending sales calls to Supabase.", network = network)
                    val startTime = System.currentTimeMillis()
                    val syncResult = supabaseService.syncSalesCalls(pendingSalesCalls)
                    val duration = System.currentTimeMillis() - startTime
                    if (syncResult.isSuccess) {
                        for (salesCall in pendingSalesCalls) {
                            salesCallDao.updateSyncStatus(salesCall.id, "SYNCED", null)
                        }
                        DeveloperLogger.success("SUPABASE_UPLOAD_SUCCESS", "Successfully synced ${pendingSalesCalls.size} rows to Supabase table sales_calls.", durationMs = duration, network = network)
                    } else {
                        val exception = syncResult.exceptionOrNull()
                        val errorMsg = exception?.message ?: "Unknown sync error"
                        for (salesCall in pendingSalesCalls) {
                            salesCallDao.updateSyncStatus(salesCall.id, "FAILED", errorMsg)
                        }
                        DeveloperLogger.error("SUPABASE_UPLOAD_FAILED", "Supabase sync failed: $errorMsg", exception = exception, network = network)
                    }
                }
            } catch (e: Exception) {
                DeveloperLogger.error("SUPABASE_UPLOAD_FAILED", "Exception occurred during Supabase sync execution", exception = e, network = network)
            }

            // Step 2d: Resolve local Contacts to canonical Person and sync to Supabase
            try {
                DeveloperLogger.info("IDENTITY_SYNC_STARTED", "Resolving contacts to Person canonical identity and syncing to Supabase", network = network)
                personRepository.syncContactsFromDevice()

                val pendingPeople = personDao.getPendingPeople()
                if (pendingPeople.isNotEmpty()) {
                    val res = supabaseService.syncPeople(pendingPeople)
                    if (res.isSuccess) {
                        for (p in pendingPeople) {
                            personDao.updatePersonSyncStatus(p.id, "SYNCED")
                        }
                        DeveloperLogger.success("IDENTITY_PEOPLE_SYNCED", "Synced ${pendingPeople.size} people to Supabase.", network = network)
                    }
                }

                val pendingPhoneNumbers = personDao.getPendingPhoneNumbers()
                if (pendingPhoneNumbers.isNotEmpty()) {
                    val res = supabaseService.syncPhoneNumbers(pendingPhoneNumbers)
                    if (res.isSuccess) {
                        for (pn in pendingPhoneNumbers) {
                            personDao.updatePhoneNumberSyncStatus(pn.id, "SYNCED")
                        }
                        DeveloperLogger.success("IDENTITY_PHONES_SYNCED", "Synced ${pendingPhoneNumbers.size} phone numbers to Supabase.", network = network)
                    }
                }

                val pendingAliases = personDao.getPendingAliases()
                if (pendingAliases.isNotEmpty()) {
                    val res = supabaseService.syncContactAliases(pendingAliases)
                    if (res.isSuccess) {
                        for (ca in pendingAliases) {
                            personDao.updateAliasSyncStatus(ca.id, "SYNCED")
                        }
                        DeveloperLogger.success("IDENTITY_ALIASES_SYNCED", "Synced ${pendingAliases.size} aliases to Supabase.", network = network)
                    }
                }

                val devices = personDao.getAllDevices()
                if (devices.isNotEmpty()) {
                    supabaseService.syncDevices(devices)
                }
            } catch (e: Exception) {
                DeveloperLogger.error("IDENTITY_SYNC_FAILED", "Exception during Person identity sync: ${e.message}", exception = e, network = network)
            }

            // Step 3: Fetch all pending items from local database for Firestore
            val pendingCalls = syncRepository.getPendingCalls()
            if (pendingCalls.isEmpty()) {
                DeveloperLogger.success("SYNC_COMPLETED", "No pending Firestore logs found. Sync completed successfully.", network = network)
                runLogsMaintenance(syncLogDao)
                return Result.success()
            }

            DeveloperLogger.info("FIRESTORE_METADATA_UPLOAD_STARTED", "Found ${pendingCalls.size} pending call logs to sync to Firestore.", network = network)
            val currentTime = System.currentTimeMillis()

            var successCount = 0
            var failCount = 0
            val startTime = System.currentTimeMillis()

            for (call in pendingCalls) {
                if (!connectivityService.isConnected()) {
                    DeveloperLogger.warning("SYNC_FAILED", "Network connection lost during sync. Suspending loop.", network = network)
                    return Result.retry()
                }

                if (call.retryCount >= 5) {
                    DeveloperLogger.warning("FIRESTORE_METADATA_UPLOAD_FAILED", "Call ID: ${call.id} exceeded max retries. Skipping.", network = network)
                    continue
                }

                if (call.syncStatus == "FAILED" && call.lastAttempt != null) {
                    val backoffDuration = getBackoffDuration(call.retryCount)
                    if (currentTime < call.lastAttempt + backoffDuration) {
                        continue
                    }
                }

                syncRepository.markUploading(call.id)

                var recordingUrl: String? = null
                var cloudPath: String? = null
                var uploadFailed = false

                // Step 4: Upload recording to Firebase Storage if present
                if (call.recordingPath != null && call.recordingUploadStatus != "SUCCESS") {
                    var uploadResult: com.example.callog.domain.service.UploadProgressState? = null
                    
                    try {
                        DeveloperLogger.info("RECORDING_UPLOAD_STARTED", "Uploading audio recording file for Call ID: ${call.id} (${call.recordingPath})", network = network)
                        val recStartTime = System.currentTimeMillis()
                        uploadService.uploadRecording(call).collect { state ->
                            uploadResult = state
                            if (state is UploadProgressState.Progress) {
                                setProgress(workDataOf("progress" to "Uploading recording for ${call.id}: ${state.bytesTransferred}/${state.totalBytes}"))
                            }
                        }

                        val recDuration = System.currentTimeMillis() - recStartTime
                        when (val finalState = uploadResult) {
                            is UploadProgressState.Success -> {
                                recordingUrl = finalState.downloadUrl
                                cloudPath = finalState.cloudPath
                                DeveloperLogger.success("RECORDING_UPLOAD_SUCCESS", "Successfully uploaded recording. URL: $recordingUrl", durationMs = recDuration, network = network)
                            }
                            is UploadProgressState.Error -> {
                                val errMessage = finalState.exception.message ?: "Unknown upload error"
                                DeveloperLogger.error("RECORDING_UPLOAD_FAILED", "Recording upload failed for Call ID: ${call.id}: $errMessage", exception = finalState.exception, network = network)
                                syncRepository.markFailed(call.id, errMessage, call.retryCount + 1, currentTime)
                                recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.FAILED, reason = errMessage)
                                uploadFailed = true
                                failCount++
                            }
                            else -> {
                                DeveloperLogger.error("RECORDING_UPLOAD_FAILED", "Recording upload failed: Completed with empty state", network = network)
                                syncRepository.markFailed(call.id, "Completed with no result", call.retryCount + 1, currentTime)
                                recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.FAILED, reason = "Completed with no result")
                                uploadFailed = true
                                failCount++
                            }
                        }
                    } catch (e: Exception) {
                        DeveloperLogger.error("RECORDING_UPLOAD_FAILED", "Recording upload exception for Call ID: ${call.id}", exception = e, network = network)
                        syncRepository.markFailed(call.id, "Upload stream exception: ${e.message}", call.retryCount + 1, currentTime)
                        recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.FAILED, reason = e.message)
                        uploadFailed = true
                        failCount++
                    }
                }

                if (uploadFailed) continue

                // Step 5: Upload/Update metadata in Firestore
                try {
                    val metadataStartTime = System.currentTimeMillis()
                    val result = uploadService.uploadMetadata(call, recordingUrl)
                    val metadataDuration = System.currentTimeMillis() - metadataStartTime
                    if (result.isSuccess) {
                        syncRepository.markSynced(call.id, cloudPath, recordingUrl, currentTime)
                        recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.UPLOADED, recordingUrl)
                        DeveloperLogger.success("FIRESTORE_METADATA_UPLOAD_SUCCESS", "Successfully uploaded metadata to Firestore for Call ID: ${call.id}.", durationMs = metadataDuration, network = network)
                        successCount++
                    } else {
                        val exception = result.exceptionOrNull()
                        val errMsg = exception?.message ?: "Metadata upload failure"
                        DeveloperLogger.error("FIRESTORE_METADATA_UPLOAD_FAILED", "Failed uploading metadata for Call ID: ${call.id}: $errMsg", exception = exception, network = network)
                        syncRepository.markFailed(call.id, errMsg, call.retryCount + 1, currentTime)
                        recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.FAILED, reason = errMsg)
                        failCount++
                    }
                } catch (e: Exception) {
                    DeveloperLogger.error("FIRESTORE_METADATA_UPLOAD_FAILED", "Metadata upload exception for Call ID: ${call.id}", exception = e, network = network)
                    syncRepository.markFailed(call.id, "Metadata upload exception: ${e.message}", call.retryCount + 1, currentTime)
                    recordingRepository.updateRecordingUploadStatus(call.id, com.example.callog.data.local.entity.UploadStatus.FAILED, reason = e.message)
                    failCount++
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime
            DeveloperLogger.success("SYNC_COMPLETED", "Background sync completed. Uploaded: $successCount, Failed: $failCount, Duration: ${totalDuration}ms", durationMs = totalDuration, network = network)
            runLogsMaintenance(syncLogDao)
            return Result.success()

        } catch (e: Exception) {
            DeveloperLogger.error("SYNC_FAILED", "Sync worker execution crashed", exception = e, network = network)
            return Result.retry()
        }
    }

    private suspend fun runLogsMaintenance(syncLogDao: SyncLogDao) {
        try {
            val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
            syncLogDao.deleteLogsOlderThan(cutoff)
            syncLogDao.trimLogs(5000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed running log cleanup maintenance", e)
        }
    }

    private fun getBackoffDuration(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceIn(0, 4)
        val minutes = Math.pow(2.0, exponent.toDouble()).toLong()
        return minutes * 60 * 1000L
    }
}
