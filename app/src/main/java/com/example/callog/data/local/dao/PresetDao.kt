package com.example.callog.data.local.dao

import androidx.room.*
import com.example.callog.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM app_presets ORDER BY createdAt ASC")
    fun observeAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM app_presets WHERE isActive = 1 LIMIT 1")
    fun observeActivePreset(): Flow<PresetEntity?>

    @Query("SELECT * FROM app_presets ORDER BY createdAt ASC")
    suspend fun getAllPresets(): List<PresetEntity>

    @Query("SELECT * FROM app_presets WHERE id = :id LIMIT 1")
    suspend fun getPresetById(id: String): PresetEntity?

    @Query("SELECT * FROM app_presets WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePreset(): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<PresetEntity>)

    @Query("UPDATE app_presets SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE app_presets SET isActive = 1 WHERE id = :id")
    suspend fun activatePreset(id: String)

    @Transaction
    suspend fun setActivePreset(id: String) {
        deactivateAll()
        activatePreset(id)
    }

    @Query("DELETE FROM app_presets WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM app_presets")
    suspend fun getCount(): Int
}
