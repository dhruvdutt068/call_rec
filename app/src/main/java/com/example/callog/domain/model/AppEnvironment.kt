package com.example.callog.domain.model

/**
 * Represents the target remote infrastructure environment.
 */
enum class AppEnvironment(val label: String) {
    DEVELOPMENT("Development"),
    PRODUCTION("Production");

    companion object {
        fun fromString(value: String?): AppEnvironment {
            return when (value?.trim()?.uppercase()) {
                "PRODUCTION", "DEPLOYMENT" -> PRODUCTION
                "DEVELOPMENT" -> DEVELOPMENT
                else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEVELOPMENT
            }
        }
    }
}
