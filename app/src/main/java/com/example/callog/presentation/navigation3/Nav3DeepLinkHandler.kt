package com.example.callog.presentation.navigation3

import android.net.Uri

/**
 * Parses application URI deep links into Navigation 3 typed keys.
 */
object Nav3DeepLinkHandler {

    fun parseUri(uri: Uri): Nav3Key? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "callog" && scheme != "https") return null

        val host = uri.host?.lowercase() ?: return null
        val pathSegments = uri.pathSegments

        return when (host) {
            "auth" -> {
                when (pathSegments.getOrNull(0)) {
                    "login" -> Nav3Key.Auth.Login
                    "register" -> Nav3Key.Auth.Register
                    "forgot-password" -> Nav3Key.Auth.ForgotPassword
                    "otp" -> {
                        val address = uri.getQueryParameter("address") ?: ""
                        Nav3Key.Auth.OtpVerification(address)
                    }
                    else -> Nav3Key.Auth.Login
                }
            }
            "crm" -> {
                when (pathSegments.getOrNull(0)) {
                    "contacts" -> {
                        val contactIdStr = pathSegments.getOrNull(1)
                        if (contactIdStr != null) {
                            Nav3Key.Contacts.ContactDetails(contactId = contactIdStr)
                        } else {
                            Nav3Key.Contacts.ContactList
                        }
                    }
                    "tasks" -> {
                        when (val sub = pathSegments.getOrNull(1)) {
                            null -> Nav3Key.Task.TaskList
                            "create" -> {
                                val contactId = uri.getQueryParameter("contactId")
                                Nav3Key.Task.CreateTask(initialContactId = contactId)
                            }
                            else -> Nav3Key.Task.TaskDetails(taskId = sub)
                        }
                    }
                    "meetings" -> {
                        when (val sub = pathSegments.getOrNull(1)) {
                            null -> Nav3Key.Meeting.MeetingList
                            "schedule" -> {
                                val contactId = uri.getQueryParameter("contactId")
                                Nav3Key.Meeting.ScheduleMeeting(initialContactId = contactId)
                            }
                            else -> Nav3Key.Meeting.MeetingDetails(meetingId = sub)
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
}
