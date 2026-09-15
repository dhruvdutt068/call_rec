package com.example.callog.domain.model

/**
 * Represents the target remote infrastructure environment.
 */
enum class AppEnvironment(val label: String) {
    DEVELOPMENT("Development"),
    DEPLOYMENT("Deployment");

    companion object {
        fun fromString(value: String?): AppEnvironment {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEVELOPMENT
        }
    }
}
