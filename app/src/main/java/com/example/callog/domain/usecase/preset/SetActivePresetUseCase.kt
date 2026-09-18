package com.example.callog.domain.usecase.preset

import com.example.callog.domain.repository.PresetRepository
import javax.inject.Inject

class SetActivePresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    suspend operator fun invoke(presetId: String) {
        repository.setActivePreset(presetId)
    }
}
