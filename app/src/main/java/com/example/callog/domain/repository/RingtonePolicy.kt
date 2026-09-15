package com.example.callog.domain.repository

import android.net.Uri
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.Person

/**
 * Policy interface for deciding the ringtone URI based on Person and Lead CRM state.
 */
interface RingtonePolicy {
    fun ringtoneFor(
        person: Person?,
        lead: Lead? = null
    ): Uri
}
