package com.example.callog.domain.usecase.preset

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.repository.PresetRepository
import javax.inject.Inject

class DuplicatePresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    suspend operator fun invoke(sourceId: String, newName: String): AppPreset? {
        return repository.duplicatePreset(sourceId, newName)
    }
}
