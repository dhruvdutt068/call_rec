package com.example.callog.domain.usecase.preset

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPresetsUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    operator fun invoke(): Flow<List<AppPreset>> {
        return repository.getPresetsFlow()
    }

    suspend fun execute(): List<AppPreset> {
        return repository.getPresets()
    }
}
