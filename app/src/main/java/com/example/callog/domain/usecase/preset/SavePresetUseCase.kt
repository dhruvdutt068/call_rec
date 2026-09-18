package com.example.callog.domain.usecase.preset

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetSecrets
import com.example.callog.domain.repository.PresetRepository
import javax.inject.Inject

class SavePresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    suspend operator fun invoke(preset: AppPreset, secrets: PresetSecrets? = null) {
        repository.savePreset(preset, secrets)
    }
}
