package com.example.callog.di

import com.example.callog.data.repository.CallRepositoryImpl
import com.example.callog.domain.repository.CallRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCallRepository(
        callRepositoryImpl: CallRepositoryImpl
    ): CallRepository

    @Binds
    @Singleton
    abstract fun bindPersonRepository(
        personRepositoryImpl: com.example.callog.data.repository.PersonRepositoryImpl
    ): com.example.callog.domain.repository.PersonRepository

    @Binds
    @Singleton
    abstract fun bindLeadRepository(
        leadRepositoryImpl: com.example.callog.data.repository.LeadRepositoryImpl
    ): com.example.callog.domain.repository.LeadRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        conversationRepositoryImpl: com.example.callog.data.repository.ConversationRepositoryImpl
    ): com.example.callog.domain.repository.ConversationRepository

    @Binds
    @Singleton
    abstract fun bindDeviceContactsRepository(
        deviceContactsRepositoryImpl: com.example.callog.data.repository.DeviceContactsRepositoryImpl
    ): com.example.callog.domain.repository.DeviceContactsRepository
}

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactsDirectoryEntryPoint {
    fun deviceContactsRepository(): com.example.callog.domain.repository.DeviceContactsRepository
    fun personRepository(): com.example.callog.domain.repository.PersonRepository
}
