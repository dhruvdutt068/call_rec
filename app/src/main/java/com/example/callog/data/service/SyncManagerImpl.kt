package com.example.callog.data.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.callog.data.worker.SyncWorker
import com.example.callog.domain.repository.FirestoreRepository
import com.example.callog.domain.service.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreRepository: FirestoreRepository
) : SyncManager {

    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val ONE_TIME_SYNC_WORK_NAME = "one_time_sync_work"
        const val PERIODIC_SYNC_WORK_NAME = "periodic_sync_work"
    }

    private val allSyncWorkInfos: Flow<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_SYNC_WORK_NAME)
        .combine(workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK_NAME)) { oneTime: List<WorkInfo>, periodic: List<WorkInfo> ->
            oneTime + periodic
        }

    override val isSyncing: Flow<Boolean> = allSyncWorkInfos
        .map { workInfos ->
            workInfos.any { it.state == WorkInfo.State.RUNNING }
        }

    override val syncProgress: Flow<String?> = allSyncWorkInfos
        .map { workInfos ->
            val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            runningWork?.progress?.getString("progress")
        }

    override fun startSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun stopSync() {
        workManager.cancelUniqueWork(ONE_TIME_SYNC_WORK_NAME)
    }

    override fun schedulePeriodicSync(intervalMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val interval = intervalMinutes.coerceAtLeast(15)
        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    override fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    override suspend fun forceResetAndSync() {
        firestoreRepository.resetAllSyncStatus()
        startSync()
    }
}
