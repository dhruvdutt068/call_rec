package com.example.callog.data.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactDto(
    val contactId: String,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>,
    val photoUri: String?,
    val isFavorite: Boolean
)

@Singleton
class ContactsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cachedContacts: List<ContactDto>? = null
    private var lastFetchTime = 0L
    private val cacheDuration = 60_000L // 60 seconds cache duration

    @Synchronized
    fun fetchContacts(): List<ContactDto> {
        val now = System.currentTimeMillis()
        cachedContacts?.let {
            if (now - lastFetchTime < cacheDuration) {
                return it
            }
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val contactsMap = mutableMapOf<String, TempContact>()
        
        // 1. Fetch Names, Phone Numbers, Photos, and Favorites
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        context.contentResolver.query(
            phoneUri,
            phoneProjection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val starredIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

            while (c.moveToNext()) {
                val contactId = if (idIdx != -1) c.getString(idIdx) ?: "" else ""
                val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Unknown" else "Unknown"
                val number = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                val photoUri = if (photoIdx != -1) c.getString(photoIdx) else null
                val isStarred = if (starredIdx != -1) c.getInt(starredIdx) == 1 else false

                if (contactId.isNotEmpty()) {
                    val temp = contactsMap.getOrPut(contactId) {
                        TempContact(
                            contactId = contactId,
                            name = name,
                            phoneNumbers = mutableSetOf<String>(),
                            emails = mutableSetOf<String>(),
                            photoUri = photoUri,
                            isFavorite = isStarred
                        )
                    }
                    if (number.isNotEmpty()) {
                        temp.phoneNumbers.add(number)
                    }
                }
            }
        }

        // 2. Fetch Emails
        val emailUri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        val emailProjection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )

        context.contentResolver.query(
            emailUri,
            emailProjection,
            null,
            null,
            null
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (c.moveToNext()) {
                val contactId = if (idIdx != -1) c.getString(idIdx) ?: "" else ""
                val email = if (emailIdx != -1) c.getString(emailIdx) ?: "" else ""

                if (contactId.isNotEmpty() && email.isNotEmpty()) {
                    contactsMap[contactId]?.emails?.add(email)
                }
            }
        }

        val result = contactsMap.values.map {
            ContactDto(
                contactId = it.contactId,
                name = it.name,
                phoneNumbers = it.phoneNumbers.toList(),
                emails = it.emails.toList(),
                photoUri = it.photoUri,
                isFavorite = it.isFavorite
            )
        }
        
        cachedContacts = result
        lastFetchTime = now
        return result
    }

    private data class TempContact(
        val contactId: String,
        val name: String,
        val phoneNumbers: MutableSet<String>,
        val emails: MutableSet<String>,
        val photoUri: String?,
        val isFavorite: Boolean
    )
}
