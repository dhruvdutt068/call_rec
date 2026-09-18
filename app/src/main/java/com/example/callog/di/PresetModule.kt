package com.example.callog.di

import com.example.callog.data.provider.ActivePresetProviderImpl
import com.example.callog.data.repository.PresetRepositoryImpl
import com.example.callog.domain.provider.ActivePresetProvider
import com.example.callog.domain.repository.PresetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PresetModule {

    @Binds
    @Singleton
    abstract fun bindPresetRepository(
        impl: PresetRepositoryImpl
    ): PresetRepository

    @Binds
    @Singleton
    abstract fun bindActivePresetProvider(
        impl: ActivePresetProviderImpl
    ): ActivePresetProvider
}
