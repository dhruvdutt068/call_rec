package com.example.callog.data.telecom

import android.net.Uri
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.RingtonePolicy
import com.example.callog.domain.repository.RingtoneRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRingtonePolicy @Inject constructor(
    private val ringtoneRepository: RingtoneRepository
) : RingtonePolicy {

    override fun ringtoneFor(person: Person?, lead: Lead?): Uri {
        // Priority 1: Person-specific ringtone override
        if (person != null) {
            val specificUri = ringtoneRepository.personSpecific(person.id)
            if (specificUri != null) {
                return specificUri
            }
        }

        // Priority 2: Lead CRM Status ringtone
        val status = lead?.status ?: when {
            person?.companyName?.isNotBlank() == true -> LeadStatus.CUSTOMER
            else -> LeadStatus.UNKNOWN
        }

        val statusUri = when (status) {
            LeadStatus.HOT -> ringtoneRepository.hotLead()
            LeadStatus.WARM -> ringtoneRepository.warmLead()
            LeadStatus.COLD -> ringtoneRepository.coldLead()
            LeadStatus.NEW -> ringtoneRepository.newLead()
            LeadStatus.CUSTOMER -> ringtoneRepository.customer()
            LeadStatus.UNKNOWN -> null
        }

        if (statusUri != null) {
            return statusUri
        }

        // Priority 3: Fallback system default
        return ringtoneRepository.default()
    }
}
