package com.example.callog.domain.repository

import android.net.Uri

interface RingtoneRepository {
    fun hotLead(): Uri?
    fun warmLead(): Uri?
    fun coldLead(): Uri?
    fun newLead(): Uri?
    fun customer(): Uri?
    fun personSpecific(personId: String): Uri?
    fun default(): Uri
    fun setRingtoneForPerson(personId: String, uri: Uri?)
    fun setRingtoneForLeadHot(uri: Uri?)
    fun setRingtoneForLeadWarm(uri: Uri?)
    fun setRingtoneForLeadCold(uri: Uri?)
    fun setRingtoneForCustomer(uri: Uri?)
}
