package com.example.callog.domain.usecase.preset

import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.repository.PresetRepository
import javax.inject.Inject

class ImportExportPresetUseCase @Inject constructor(
    private val repository: PresetRepository
) {
    suspend fun exportJson(presetId: String, includeSecrets: Boolean): String? {
        return repository.exportPresetJson(presetId, includeSecrets)
    }

    suspend fun importJson(jsonString: String): Result<AppPreset> {
        return repository.importPresetJson(jsonString)
    }
}
