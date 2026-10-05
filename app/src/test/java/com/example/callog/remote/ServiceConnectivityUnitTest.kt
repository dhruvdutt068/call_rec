package com.example.callog.remote

import android.net.Uri
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.remote.FirestoreService
import com.example.callog.data.telecom.DefaultRingtonePolicy
import com.example.callog.domain.call.CallRecordingState
import com.example.callog.domain.call.RecordingSession
import com.example.callog.domain.model.Lead
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.Person
import com.example.callog.domain.repository.RingtoneRepository
import org.junit.Assert.*
import org.junit.Test
import android.net.FakeUri

class ServiceConnectivityUnitTest {

    private fun createDummyUri(identifier: String): Uri {
        return FakeUri("content://callog/ringtone/$identifier")
    }

    @Test
    fun testNormalizeStorageBucketWithDevBucket() {
        val rawBucket = "gs://allset_calllogs_bucket"
        val normalized = FirestoreService.normalizeStorageBucket(rawBucket, "allset-491218")
        assertEquals("allset_calllogs_bucket", normalized)
    }

    @Test
    fun testNormalizeStorageBucketWithProdFirebaseStorageUrl() {
        val rawUrl = "https://firebasestorage.googleapis.com/v0/b/allset-244ab.firebasestorage.app/o/"
        val normalized = FirestoreService.normalizeStorageBucket(rawUrl, "allset-244ab")
        assertEquals("allset-244ab.firebasestorage.app", normalized)
    }

    @Test
    fun testNormalizeStorageBucketFallbackToProjectId() {
        val normalized = FirestoreService.normalizeStorageBucket(null, "allset-491218")
        assertEquals("allset-491218.firebasestorage.app", normalized)
    }

    @Test
    fun testPhoneNumberNormalizerStandardizesIndianNumbers() {
        assertEquals("9876543210", PhoneNumberNormalizer.normalize("+91 98765 43210"))
        assertEquals("9876543210", PhoneNumberNormalizer.normalize("09876543210"))
        assertEquals("9876543210", PhoneNumberNormalizer.normalize("9876543210"))
    }

    @Test
    fun testRingtonePolicyPrioritizesPersonSpecificOverLeadStatus() {
        val personUri = createDummyUri("person_vip")
        val hotUri = createDummyUri("lead_hot")
        val defaultUri = createDummyUri("system_default")

        val fakeRepo = object : RingtoneRepository {
            override fun hotLead(): Uri = hotUri
            override fun warmLead(): Uri? = null
            override fun coldLead(): Uri? = null
            override fun newLead(): Uri? = null
            override fun customer(): Uri? = null
            override fun personSpecific(personId: String): Uri? {
                return if (personId == "P_VIP") personUri else null
            }
            override fun default(): Uri = defaultUri
            override fun setRingtoneForLeadHot(uri: Uri?) {}
            override fun setRingtoneForLeadWarm(uri: Uri?) {}
            override fun setRingtoneForLeadCold(uri: Uri?) {}
            override fun setRingtoneForCustomer(uri: Uri?) {}
            override fun setRingtoneForPerson(personId: String, uri: Uri?) {}
            override suspend fun importRingtoneForLeadHot(sourceUri: Uri): Result<Uri> = Result.success(sourceUri)
            override suspend fun importRingtoneForLeadWarm(sourceUri: Uri): Result<Uri> = Result.success(sourceUri)
            override suspend fun importRingtoneForLeadCold(sourceUri: Uri): Result<Uri> = Result.success(sourceUri)
            override suspend fun importRingtoneForCustomer(sourceUri: Uri): Result<Uri> = Result.success(sourceUri)
            override suspend fun importRingtoneForPerson(personId: String, sourceUri: Uri): Result<Uri> = Result.success(sourceUri)
        }

        val policy = DefaultRingtonePolicy(fakeRepo)

        val vipPerson = Person(id = "P_VIP", displayName = "Important Client", phoneNumbers = emptyList())
        val hotLead = Lead(id = "L1", personId = "P_VIP", status = LeadStatus.HOT)

        // 1. Person specific override must win over Hot Lead status
        val resolved = policy.ringtoneFor(vipPerson, hotLead)
        assertEquals(personUri.toString(), resolved.toString())

        // 2. Normal person with Hot Lead must get hotUri
        val regularPerson = Person(id = "P_REGULAR", displayName = "Regular Prospect", phoneNumbers = emptyList())
        val resolvedHot = policy.ringtoneFor(regularPerson, hotLead)
        assertEquals(hotUri.toString(), resolvedHot.toString())

        // 3. Unknown person and lead must get defaultUri
        val resolvedDefault = policy.ringtoneFor(null, null)
        assertEquals(defaultUri.toString(), resolvedDefault.toString())
    }

    @Test
    fun testTwoWayCallRecordingSessionMetadata() {
        val session = RecordingSession(
            recordingId = "rec_voice_call_001",
            callId = "call_sim_42",
            phoneNumber = "+919876543210",
            contactName = "Sarah Connor",
            filePath = "/storage/emulated/0/Recordings/Callog/Callog_Sarah_Connor_20261004.m4a",
            startedAt = System.currentTimeMillis(),
            durationMillis = 42000L,
            audioSource = 4, // MediaRecorder.AudioSource.VOICE_CALL
            audioSourceName = "VOICE_CALL (Direct 2-Way)"
        )

        assertEquals(4, session.audioSource)
        assertEquals("VOICE_CALL (Direct 2-Way)", session.audioSourceName)

        val saved = CallRecordingState.Saved(
            recordingId = session.recordingId,
            filePath = session.filePath,
            durationMillis = session.durationMillis,
            audioSource = session.audioSource,
            audioSourceName = session.audioSourceName
        )

        assertEquals("VOICE_CALL (Direct 2-Way)", saved.audioSourceName)
        assertEquals(42000L, saved.durationMillis)
    }
}
