package com.example.callog.presentation.navigation3.allset

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * AllSet Navigation 3 Type-Safe Route / Key Hierarchy.
 *
 * Implements the official Android Navigation 3 design reference:
 * - All destinations are strongly-typed `@Serializable` objects or data classes.
 * - Central `contactId` (String) is used as the canonical navigation identity.
 * - Complete data models are NOT passed through the back stack; ViewModels load
 *   entity state from the repository layer using the canonical identifier.
 */
@Immutable
sealed interface AllSetNavKey {

    // ==========================================
    // TOP-LEVEL CRM TABS
    // ==========================================

    @Serializable
    data object Home : AllSetNavKey

    @Serializable
    data object ContactsTab : AllSetNavKey

    @Serializable
    data object TasksTab : AllSetNavKey

    @Serializable
    data object MeetingsTab : AllSetNavKey

    @Serializable
    data object SettingsTab : AllSetNavKey

    @Serializable
    data object CallLogs : AllSetNavKey

    @Serializable
    data object DeveloperLogs : AllSetNavKey

    // ==========================================
    // AUTHENTICATION FLOW
    // ==========================================

    sealed interface Auth : AllSetNavKey {
        @Serializable
        data object Login : Auth

        @Serializable
        data object Register : Auth

        @Serializable
        data object ForgotPassword : Auth

        @Serializable
        data class OtpVerification(val destinationAddress: String) : Auth
    }

    // ==========================================
    // CONTACTS FLOW
    // Contacts -> Global Search -> Search Results -> Contact Details -> Sub-destinations
    // ==========================================

    sealed interface Contacts : AllSetNavKey {
        @Serializable
        data object List : Contacts

        @Serializable
        data class GlobalSearch(val initialQuery: String = "") : Contacts

        @Serializable
        data class SearchResults(val query: String) : Contacts

        /**
         * Canonical Contact Details screen.
         * @param contactId Canonical central contact identifier (e.g. "C001", "102").
         */
        @Serializable
        data class ContactDetails(val contactId: String) : Contacts

        @Serializable
        data class EditContact(val contactId: String) : Contacts

        @Serializable
        data class AddFeedback(val contactId: String) : Contacts

        @Serializable
        data object SelectContact : Contacts

        // Contact sub-destinations / tabs
        sealed interface SubSection : Contacts {
            @Serializable
            data class Calls(val contactId: String) : SubSection

            @Serializable
            data class WhatsApp(val contactId: String) : SubSection

            @Serializable
            data class Meetings(val contactId: String) : SubSection

            @Serializable
            data class Tasks(val contactId: String) : SubSection

            @Serializable
            data class Feedback(val contactId: String) : SubSection

            @Serializable
            data class InteractionHistory(val contactId: String) : SubSection
        }
    }

    // ==========================================
    // TASKS FLOW
    // ==========================================

    sealed interface Tasks : AllSetNavKey {
        @Serializable
        data object List : Tasks

        @Serializable
        data class CreateTask(val initialContactId: String? = null) : Tasks

        @Serializable
        data class TaskDetails(val taskId: String) : Tasks
    }

    // ==========================================
    // MEETINGS FLOW
    // ==========================================

    sealed interface Meetings : AllSetNavKey {
        @Serializable
        data object List : Meetings

        @Serializable
        data class ScheduleMeeting(val initialContactId: String? = null) : Meetings

        @Serializable
        data class MeetingDetails(val meetingId: String) : Meetings
    }
}
