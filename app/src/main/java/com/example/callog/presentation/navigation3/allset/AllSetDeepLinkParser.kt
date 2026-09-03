package com.example.callog.presentation.navigation3.allset

import android.net.Uri

/**
 * Deep link resolution engine for AllSet Navigation 3.
 * Maps URIs (`allset://...` and `callog://...`) to typed [AllSetNavKey] destinations.
 */
object AllSetDeepLinkParser {

    fun parse(uri: Uri): AllSetNavKey? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "allset" && scheme != "callog" && scheme != "https") return null

        val host = uri.host?.lowercase() ?: return null
        val segments = uri.pathSegments

        return when (host) {
            "contact" -> {
                val contactId = segments.getOrNull(0) ?: uri.getQueryParameter("id")
                if (contactId != null) AllSetNavKey.Contacts.ContactDetails(contactId) else AllSetNavKey.Contacts.List
            }
            "contacts" -> {
                val sub = segments.getOrNull(0)
                when (sub) {
                    null -> {
                        val query = uri.getQueryParameter("q")
                        if (query != null) AllSetNavKey.Contacts.SearchResults(query) else AllSetNavKey.Contacts.List
                    }
                    "search" -> {
                        val query = uri.getQueryParameter("q") ?: ""
                        AllSetNavKey.Contacts.SearchResults(query)
                    }
                    else -> AllSetNavKey.Contacts.ContactDetails(sub)
                }
            }
            "whatsapp" -> {
                val contactId = segments.getOrNull(0) ?: uri.getQueryParameter("id") ?: ""
                AllSetNavKey.Contacts.SubSection.WhatsApp(contactId)
            }
            "task" -> {
                val taskId = segments.getOrNull(0) ?: uri.getQueryParameter("id") ?: ""
                AllSetNavKey.Tasks.TaskDetails(taskId)
            }
            "tasks" -> {
                val sub = segments.getOrNull(0)
                if (sub == "create") {
                    val cid = uri.getQueryParameter("contactId")
                    AllSetNavKey.Tasks.CreateTask(cid)
                } else if (sub != null) {
                    AllSetNavKey.Tasks.TaskDetails(sub)
                } else {
                    AllSetNavKey.Tasks.List
                }
            }
            "meeting" -> {
                val meetingId = segments.getOrNull(0) ?: uri.getQueryParameter("id") ?: ""
                AllSetNavKey.Meetings.MeetingDetails(meetingId)
            }
            "meetings" -> {
                val sub = segments.getOrNull(0)
                if (sub == "schedule") {
                    val cid = uri.getQueryParameter("contactId")
                    AllSetNavKey.Meetings.ScheduleMeeting(cid)
                } else if (sub != null) {
                    AllSetNavKey.Meetings.MeetingDetails(sub)
                } else {
                    AllSetNavKey.Meetings.List
                }
            }
            "auth" -> {
                when (segments.getOrNull(0)) {
                    "login" -> AllSetNavKey.Auth.Login
                    "register" -> AllSetNavKey.Auth.Register
                    "forgot-password" -> AllSetNavKey.Auth.ForgotPassword
                    "otp" -> {
                        val address = uri.getQueryParameter("address") ?: ""
                        AllSetNavKey.Auth.OtpVerification(address)
                    }
                    else -> AllSetNavKey.Auth.Login
                }
            }
            "home" -> AllSetNavKey.Home
            "settings" -> AllSetNavKey.SettingsTab
            else -> null
        }
    }

    fun parseUrl(url: String): AllSetNavKey? {
        val trimmed = url.trim()
        val schemeSplit = trimmed.split("://")
        if (schemeSplit.size != 2) return null
        val scheme = schemeSplit[0].lowercase()
        if (scheme != "allset" && scheme != "callog" && scheme != "https") return null

        val rest = schemeSplit[1]
        val pathAndQuery = rest.split("?")
        val pathPart = pathAndQuery[0].trim('/')
        val queryPart = pathAndQuery.getOrNull(1)

        val queryParams = mutableMapOf<String, String>()
        if (queryPart != null) {
            queryPart.split("&").forEach { param ->
                val pair = param.split("=")
                if (pair.size == 2) {
                    queryParams[pair[0]] = pair[1]
                }
            }
        }

        val segments = pathPart.split("/").filter { it.isNotEmpty() }
        val host = segments.getOrNull(0)?.lowercase() ?: return null
        val subSegments = segments.drop(1)

        return when (host) {
            "contact" -> {
                val contactId = subSegments.getOrNull(0) ?: queryParams["id"]
                if (contactId != null) AllSetNavKey.Contacts.ContactDetails(contactId) else AllSetNavKey.Contacts.List
            }
            "contacts" -> {
                val sub = subSegments.getOrNull(0)
                when (sub) {
                    null -> {
                        val query = queryParams["q"]
                        if (query != null) AllSetNavKey.Contacts.SearchResults(query) else AllSetNavKey.Contacts.List
                    }
                    "search" -> {
                        val query = queryParams["q"] ?: ""
                        AllSetNavKey.Contacts.SearchResults(query)
                    }
                    else -> AllSetNavKey.Contacts.ContactDetails(sub)
                }
            }
            "whatsapp" -> {
                val contactId = subSegments.getOrNull(0) ?: queryParams["id"] ?: ""
                AllSetNavKey.Contacts.SubSection.WhatsApp(contactId)
            }
            "task" -> {
                val taskId = subSegments.getOrNull(0) ?: queryParams["id"] ?: ""
                AllSetNavKey.Tasks.TaskDetails(taskId)
            }
            "tasks" -> {
                val sub = subSegments.getOrNull(0)
                if (sub == "create") {
                    val cid = queryParams["contactId"]
                    AllSetNavKey.Tasks.CreateTask(cid)
                } else if (sub != null) {
                    AllSetNavKey.Tasks.TaskDetails(sub)
                } else {
                    AllSetNavKey.Tasks.List
                }
            }
            "meeting" -> {
                val meetingId = subSegments.getOrNull(0) ?: queryParams["id"] ?: ""
                AllSetNavKey.Meetings.MeetingDetails(meetingId)
            }
            "meetings" -> {
                val sub = subSegments.getOrNull(0)
                if (sub == "schedule") {
                    val cid = queryParams["contactId"]
                    AllSetNavKey.Meetings.ScheduleMeeting(cid)
                } else if (sub != null) {
                    AllSetNavKey.Meetings.MeetingDetails(sub)
                } else {
                    AllSetNavKey.Meetings.List
                }
            }
            "auth" -> {
                when (subSegments.getOrNull(0)) {
                    "login" -> AllSetNavKey.Auth.Login
                    "register" -> AllSetNavKey.Auth.Register
                    "forgot-password" -> AllSetNavKey.Auth.ForgotPassword
                    "otp" -> {
                        val address = queryParams["address"] ?: ""
                        AllSetNavKey.Auth.OtpVerification(address)
                    }
                    else -> AllSetNavKey.Auth.Login
                }
            }
            "home" -> AllSetNavKey.Home
            "settings" -> AllSetNavKey.SettingsTab
            else -> null
        }
    }
}
