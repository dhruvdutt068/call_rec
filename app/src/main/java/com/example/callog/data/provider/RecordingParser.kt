package com.example.callog.data.provider

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class RecordingMetadata(
    val phoneNumber: String?,
    val contactName: String?,
    val timestamp: Long?,
    val originalFileName: String,
    val parserName: String
)

interface RecordingParser {
    fun parse(fileName: String): RecordingMetadata?
}

abstract class BaseRecordingParser : RecordingParser {
    protected fun isPhoneNumber(text: String): Boolean {
        return text.matches(Regex("^[+\\d\\s()-]+$"))
    }

    protected fun parseDateTime(dateTimeStr: String, pattern: String): Long? {
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(dateTimeStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Matches: Call recording <phone/name>_<YYMMDD>_<HHMMSS>
 */
class SamsungCallRecordingParser : BaseRecordingParser() {
    private val regex = Regex("(?i)^Call recording\\s+([+_a-zA-Z0-9\\s()-]+)_(\\d{6})_(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim()
        val dateStr = match.groupValues[2]
        val timeStr = match.groupValues[3]
        
        val timestamp = parseDateTime("${dateStr}${timeStr}", "yyMMddHHmmss")
        
        val isPhone = isPhoneNumber(identifier)
        return RecordingMetadata(
            phoneNumber = if (isPhone) identifier else null,
            contactName = if (!isPhone) identifier else null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "SamsungCallRecordingParser"
        )
    }
}

/**
 * Matches: Call <phone/name>_<YYMMDD>_<HHMMSS>
 */
class SamsungCallParser : BaseRecordingParser() {
    private val regex = Regex("(?i)^Call\\s+([+_a-zA-Z0-9\\s()-]+)_(\\d{6})_(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim()
        val dateStr = match.groupValues[2]
        val timeStr = match.groupValues[3]
        
        val timestamp = parseDateTime("${dateStr}${timeStr}", "yyMMddHHmmss")
        
        val isPhone = isPhoneNumber(identifier)
        return RecordingMetadata(
            phoneNumber = if (isPhone) identifier else null,
            contactName = if (!isPhone) identifier else null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "SamsungCallParser"
        )
    }
}

/**
 * Matches: <phone/name>_<YYMMDDHHMM>
 */
class CompactDateTimeParser : BaseRecordingParser() {
    private val regex = Regex("^([+_a-zA-Z0-9\\s()-]+)_(\\d{10})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim()
        val dateTimeStr = match.groupValues[2]
        
        val timestamp = parseDateTime(dateTimeStr, "yyMMddHHmm")
        
        val isPhone = isPhoneNumber(identifier)
        return RecordingMetadata(
            phoneNumber = if (isPhone) identifier else null,
            contactName = if (!isPhone) identifier else null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "CompactDateTimeParser"
        )
    }
}

/**
 * Matches format: YYYY-MM-DD HH-MM-SS or YYYY-MM-DD HH_MM_SS
 * e.g., Chirag Pujara 2026-07-14 14-22-18
 */
class StandardDateTimeParser : BaseRecordingParser() {
    private val regex = Regex("^([+_a-zA-Z0-9\\s()-]+?)[-_\\s]*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2})[-_](\\d{2})[-_](\\d{2})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim().removeSuffix("-").removeSuffix("_").trim()
        val dateStr = match.groupValues[2]
        val hh = match.groupValues[3]
        val mm = match.groupValues[4]
        val ss = match.groupValues[5]
        
        val timestamp = parseDateTime("${dateStr} ${hh}-${mm}-${ss}", "yyyy-MM-dd HH-mm-ss")
        
        val isPhone = isPhoneNumber(identifier)
        return RecordingMetadata(
            phoneNumber = if (isPhone) identifier else null,
            contactName = if (!isPhone) identifier else null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "StandardDateTimeParser"
        )
    }
}

/**
 * Matches format: YYYYMMDD_HHMMSS or YYYYMMDDHHMMSS
 */
class SimpleDateTimeParser : BaseRecordingParser() {
    private val regex = Regex("^([+_a-zA-Z0-9\\s()-]+?)[-_\\s]*(\\d{8})[_\\s]?(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim().removeSuffix("-").removeSuffix("_").trim()
        val dateStr = match.groupValues[2]
        val timeStr = match.groupValues[3]
        
        val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")
        
        val isPhone = isPhoneNumber(identifier)
        return RecordingMetadata(
            phoneNumber = if (isPhone) identifier else null,
            contactName = if (!isPhone) identifier else null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "SimpleDateTimeParser"
        )
    }
}

/**
 * Fallback parser using file last modified
 */
class FallbackParser(private val fileLastModified: Long) : BaseRecordingParser() {
    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val cleanName = nameWithoutExt.trim().removeSuffix("-").removeSuffix("_").trim()
        if (cleanName.isEmpty()) return null
        
        val isPhone = isPhoneNumber(cleanName)
        return RecordingMetadata(
            phoneNumber = if (isPhone) cleanName else null,
            contactName = if (!isPhone) cleanName else null,
            timestamp = fileLastModified,
            originalFileName = fileName,
            parserName = "FallbackParser"
        )
    }
}

object RecordingParserChain {
    fun parse(fileName: String, fileLastModified: Long): RecordingMetadata {
        val parsers = listOf(
            SamsungCallRecordingParser(),
            SamsungCallParser(),
            CompactDateTimeParser(),
            StandardDateTimeParser(),
            SimpleDateTimeParser()
        )
        
        for (parser in parsers) {
            val meta = parser.parse(fileName)
            if (meta != null) {
                return meta
            }
        }
        
        return FallbackParser(fileLastModified).parse(fileName)!!
    }
}
