package com.example.callog.di

import com.example.callog.data.repository.FirestoreRepositoryImpl
import com.example.callog.data.repository.RecordingRepositoryImpl
import com.example.callog.domain.repository.FirestoreRepository
import com.example.callog.domain.repository.RecordingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreModule {

    @Binds
    @Singleton
    abstract fun bindFirestoreRepository(
        firestoreRepositoryImpl: FirestoreRepositoryImpl
    ): FirestoreRepository

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(
        recordingRepositoryImpl: RecordingRepositoryImpl
    ): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: com.example.callog.data.repository.SyncRepositoryImpl
    ): com.example.callog.domain.repository.SyncRepository
}
