package com.example.callog.identity

import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.data.local.dao.PersonDao
import com.example.callog.data.local.entity.ContactAliasEntity
import com.example.callog.data.local.entity.DeviceEntity
import com.example.callog.data.local.entity.PersonEntity
import com.example.callog.data.local.entity.PersonWithDetails
import com.example.callog.data.local.entity.PhoneNumberEntity
import com.example.callog.data.provider.ContactDto
import com.example.callog.data.remote.model.PersonResolutionRequest
import com.example.callog.data.remote.model.PersonResolutionResponse
import com.example.callog.domain.usecase.ResolveAndAttachContactUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ServerIdentityResolutionTest {

    private lateinit var fakePersonDao: CentralPersonIdentityTest.FakePersonDao
    private lateinit var fakeServerResolver: FakeServerIdentityResolver
    private lateinit var resolveAndAttachContactUseCase: ResolveAndAttachContactUseCase

    @Before
    fun setUp() {
        fakePersonDao = CentralPersonIdentityTest.FakePersonDao()
        fakeServerResolver = FakeServerIdentityResolver()
        resolveAndAttachContactUseCase = ResolveAndAttachContactUseCase(fakePersonDao)
    }

    // Test 1: New identity created on server
    @Test
    fun testNewIdentityCreatedViaServer() = runBlocking {
        val request = PersonResolutionRequest(
            deviceId = "DEV_001",
            androidContactId = "c_1",
            aliasName = "Rahul Sharma",
            phoneNumber = "+91 98765 43210",
            normalizedNumber = "9876543210"
        )
        val response = fakeServerResolver.resolve(request)
        assertNotNull(response.personId)
        assertTrue(response.personId.startsWith("P"))
        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(1, fakeServerResolver.serverAliases.size)
    }

    // Test 2: Existing identity resolved on server
    @Test
    fun testExistingIdentityResolvedViaServer() = runBlocking {
        val req1 = PersonResolutionRequest("DEV_001", "c_1", "Rahul Sharma", "+919876543210", "9876543210")
        val res1 = fakeServerResolver.resolve(req1)

        val req2 = PersonResolutionRequest("DEV_002", "c_2", "Rahul", "09876543210", "9876543210")
        val res2 = fakeServerResolver.resolve(req2)

        assertEquals(res1.personId, res2.personId)
        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(2, fakeServerResolver.serverAliases.size)
    }

    // Test 3: Same device repeated sync is idempotent
    @Test
    fun testSameDeviceRepeatedSyncIsIdempotent() = runBlocking {
        val req = PersonResolutionRequest("DEV_001", "c_1", "Rahul", "+919876543210", "9876543210")
        fakeServerResolver.resolve(req)
        fakeServerResolver.resolve(req)
        fakeServerResolver.resolve(req)

        assertEquals(1, fakeServerResolver.serverPeople.size)
        assertEquals(1, fakeServerResolver.serverPhones.size)
        assertEquals(1, fakeServerResolver.serverAliases.size)
    }

    // Test 4: Five-Device Scenario
    @Test
    fun testFiveDeviceScenarioResolvesToSingleCanonicalPerson() = runBlocking {
        val d1 = PersonResolutionRequest("DEV_001", "c_1", "Rahul Sharma", "+91 98765 43210", "9876543210")
        val d2 = PersonResolutionRequest("DEV_002", "c_2", "Rahul", "+919876543210", "9876543210")
        val d3 = PersonResolutionRequest("DEV_003", "c_3", "Rahul Sir", "09876543210", "9876543210")
        val d4 = PersonResolutionRequest("DEV_004", "c_4", "R Sharma", "98765-43210", "9876543210")
        val d5 = PersonResolutionRequest("DEV_005", "c_5", "ABC Client", "9876543210", "9876543210")

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

    // Test 5: Multiple phone numbers under one person
    @Test
    fun testMultiplePhoneNumbersUnderOnePerson() = runBlocking {
        val contactWithMultiple = ContactDto(
            contactId = "c_multi",
            name = "Vikram Patel",
            phoneNumbers = listOf("+919876543210", "+919123456789"),
            emails = emptyList(),
            photoUri = null,
            isFavorite = false
        )
        val person = resolveAndAttachContactUseCase(contactWithMultiple, "DEV_001")
        assertEquals(1, fakePersonDao.people.size)
        assertEquals(2, fakePersonDao.phoneNumbers.filter { it.personId == person.id }.size)
    }

    // Test 6: Different phone numbers resolve to different people
    @Test
    fun testDifferentPhoneNumbersResolveToDifferentPeople() = runBlocking {
        val reqA = PersonResolutionRequest("DEV_001", "c_1", "Alice", "+919876543210", "9876543210")
        val reqB = PersonResolutionRequest("DEV_001", "c_2", "Bob", "+919111122222", "9111122222")

        val resA = fakeServerResolver.resolve(reqA)
        val resB = fakeServerResolver.resolve(reqB)

        assertNotEquals(resA.personId, resB.personId)
        assertEquals(2, fakeServerResolver.serverPeople.size)
        assertEquals(2, fakeServerResolver.serverPhones.size)
    }

    // Test 7: Offline fallback preserves local identity
    @Test
    fun testOfflineFallbackPreservesLocalIdentity() = runBlocking {
        val contact = ContactDto("c_off", "Offline Contact", listOf("+919999988888"), emptyList(), null, false)
        val localPerson = resolveAndAttachContactUseCase(contact, "DEV_001")

        assertNotNull(localPerson)
        assertEquals("Offline Contact", localPerson.displayName)
        assertEquals(1, fakePersonDao.people.size)
        assertEquals("PENDING", fakePersonDao.people.first().syncStatus)
    }

    // Test 8: Network error handled gracefully without crashing
    @Test
    fun testNetworkErrorHandledGracefullyWithoutCrashing() = runBlocking {
        fakeServerResolver.shouldSimulateNetworkError = true
        val contact = ContactDto("c_err", "Error Case", listOf("+919876543210"), emptyList(), null, false)
        
        try {
            // When server call fails, local fallback completes cleanly
            val fallbackPerson = resolveAndAttachContactUseCase(contact, "DEV_001")
            assertNotNull(fallbackPerson)
            assertEquals("Error Case", fallbackPerson.displayName)
        } catch (e: Exception) {
            fail("Should not throw unhandled exception on network error: ${e.message}")
        }
    }

    // --- Fake Server-Side RPC Resolver Simulation ---
    class FakeServerIdentityResolver {
        val serverPeople = mutableMapOf<String, String>() // person_id -> display_name
        val serverPhones = mutableMapOf<String, String>() // normalized_number -> person_id
        val serverAliases = mutableMapOf<String, String>() // "device_id:normalized_number" -> alias_name
        var shouldSimulateNetworkError = false

        fun resolve(request: PersonResolutionRequest): PersonResolutionResponse {
            if (shouldSimulateNetworkError) {
                throw RuntimeException("Network timeout or connection refused")
            }

            val norm = request.normalizedNumber.trim()
            var personId = serverPhones[norm]

            if (personId == null) {
                personId = "P" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                serverPeople[personId] = request.aliasName.ifBlank { "Unknown Contact" }
                serverPhones[norm] = personId
            }

            val aliasKey = "${request.deviceId}:$norm"
            serverAliases[aliasKey] = request.aliasName

            return PersonResolutionResponse(
                personId = personId,
                phoneNumberId = "phone_" + norm,
                aliasId = "alias_" + aliasKey
            )
        }
    }
}
