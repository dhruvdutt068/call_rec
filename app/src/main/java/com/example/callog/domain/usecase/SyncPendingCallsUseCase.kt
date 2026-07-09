package com.example.callog.domain.usecase

import com.example.callog.domain.repository.FirestoreRepository
import javax.inject.Inject

class SyncPendingCallsUseCase @Inject constructor(
    private val firestoreRepository: FirestoreRepository
) {
    suspend operator fun invoke() {
        firestoreRepository.syncPendingCalls()
    }
}
