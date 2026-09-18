package com.example.callog.presentation.navigation3

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Type-safe destination keys for Navigation 3.
 * All destinations represent state-driven routes with strictly typed parameters.
 * Canonical contactId values are directly passed without string conversions.
 */
@Serializable
@Immutable
sealed interface Nav3Key {

    /**
     * Authentication flow destinations
     */
    @Serializable
    sealed interface Auth : Nav3Key {
        @Serializable
        data object Login : Auth

        @Serializable
        data object Register : Auth

        @Serializable
        data object ForgotPassword : Auth

        @Serializable
        data class OtpVerification(val destinationAddress: String) : Auth
    }

    /**
     * Top-level CRM destinations
     */
    @Serializable
    sealed interface CrmTab : Nav3Key {
        @Serializable
        data object Dashboard : CrmTab

        @Serializable
        data object Calls : CrmTab

        @Serializable
        data object Contacts : CrmTab

        @Serializable
        data object Recordings : CrmTab

        @Serializable
        data object Tasks : CrmTab

        @Serializable
        data object Meetings : CrmTab

        @Serializable
        data object Analytics : CrmTab

        @Serializable
        data object Settings : CrmTab

        @Serializable
        data object CallLogs : CrmTab

        @Serializable
        data object Dialer : CrmTab

        @Serializable
        data object Favorites : CrmTab

        @Serializable
        data object DeveloperLogs : CrmTab

        @Serializable
        data object RingtoneSettings : CrmTab

        @Serializable
        data object CallSimulator : CrmTab
    }

    /**
     * Contact and CRM interaction destinations
     */
    @Serializable
    sealed interface Contacts : Nav3Key {
        @Serializable
        data object ContactList : Contacts

        /**
         * Contact details destination with canonical [contactId].
         * @param contactId Canonical system contact ID.
         * @param canonicalContactId Optional remote or UUID-based canonical ID for sync.
         */
        @Serializable
        data class ContactDetails(
            val contactId: String,
            val canonicalContactId: String? = null
        ) : Contacts

        /**
         * Device contact details destination with local [androidContactId].
         */
        @Serializable
        data class DeviceContactDetails(
            val androidContactId: String
        ) : Contacts
    }

    /**
     * Task management flow destinations
     */
    @Serializable
    sealed interface Task : Nav3Key {
        @Serializable
        data object TaskList : Task

        @Serializable
        data class CreateTask(val initialContactId: String? = null) : Task

        @Serializable
        data class TaskDetails(val taskId: String) : Task
    }

    /**
     * Meeting and schedule flow destinations
     */
    @Serializable
    sealed interface Meeting : Nav3Key {
        @Serializable
        data object MeetingList : Meeting

        @Serializable
        data class ScheduleMeeting(val initialContactId: String? = null) : Meeting

        @Serializable
        data class MeetingDetails(val meetingId: String) : Meeting
    }
}
