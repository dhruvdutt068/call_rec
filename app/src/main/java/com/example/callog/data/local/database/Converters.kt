package com.example.callog.data.local.database

import androidx.room.TypeConverter
import com.example.callog.data.local.entity.MatchStatus
import com.example.callog.data.local.entity.UploadStatus

class Converters {
    @TypeConverter
    fun fromMatchStatus(value: MatchStatus): String {
        return value.name
    }

    @TypeConverter
    fun toMatchStatus(value: String): MatchStatus {
        return try {
            MatchStatus.valueOf(value)
        } catch (e: Exception) {
            MatchStatus.UNMATCHED
        }
    }

    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String {
        return value.name
    }

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus {
        return try {
            UploadStatus.valueOf(value)
        } catch (e: Exception) {
            UploadStatus.PENDING
        }
    }
}
