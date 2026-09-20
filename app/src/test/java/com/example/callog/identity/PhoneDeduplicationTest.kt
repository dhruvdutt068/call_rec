package com.example.callog.identity

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.DeviceEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.remote.model.PersonResolutionRequest
import com.example.callog.data.remote.model.PersonResolutionResponse
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PhoneDeduplicationTest {

    private lateinit var fakePersonDao: CentralPersonIdentityTest.FakePersonDao
    private lateinit var fakeServerResolver: ServerIdentityResolutionTest.FakeServerIdentityResolver
    private lateinit var resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase

    @Before
    fun setUp() {
        fakePersonDao = CentralPersonIdentityTest.FakePersonDao()
        fakeServerResolver = ServerIdentityResolutionTest.FakeServerIdentityResolver()
        resolveAndAttachContactUseCase = ResolveAndAttachContactUseCase(fakePersonDao)
    }

    // 1. Same number in different formatting (+91, spaces, dashes, parentheses, leading 0)
    @Test
    fun testSameNumberInDifferentFormattingResolvesToSameCanonicalNumber() {
        val raw1 = "+91 63513 24531"
        val raw2 = "+916351324531"
        val raw3 = "916351324531"
        val raw4 = "06351324531"
        val raw5 = "6351324531"
        val raw6 = "(635) 132-4531"
        val raw7 = "+91-63513-24531"

        val norm1 = PhoneNumberNormalizer.normalize(raw1)
        val norm2 = PhoneNumberNormalizer.normalize(raw2)
        val norm3 = PhoneNumberNormalizer.normalize(raw3)
        val norm4 = PhoneNumberNormalizer.normalize(raw4)
        val norm5 = PhoneNumberNormalizer.normalize(raw5)
        val norm6 = PhoneNumberNormalizer.normalize(raw6)
        val norm7 = PhoneNumberNormalizer.normalize(raw7)

        val expected = "6351324531"
        assertEquals(expected, norm1)
        assertEquals(expected, norm2)
        assertEquals(expected, norm3)
        assertEquals(expected, norm4)
        assertEquals(expected, norm5)
        assertEquals(expected, norm6)
        assertEquals(expected, norm7)
    }

    // 2. +91 vs local representation in server resolution
    @Test
    fun testPlus91VsLocalRepresentationResolvesToSamePerson() = runBlocking {
        val reqPlus91 = PersonResolutionRequest("DEV_1", "c1", "Client A", "+916351324531", PhoneNumberNormalizer.normalize("+916351324531"))
        val reqLocal = PersonResolutionRequest("DEV_2", "c2", "Client A Local", "6351324531", PhoneNumberNormalizer.normalize("6351324531"))

        val res1 = fakeServerResolver.resolve(reqPlus91)
        val res2 = fakeServerResolver.resolve(reqLocal)

        assertEquals(res1.personId, res2.personId)
        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
    }

    // 3. 5-Device Acceptance Test
    // D001 → Rahul Sharma → 6351324531
    // D002 → Rahul        → 6351324531
    // D003 → Rahul Sir    → 6351324531
    // D004 → R Sharma     → 6351324531
    // D005 → ABC Client   → 6351324531
    // Expected: people = 1, phone_numbers = 1, contact_aliases = 5
    @Test
    fun testFiveDeviceAcceptanceScenario() = runBlocking {
        val d1 = PersonResolutionRequest("D001", "c1", "Rahul Sharma", "6351324531", "6351324531")
        val d2 = PersonResolutionRequest("D002", "c2", "Rahul", "+916351324531", "6351324531")
        val d3 = PersonResolutionRequest("D003", "c3", "Rahul Sir", "06351324531", "6351324531")
        val d4 = PersonResolutionRequest("D004", "c4", "R Sharma", "+91 63513 24531", "6351324531")
        val d5 = PersonResolutionRequest("D005", "c5", "ABC Client", "63513-24531", "6351324531")

        val r1 = fakeServerResolver.resolve(d1)
        val r2 = fakeServerResolver.resolve(d2)
        val r3 = fakeServerResolver.resolve(d3)
        val r4 = fakeServerResolver.resolve(d4)
        val r5 = fakeServerResolver.resolve(d5)

        assertEquals(r1.personId, r2.personId)
        assertEquals(r1.personId, r3.personId)
        assertEquals(r1.personId, r4.personId)
        assertEquals(r1.personId, r5.personId)

        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(5, fakeServerResolver.serverAliases.size)
    }

    // 4. Repeated Synchronization (100 times)
    // Synchronize the same contact 100 times.
    // Expected: people increase = 0 after first creation, phone_numbers increase = 0, alias increase = 0
    @Test
    fun testRepeatedSync100TimesProducesNoDuplicates() = runBlocking {
        val req = PersonResolutionRequest("D001", "c100", "Repeated Contact", "+916351324531", "6351324531")
        
        var firstPersonId: String? = null
        for (i in 1..100) {
            val res = fakeServerResolver.resolve(req)
            if (firstPersonId == null) {
                firstPersonId = res.personId
            } else {
                assertEquals(firstPersonId, res.personId)
            }
        }

        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(1, fakeServerResolver.serverAliases.size)
    }

    // 5. Local Room Deduplication: 100 repeated local resolutions
    @Test
    fun testLocalRoomRepeatedSync100TimesProducesNoDuplicates() = runBlocking {
        val contact = ContactDto(
            contactId = "c_local_1",
            name = "Local Test",
            phoneNumbers = listOf("+91 63513 24531"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )

        var firstId: String? = null
        for (i in 1..100) {
            val person = resolveAndAttachContactUseCase(contact, "DEV_001")
            if (firstId == null) {
                firstId = person.id
            } else {
                assertEquals(firstId, person.id)
            }
        }

        assertEquals(1, fakePersonDao.people.size)
        assertEquals(1, fakePersonDao.phoneNumbers.size)
        assertEquals(1, fakePersonDao.aliases.size)
    }

    // 6. Concurrent Identity Resolution Simulation
    @Test
    fun testConcurrentIdentityResolution() = runBlocking {
        val reqA = PersonResolutionRequest("D001", "c1", "Concurrent A", "+919876543210", "9876543210")
        val reqB = PersonResolutionRequest("D002", "c2", "Concurrent B", "9876543210", "9876543210")

        val deferredA = async(Dispatchers.Default) { fakeServerResolver.resolve(reqA) }
        val deferredB = async(Dispatchers.Default) { fakeServerResolver.resolve(reqB) }

        val resA = deferredA.await()
        val resB = deferredB.await()

        assertEquals(resA.personId, resB.personId)
        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(2, fakeServerResolver.serverAliases.size)
    }

    // 7. Edge cases: Malformed / Short / Blank / Null Numbers
    @Test
    fun testEdgeCasePhoneNumbers() {
        assertEquals("", PhoneNumberNormalizer.normalize(null))
        assertEquals("", PhoneNumberNormalizer.normalize(""))
        assertEquals("", PhoneNumberNormalizer.normalize("   "))
        assertEquals("", PhoneNumberNormalizer.normalize("abc-def"))
        assertEquals("100", PhoneNumberNormalizer.normalize("100"))
        assertEquals("911", PhoneNumberNormalizer.normalize("911"))
    }

    // 8. Masking for secure diagnostics logging
    @Test
    fun testPhoneNumberMasking() {
        assertEquals("******4531", PhoneNumberNormalizer.mask("+916351324531"))
        assertEquals("******4531", PhoneNumberNormalizer.mask("6351324531"))
        assertEquals("****", PhoneNumberNormalizer.mask("1234"))
        assertEquals("<blank>", PhoneNumberNormalizer.mask(null))
        assertEquals("<blank>", PhoneNumberNormalizer.mask(""))
    }
}
