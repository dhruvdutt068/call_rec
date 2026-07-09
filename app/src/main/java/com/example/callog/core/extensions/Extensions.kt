package com.example.callog.core.extensions

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toRelativeTimeSpan(): String {
    val now = System.currentTimeMillis()
    return if (Math.abs(now - this) < DateUtils.MINUTE_IN_MILLIS) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(
            this,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
}

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Int.toDurationString(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return when {
        hours > 0 -> String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, seconds)
        minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
        else -> String.format(Locale.getDefault(), "%ds", seconds)
    }
}

fun String.normalizePhoneNumber(): String {
    // Remove formatting symbols: spaces, hyphens, parenthesis, and leading + country code if necessary
    val digits = this.replace(Regex("[^0-9+]"), "")
    return if (digits.startsWith("+") && digits.length > 10) {
        digits.substring(digits.length - 10) // match last 10 digits for standard comparison
    } else {
        digits
    }
}
