package com.example.callog.di

import com.example.callog.data.service.UploadServiceImpl
import com.example.callog.domain.service.UploadService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindUploadService(
        uploadServiceImpl: UploadServiceImpl
    ): UploadService

    @Binds
    @Singleton
    abstract fun bindSyncManager(
        syncManagerImpl: com.example.callog.data.service.SyncManagerImpl
    ): com.example.callog.domain.service.SyncManager
}
