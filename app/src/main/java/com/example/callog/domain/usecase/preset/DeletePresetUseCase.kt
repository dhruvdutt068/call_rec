package com.example.callog.domain.usecase.preset

import com.example.callog.domain.repository.PresetRepository
import javax.inject.Inject

class DeletePresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    suspend operator fun invoke(presetId: String): Boolean {
        return repository.deletePreset(presetId)
    }
}
