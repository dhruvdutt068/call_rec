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

    suspend fun importRingtoneForLeadHot(sourceUri: Uri): Result<Uri> = runCatching {
        setRingtoneForLeadHot(sourceUri)
        hotLead() ?: sourceUri
    }

    suspend fun importRingtoneForLeadWarm(sourceUri: Uri): Result<Uri> = runCatching {
        setRingtoneForLeadWarm(sourceUri)
        warmLead() ?: sourceUri
    }

    suspend fun importRingtoneForLeadCold(sourceUri: Uri): Result<Uri> = runCatching {
        setRingtoneForLeadCold(sourceUri)
        coldLead() ?: sourceUri
    }

    suspend fun importRingtoneForCustomer(sourceUri: Uri): Result<Uri> = runCatching {
        setRingtoneForCustomer(sourceUri)
        customer() ?: sourceUri
    }

    suspend fun importRingtoneForPerson(personId: String, sourceUri: Uri): Result<Uri> = runCatching {
        setRingtoneForPerson(personId, sourceUri)
        personSpecific(personId) ?: sourceUri
    }

    fun getRingtoneTitle(uri: Uri?): String = if (uri == null) "System Default" else "Custom Audio"
}
