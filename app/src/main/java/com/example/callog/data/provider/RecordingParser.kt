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
    private val regex = Regex("(?i)^Call recording\\s+([+_a-zA-Z0-9\\s()-]+)_(\\d{6}|\\d{8})_(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim()
        val dateStr = match.groupValues[2]
        val timeStr = match.groupValues[3]
        
        val pattern = if (dateStr.length == 8) "yyyyMMddHHmmss" else "yyMMddHHmmss"
        val timestamp = parseDateTime("${dateStr}${timeStr}", pattern)
        
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
 * Matches: Call <phone/name>_<YYMMDD>_<HHMMSS> or Call <phone/name>_<YYYYMMDD>_<HHMMSS>
 */
class SamsungCallParser : BaseRecordingParser() {
    private val regex = Regex("(?i)^Call\\s+([+_a-zA-Z0-9\\s()-]+)_(\\d{6}|\\d{8})_(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val identifier = match.groupValues[1].trim()
        val dateStr = match.groupValues[2]
        val timeStr = match.groupValues[3]
        
        val pattern = if (dateStr.length == 8) "yyyyMMddHHmmss" else "yyMMddHHmmss"
        val timestamp = parseDateTime("${dateStr}${timeStr}", pattern)
        
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
 * Matches Xiaomi/MIUI/HyperOS formats:
 * 1. <phone>(<contact>)_<YYYYMMDDHHMMSS> or <contact>(<phone>)_<YYYYMMDDHHMMSS>
 * 2. <phone/name>_<YYYYMMDDHHMMSS>
 */
class XiaomiCallParser : BaseRecordingParser() {
    private val parenRegex = Regex("^([+_a-zA-Z0-9\\s-]+)\\(([^)]+)\\)_(\\d{8})(\\d{6})$")
    private val simpleRegex = Regex("^(?:Call_)?([+_a-zA-Z0-9\\s()-]+)_(\\d{8})(\\d{6})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val parenMatch = parenRegex.find(nameWithoutExt)
        if (parenMatch != null) {
            val part1 = parenMatch.groupValues[1].trim()
            val part2 = parenMatch.groupValues[2].trim()
            val dateStr = parenMatch.groupValues[3]
            val timeStr = parenMatch.groupValues[4]
            val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")

            val phone = if (isPhoneNumber(part1)) part1 else if (isPhoneNumber(part2)) part2 else null
            val contact = if (phone != part1 && part1.isNotBlank()) part1 else if (phone != part2 && part2.isNotBlank()) part2 else null

            return RecordingMetadata(
                phoneNumber = phone,
                contactName = contact,
                timestamp = timestamp,
                originalFileName = fileName,
                parserName = "XiaomiCallParser"
            )
        }

        val simpleMatch = simpleRegex.find(nameWithoutExt)
        if (simpleMatch != null) {
            val identifier = simpleMatch.groupValues[1].trim()
            val dateStr = simpleMatch.groupValues[2]
            val timeStr = simpleMatch.groupValues[3]
            val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")

            val isPhone = isPhoneNumber(identifier)
            return RecordingMetadata(
                phoneNumber = if (isPhone) identifier else null,
                contactName = if (!isPhone) identifier else null,
                timestamp = timestamp,
                originalFileName = fileName,
                parserName = "XiaomiCallParser"
            )
        }

        return null
    }
}

/**
 * Matches Vivo / Oppo / Realme / ColorOS formats:
 * 1. REC_<YYYYMMDD_HHMMSS>_<phone/name>
 * 2. <phone/name>_<YYYY-MM-DD-HH-MM-SS>
 */
class OppoVivoCallParser : BaseRecordingParser() {
    private val recPrefixRegex = Regex("^(?i)REC_(\\d{8})_(\\d{6})_([+_a-zA-Z0-9\\s()-]+)$")
    private val hyphenDateRegex = Regex("^([+_a-zA-Z0-9\\s()-]+)_(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})$")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val recMatch = recPrefixRegex.find(nameWithoutExt)
        if (recMatch != null) {
            val dateStr = recMatch.groupValues[1]
            val timeStr = recMatch.groupValues[2]
            val identifier = recMatch.groupValues[3].trim()
            val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")
            val isPhone = isPhoneNumber(identifier)
            return RecordingMetadata(
                phoneNumber = if (isPhone) identifier else null,
                contactName = if (!isPhone) identifier else null,
                timestamp = timestamp,
                originalFileName = fileName,
                parserName = "OppoVivoCallParser"
            )
        }

        val hyphenMatch = hyphenDateRegex.find(nameWithoutExt)
        if (hyphenMatch != null) {
            val identifier = hyphenMatch.groupValues[1].trim()
            val yyyy = hyphenMatch.groupValues[2]
            val mm = hyphenMatch.groupValues[3]
            val dd = hyphenMatch.groupValues[4]
            val hh = hyphenMatch.groupValues[5]
            val min = hyphenMatch.groupValues[6]
            val ss = hyphenMatch.groupValues[7]
            val timestamp = parseDateTime("${yyyy}${mm}${dd}${hh}${min}${ss}", "yyyyMMddHHmmss")
            val isPhone = isPhoneNumber(identifier)
            return RecordingMetadata(
                phoneNumber = if (isPhone) identifier else null,
                contactName = if (!isPhone) identifier else null,
                timestamp = timestamp,
                originalFileName = fileName,
                parserName = "OppoVivoCallParser"
            )
        }

        return null
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
 * Matches standalone format: ...YYYY-MM-DD_HH-MM-SS... with any random string prefix/suffix
 */
class TimestampOnlyParser : BaseRecordingParser() {
    private val regex = Regex("(\\d{4})[-_](\\d{2})[-_](\\d{2})[-_\\s]+(\\d{2})[-_](\\d{2})[-_](\\d{2})")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val dateStr = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
        val timeStr = "${match.groupValues[4]}-${match.groupValues[5]}-${match.groupValues[6]}"
        
        val timestamp = parseDateTime("${dateStr} ${timeStr}", "yyyy-MM-dd HH-mm-ss")
        return RecordingMetadata(
            phoneNumber = null,
            contactName = null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "TimestampOnlyParser"
        )
    }
}

/**
 * Matches standalone compact format: ...YYYYMMDD_HHMMSS... with any random string prefix/suffix
 */
class CompactTimestampOnlyParser : BaseRecordingParser() {
    private val regex = Regex("(\\d{8})[_T-](\\d{6})")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val match = regex.find(nameWithoutExt) ?: return null
        
        val dateStr = match.groupValues[1]
        val timeStr = match.groupValues[2]
        
        val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")
        return RecordingMetadata(
            phoneNumber = null,
            contactName = null,
            timestamp = timestamp,
            originalFileName = fileName,
            parserName = "CompactTimestampOnlyParser"
        )
    }
}

/**
 * Matches 13-digit (ms) or 10-digit (s) Epoch timestamp anywhere in the filename
 */
class EpochTimestampParser : BaseRecordingParser() {
    private val msRegex = Regex("(\\b|[^0-9])(1[6-9]\\d{11})(\\b|[^0-9])")
    private val sRegex = Regex("(\\b|[^0-9])(1[6-9]\\d{8})(\\b|[^0-9])")

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val msMatch = msRegex.find(nameWithoutExt)
        if (msMatch != null) {
            val epochMs = msMatch.groupValues[2].toLongOrNull()
            if (epochMs != null && epochMs > 1_500_000_000_000L) {
                return RecordingMetadata(
                    phoneNumber = null,
                    contactName = null,
                    timestamp = epochMs,
                    originalFileName = fileName,
                    parserName = "EpochTimestampParser"
                )
            }
        }

        val sMatch = sRegex.find(nameWithoutExt)
        if (sMatch != null) {
            val epochS = sMatch.groupValues[2].toLongOrNull()
            if (epochS != null && epochS > 1_500_000_000L) {
                return RecordingMetadata(
                    phoneNumber = null,
                    contactName = null,
                    timestamp = epochS * 1000L,
                    originalFileName = fileName,
                    parserName = "EpochTimestampParser"
                )
            }
        }
        return null
    }
}

/**
 * Matches: Callog_<phone/name>_<YYYYMMDD>_<HHMMSS> or Callog_Rec_<phone/name>_<YYYYMMDD>_<HHMMSS>
 * or Callog_<phone/name>_<YYYYMMDDHHMMSS> or Callog_<phone/name>_<EpochTimestamp>
 */
class CallogRecordingParser : BaseRecordingParser() {
    private val dateTimeRegex = Regex("(?i)^Callog(?:_Rec)?_([+_a-zA-Z0-9\\s()-]+?)_(\\d{8})_?(\\d{6})(?:_.*)?$")
    private val epochMsRegex = Regex("(?i)^Callog(?:_Rec)?_([+_a-zA-Z0-9\\s()-]+?)_(1[6-9]\\d{11})(?:_.*)?$")
    private val epochSecRegex = Regex("(?i)^Callog(?:_Rec)?_([+_a-zA-Z0-9\\s()-]+?)_(1[6-9]\\d{8})(?:_.*)?$")

    private fun cleanIdentifier(raw: String): Pair<String?, String?> {
        val identifier = raw.trim().trim { it == '_' || it == '-' || it.isWhitespace() }
        val digitsOnly = identifier.replace(Regex("[^0-9]"), "")
        val isPhone = isPhoneNumber(identifier) || digitsOnly.length in 7..15
        val phone = if (isPhone) {
            if (!identifier.startsWith("+") && digitsOnly.length >= 10 && (digitsOnly.startsWith("91") || digitsOnly.length == 10)) {
                if (digitsOnly.startsWith("91")) "+$digitsOnly" else "+91$digitsOnly"
            } else {
                identifier
            }
        } else null
        val name = if (!isPhone) identifier else null
        return Pair(phone, name)
    }

    override fun parse(fileName: String): RecordingMetadata? {
        val nameWithoutExt = fileName.substringBeforeLast(".")

        val dtMatch = dateTimeRegex.find(nameWithoutExt)
        if (dtMatch != null) {
            val (phone, name) = cleanIdentifier(dtMatch.groupValues[1])
            val dateStr = dtMatch.groupValues[2]
            val timeStr = dtMatch.groupValues[3]
            val timestamp = parseDateTime("${dateStr}${timeStr}", "yyyyMMddHHmmss")
            return RecordingMetadata(
                phoneNumber = phone,
                contactName = name,
                timestamp = timestamp,
                originalFileName = fileName,
                parserName = "CallogRecordingParser"
            )
        }

        val msMatch = epochMsRegex.find(nameWithoutExt)
        if (msMatch != null) {
            val (phone, name) = cleanIdentifier(msMatch.groupValues[1])
            val epochMs = msMatch.groupValues[2].toLongOrNull()
            return RecordingMetadata(
                phoneNumber = phone,
                contactName = name,
                timestamp = epochMs,
                originalFileName = fileName,
                parserName = "CallogRecordingParser"
            )
        }

        val secMatch = epochSecRegex.find(nameWithoutExt)
        if (secMatch != null) {
            val (phone, name) = cleanIdentifier(secMatch.groupValues[1])
            val epochSec = secMatch.groupValues[2].toLongOrNull()
            return RecordingMetadata(
                phoneNumber = phone,
                contactName = name,
                timestamp = if (epochSec != null) epochSec * 1000L else null,
                originalFileName = fileName,
                parserName = "CallogRecordingParser"
            )
        }

        return null
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
            CallogRecordingParser(),
            SamsungCallRecordingParser(),
            SamsungCallParser(),
            XiaomiCallParser(),
            OppoVivoCallParser(),
            CompactDateTimeParser(),
            StandardDateTimeParser(),
            SimpleDateTimeParser(),
            TimestampOnlyParser(),
            CompactTimestampOnlyParser(),
            EpochTimestampParser()
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
