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
        fun leadDao(): com.example.callog.data.local.dao.LeadDao
        fun crmRepository(): com.example.callog.domain.crm.repository.CrmRepository
        fun leadMasterDao(): com.example.callog.data.local.dao.LeadMasterDao
        fun leadStageDao(): com.example.callog.data.local.dao.LeadStageDao
        fun leadStatusDao(): com.example.callog.data.local.dao.LeadStatusDao
        fun callsLogDao(): com.example.callog.data.local.dao.CallsLogDao
        fun contactRingtoneSyncManager(): com.example.callog.data.provider.ContactRingtoneSyncManager
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
        val leadDao = entryPoint.leadDao()
        val crmRepository = entryPoint.crmRepository()
        val leadMasterDao = entryPoint.leadMasterDao()
        val leadStageDao = entryPoint.leadStageDao()
        val leadStatusDao = entryPoint.leadStatusDao()
        val callsLogDao = entryPoint.callsLogDao()
        val contactRingtoneSyncManager = entryPoint.contactRingtoneSyncManager()

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
        setProgress(workDataOf("progress" to "Scanning system logs & local recordings...", "stage" to "SCANNING"))

        // Step 1: Ensure we are online before initiating sync
        if (!connectivityService.isConnected()) {
            Log.w(TAG, "Device is offline. Suspending sync run.")
            DeveloperLogger.warning("SYNC_STARTED", "Device is offline. Suspending sync run.", network = network)
            setProgress(workDataOf("progress" to "Device is offline. Waiting for network...", "stage" to "WAITING_FOR_NETWORK"))
            return Result.retry()
        }

        try {
            // Step 2: Fetch and import call logs from provider, match files, etc.
            DeveloperLogger.info("READ_PENDING_CALLS", "Scanning system logs & local recordings to refresh database", network = network)
            callRepository.syncCallLogs()

            // Step 2a: Resolve local Contacts to canonical Person and sync to Supabase FIRST
            // This ensures People, PhoneNumbers, Devices, Aliases exist and call.personId can be resolved properly
            try {
                DeveloperLogger.info("IDENTITY_SYNC_STARTED", "Resolving contacts to Person canonical identity and syncing to Supabase", network = network)
                setProgress(workDataOf("progress" to "Resolving contact identities with Supabase...", "stage" to "RESOLVING_IDENTITIES"))

                // 1. FIRST: Download latest global contacts from Supabase into local Room database
                // This ensures this device knows all canonical Person IDs and phone numbers registered by other devices
                DeveloperLogger.info("GLOBAL_CONTACTS_SYNC", "Downloading latest global contacts from Supabase into local Room database", network = network)
                personRepository.syncGlobalContactsFromSupabase()

                // 2. Scan and resolve local device contacts against refreshed Room cache and Supabase RPC
                personRepository.syncContactsFromDevice()

                // 3. Sync all Devices to guarantee contact_aliases foreign key
                val devices = personDao.getAllDevices().distinctBy { it.id }
                if (devices.isNotEmpty()) {
                    supabaseService.syncDevices(devices)
                }

                // 4. Collect all pending records
                val pendingPhoneNumbers = personDao.getPendingPhoneNumbers()
                val pendingAliases = personDao.getPendingAliases()
                val pendingPeople = personDao.getPendingPeople()

                // Guarantee that only valid People referenced by active pending phone numbers or aliases are upserted
                val allReferencedPersonIds = (pendingPeople.map { it.id } +
                        pendingPhoneNumbers.map { it.personId } +
                        pendingAliases.map { it.personId }).distinct()

                val peopleToUpsert = allReferencedPersonIds.mapNotNull { personDao.getPersonById(it) }
                    .filter { p ->
                        // Root-level fix: never push an orphaned person record without phone numbers or aliases
                        personDao.getPhoneNumbersForPerson(p.id).isNotEmpty() || personDao.getAliasesForPerson(p.id).isNotEmpty()
                    }
                    .distinctBy { it.id }
                if (peopleToUpsert.isNotEmpty()) {
                    val res = supabaseService.syncPeople(peopleToUpsert)
                    if (res.isSuccess) {
                        personDao.updatePeopleSyncStatus(peopleToUpsert.map { it.id }, "SYNCED")
                        DeveloperLogger.success("IDENTITY_PEOPLE_SYNCED", "Synced ${peopleToUpsert.size} people to Supabase.", network = network)
                    } else {
                        val errMsg = res.exceptionOrNull()?.message ?: "Unknown error"
                        DeveloperLogger.error("IDENTITY_PEOPLE_SYNC_FAILED", "Failed syncing people to Supabase: $errMsg", exception = res.exceptionOrNull(), network = network)
                    }
                }

                // 3. Sync phone numbers (deduplicated by normalized_number and ensuring valid personId)
                if (pendingPhoneNumbers.isNotEmpty()) {
                    val validPhoneNumbers = pendingPhoneNumbers
                        .filter { pn -> personDao.getPersonById(pn.personId) != null }
                        .distinctBy { it.normalizedNumber }

                    if (validPhoneNumbers.isNotEmpty()) {
                        val res = supabaseService.syncPhoneNumbers(validPhoneNumbers)
                        if (res.isSuccess) {
                            personDao.updatePhoneNumbersSyncStatus(validPhoneNumbers.map { it.id }, "SYNCED")
                            DeveloperLogger.success("IDENTITY_PHONES_SYNCED", "Synced ${validPhoneNumbers.size} phone numbers to Supabase.", network = network)
                        } else {
                            val errMsg = res.exceptionOrNull()?.message ?: "Unknown error"
                            DeveloperLogger.error("IDENTITY_PHONES_SYNC_FAILED", "Failed syncing phone numbers to Supabase: $errMsg", exception = res.exceptionOrNull(), network = network)
                        }
                    }
                }

                // 4. Sync contact aliases (deduplicated by device_id + normalized_number and ensuring valid personId)
                if (pendingAliases.isNotEmpty()) {
                    val validAliases = pendingAliases
                        .filter { ca -> personDao.getPersonById(ca.personId) != null }
                        .distinctBy { "${it.deviceId}_${it.normalizedNumber}" }

                    if (validAliases.isNotEmpty()) {
                        val res = supabaseService.syncContactAliases(validAliases)
                        if (res.isSuccess) {
                            personDao.updateAliasesSyncStatus(validAliases.map { it.id }, "SYNCED")
                            DeveloperLogger.success("IDENTITY_ALIASES_SYNCED", "Synced ${validAliases.size} aliases to Supabase.", network = network)
                        } else {
                            val errMsg = res.exceptionOrNull()?.message ?: "Unknown error"
                            DeveloperLogger.error("IDENTITY_ALIASES_SYNC_FAILED", "Failed syncing aliases to Supabase: $errMsg", exception = res.exceptionOrNull(), network = network)
                        }
                    }
                }

                // 4. Download latest global contacts and leads from Supabase into Room cache
                DeveloperLogger.info("GLOBAL_CONTACTS_SYNC", "Downloading latest global contacts from Supabase into local Room database", network = network)
                personRepository.syncGlobalContactsFromSupabase()
            } catch (e: Exception) {
                DeveloperLogger.error("IDENTITY_SYNC_FAILED", "Exception during Person identity sync: ${e.message}", exception = e, network = network)
            }

            // Step 2b: Populate / update sales_calls table from refreshed call log & resolved identities
            val salespersonPhoneRaw = firestoreService.getDevicePhoneNumber().ifBlank {
                personDao.getAllDevices().firstOrNull()?.devicePhone.orEmpty().ifBlank { "device_owner" }
            }
            val salespersonPhone = FirestoreRepositoryImpl.normalizePhoneNumber(salespersonPhoneRaw)
            val salespersonName  = firestoreService.getDeviceOwnerName().ifBlank { "Sales Rep" }
            val allCalls = callRepository.getAllCallsSnapshot().take(100)
            DeveloperLogger.info("SALES_CALL_CREATION_STARTED", "Generating/updating SalesCallEntity cache for last ${allCalls.size} calls.", network = network)
            var createdCount = 0
            var updatedCount = 0
            for (call in allCalls) {
                val normBuyer = com.example.callog.core.utils.PhoneNumberNormalizer.normalize(call.number)
                val resolvedPersonId = call.personId ?: (if (normBuyer.isNotBlank()) personDao.findPhoneNumberByNormalized(normBuyer)?.personId else null)
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
                            personId         = resolvedPersonId,
                            createdAt        = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("UTC")
                            }.format(java.util.Date(call.timestamp)),
                            syncStatus       = "PENDING"
                        )
                    )
                    createdCount++
                } else if (existing.duration != call.duration || existing.buyerName != call.name || existing.personId != resolvedPersonId || existing.callType != call.callType) {
                    salesCallDao.insertSalesCall(
                        existing.copy(
                            duration = call.duration,
                            buyerName = call.name,
                            personId = resolvedPersonId ?: existing.personId,
                            callType = call.callType,
                            syncStatus = "PENDING"
                        )
                    )
                    updatedCount++
                }
            }
            if (createdCount > 0 || updatedCount > 0) {
                DeveloperLogger.success("SALES_CALL_CREATED", "Populated $createdCount new, updated $updatedCount sales call logs locally.", network = network)
            }

            // Step 2c: Sync pending sales_calls to Supabase
            try {
                val pendingSalesCalls = salesCallDao.getPendingSalesCalls().take(100)
                if (pendingSalesCalls.isNotEmpty()) {
                    DeveloperLogger.info("SUPABASE_UPLOAD_STARTED", "Syncing ${pendingSalesCalls.size} pending sales calls to Supabase.", network = network)
                    setProgress(workDataOf("progress" to "Syncing ${pendingSalesCalls.size} sales calls to Supabase...", "stage" to "SYNCING_SUPABASE"))
                    val startTime = System.currentTimeMillis()
                    val syncResult = supabaseService.syncSalesCalls(pendingSalesCalls)
                    val duration = System.currentTimeMillis() - startTime
                    // Sync to CRM table lead_communications (triggers auto-association to leads_master)
                    try {
                        val crmEvents = pendingSalesCalls.map { sc ->
                            val epochMs = try {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                                }.parse(sc.createdAt)?.time ?: System.currentTimeMillis()
                            } catch (_: Exception) {
                                System.currentTimeMillis()
                            }
                            com.example.callog.domain.crm.model.CrmCallEvent(
                                callId = sc.callId,
                                rawPhoneNumber = sc.buyerPhone,
                                contactName = sc.buyerName,
                                callType = sc.callType,
                                durationSeconds = sc.duration,
                                timestampEpochMs = epochMs,
                                salespersonPhone = sc.salespersonPhone,
                                salespersonName = sc.salespersonName
                            )
                        }
                        crmRepository.publishBatchCallEvents(crmEvents)
                    } catch (e: Exception) {
                        DeveloperLogger.warning("CRM_COMM_SYNC_WARNING", "Failed syncing batch to lead_communications: ${e.message}", network = network)
                    }

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

            // Step 2d: Sync pending CRM Leads to Supabase
            try {
                val pendingLeads = leadDao.getPendingLeads().take(100)
                if (pendingLeads.isNotEmpty()) {
                    DeveloperLogger.info("SUPABASE_LEAD_UPLOAD_STARTED", "Syncing ${pendingLeads.size} pending leads to Supabase.", network = network)
                    setProgress(workDataOf("progress" to "Syncing ${pendingLeads.size} leads to Supabase...", "stage" to "SYNCING_SUPABASE"))
                    val leadStartTime = System.currentTimeMillis()
                    val leadSyncResult = supabaseService.syncLeads(pendingLeads)
                    val leadDuration = System.currentTimeMillis() - leadStartTime

                    if (leadSyncResult.isSuccess) {
                        for (lead in pendingLeads) {
                            leadDao.updateSyncStatusIfTimestampMatches(lead.id, "SYNCED", lead.updatedAt)
                        }
                        DeveloperLogger.success("SUPABASE_LEAD_UPLOAD_SUCCESS", "Successfully synced ${pendingLeads.size} leads to Supabase table leads.", durationMs = leadDuration, network = network)
                    } else {
                        val exception = leadSyncResult.exceptionOrNull()
                        val errorMsg = exception?.message ?: "Unknown lead sync error"
                        DeveloperLogger.error("SUPABASE_LEAD_UPLOAD_FAILED", "Supabase lead sync failed: $errorMsg", exception = exception, network = network)
                    }
                }
            } catch (e: Exception) {
                DeveloperLogger.error("SUPABASE_LEAD_UPLOAD_FAILED", "Exception occurred during Supabase lead sync execution", exception = e, network = network)
            }

            // Step 2e: Pull latest Supabase data into local Room DB (leads_master, lead_stages, lead_statuses, sales_calls, calls_log)
            if (connectivityService.isConnected()) {
                try {
                    // 1. Sync lead stages & statuses
                    val stagesRes = supabaseService.fetchLeadStages()
                    if (stagesRes.isSuccess) {
                        val stages = stagesRes.getOrDefault(emptyList())
                        if (stages.isNotEmpty()) {
                            leadStageDao.insertLeadStages(stages.map { com.example.callog.data.local.entity.LeadStageEntity.fromRemote(it) })
                        }
                    }
                    val statusesRes = supabaseService.fetchLeadStatuses()
                    if (statusesRes.isSuccess) {
                        val statuses = statusesRes.getOrDefault(emptyList())
                        if (statuses.isNotEmpty()) {
                            leadStatusDao.insertLeadStatuses(statuses.map { com.example.callog.data.local.entity.LeadStatusEntity.fromRemote(it) })
                        }
                    }

                    // 2. Sync leads_master
                    val leadsRes = supabaseService.fetchLeadsMaster(limit = 200)
                    if (leadsRes.isSuccess) {
                        val remoteLeads = leadsRes.getOrDefault(emptyList())
                        if (remoteLeads.isNotEmpty()) {
                            val leadEntities = remoteLeads.map { com.example.callog.data.local.entity.LeadMasterEntity.fromRemote(it) }
                            leadMasterDao.insertLeadsMaster(leadEntities)
                            DeveloperLogger.success("CRM_PIPELINE_SYNCED", "Synced ${remoteLeads.size} leads_master from Supabase to local Room.", network = network)
                            try {
                                contactRingtoneSyncManager.syncAllLeadsToContacts(leadEntities)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error syncing contact ringtones: ${e.message}")
                            }
                        }
                    }

                    // 3. Pull recent sales_calls from Supabase
                    val salesCallsRes = supabaseService.fetchSalesCalls(limit = 200)
                    if (salesCallsRes.isSuccess) {
                        val remoteSalesCalls = salesCallsRes.getOrDefault(emptyList())
                        if (remoteSalesCalls.isNotEmpty()) {
                            val entities = remoteSalesCalls.map { sc ->
                                com.example.callog.data.local.entity.SalesCallEntity(
                                    salespersonPhone = sc.salespersonPhone,
                                    salespersonName = sc.salespersonName,
                                    buyerPhone = sc.buyerPhone,
                                    buyerName = sc.buyerName,
                                    callType = sc.callType,
                                    callId = sc.callId,
                                    duration = sc.duration,
                                    personId = sc.personId,
                                    createdAt = sc.createdAt,
                                    syncStatus = "SYNCED"
                                )
                            }
                            salesCallDao.insertSalesCalls(entities)
                        }
                    }

                    // 4. Pull calls_log from Supabase
                    val callsLogRes = supabaseService.fetchCallsLog(limit = 200)
                    if (callsLogRes.isSuccess) {
                        val remoteCallsLogs = callsLogRes.getOrDefault(emptyList())
                        if (remoteCallsLogs.isNotEmpty()) {
                            callsLogDao.insertCallsLogs(remoteCallsLogs.map { com.example.callog.data.local.entity.CallsLogEntity.fromRemote(it) })
                        }
                    }
                } catch (e: Exception) {
                    DeveloperLogger.warning("SUPABASE_DATA_PULL_WARNING", "Warning during Supabase data pull to Room: ${e.message}", network = network)
                }
            }

            // Step 3: Fetch all pending items from local database for Firestore (limit to last 100)
            val pendingCalls = syncRepository.getPendingCalls().take(100)
            if (pendingCalls.isEmpty()) {
                DeveloperLogger.success("SYNC_COMPLETED", "All records up to date. Sync completed successfully.", network = network)
                setProgress(workDataOf("progress" to "Sync completed. All records up to date.", "stage" to "COMPLETED"))
                runLogsMaintenance(syncLogDao)
                return Result.success()
            }

            DeveloperLogger.info("FIRESTORE_METADATA_UPLOAD_STARTED", "Found ${pendingCalls.size} pending call logs to sync to Firestore.", network = network)
            setProgress(workDataOf("progress" to "Syncing ${pendingCalls.size} call records to Firebase Firestore...", "stage" to "SYNCING_FIRESTORE"))
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
            val completionStage = if (failCount > 0 && successCount > 0) "PARTIALLY_COMPLETED" else if (failCount > 0) "FAILED" else "COMPLETED"
            setProgress(workDataOf("progress" to "Sync finished: $successCount synced, $failCount failed", "stage" to completionStage))
            runLogsMaintenance(syncLogDao)
            return Result.success()

        } catch (e: Exception) {
            DeveloperLogger.error("SYNC_FAILED", "Sync worker execution crashed", exception = e, network = network)
            return Result.retry()
        }
    }

    private suspend fun runLogsMaintenance(syncLogDao: SyncLogDao) {
        try {
            val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000) // 7 days
            syncLogDao.deleteLogsOlderThan(cutoff)
            syncLogDao.trimLogs(300)
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
