package com.example.callog.domain.usecase.preset

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetSecrets
import com.example.callog.domain.repository.PresetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActivePresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    operator fun invoke(): Flow<AppPreset?> {
        return repository.getActivePresetFlow()
    }

    suspend fun execute(): AppPreset? {
        return repository.getActivePreset()
    }

    suspend fun getSecrets(presetId: String): PresetSecrets? {
        return repository.getSecrets(presetId)
    }
}
