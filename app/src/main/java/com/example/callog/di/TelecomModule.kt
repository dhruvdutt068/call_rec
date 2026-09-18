package com.example.callog.di

import com.example.callog.core.telecom.CallOrchestrator
import com.example.callog.core.telecom.DefaultCallOrchestrator
import com.example.callog.data.repository.RingtoneRepositoryImpl
import com.example.callog.data.telecom.DefaultRingtonePolicy
import com.example.callog.domain.repository.RingtonePolicy
import com.example.callog.domain.repository.RingtoneRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelecomModule {

    @Binds
    @Singleton
    abstract fun bindRingtoneRepository(
        impl: RingtoneRepositoryImpl
    ): RingtoneRepository

    @Binds
    @Singleton
    abstract fun bindRingtonePolicy(
        impl: DefaultRingtonePolicy
    ): RingtonePolicy

    @Binds
    @Singleton
    abstract fun bindCallOrchestrator(
        impl: DefaultCallOrchestrator
    ): CallOrchestrator
}

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface TelecomEntryPoint {
    fun ringtoneRepository(): RingtoneRepository
    fun ringtonePolicy(): RingtonePolicy
    fun callOrchestrator(): CallOrchestrator
    fun callSimulatorManager(): com.example.callog.domain.service.simulator.CallSimulatorManager
}

