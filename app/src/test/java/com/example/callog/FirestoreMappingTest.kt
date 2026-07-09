package com.example.callog
 
import com.example.callog.data.repository.FirestoreRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Test
 
class FirestoreMappingTest {
 
    @Test
    fun testNormalizePhoneNumber() {
        // Strip plus, spaces, dashes, parentheses
        assertEquals("919876543210", FirestoreRepositoryImpl.normalizePhoneNumber("+91 98765 43210"))
        assertEquals("919876543210", FirestoreRepositoryImpl.normalizePhoneNumber("+919876543210"))
        assertEquals("919876543210", FirestoreRepositoryImpl.normalizePhoneNumber("(987) 654-3210"))
        assertEquals("919876543210", FirestoreRepositoryImpl.normalizePhoneNumber("9876543210"))
        assertEquals("919876543210", FirestoreRepositoryImpl.normalizePhoneNumber("09876543210"))
        
        // Edge cases
        assertEquals("unknown", FirestoreRepositoryImpl.normalizePhoneNumber(""))
        assertEquals("unknown", FirestoreRepositoryImpl.normalizePhoneNumber("   "))
        assertEquals("123", FirestoreRepositoryImpl.normalizePhoneNumber("++1-(2)-3"))
    }
 
    @Test
    fun testGetFirestoreCallType() {
        // Mapped types
        assertEquals("incoming", FirestoreRepositoryImpl.getFirestoreCallType("INCOMING"))
        assertEquals("incoming", FirestoreRepositoryImpl.getFirestoreCallType("incoming"))
        
        assertEquals("outgoing", FirestoreRepositoryImpl.getFirestoreCallType("OUTGOING"))
        assertEquals("outgoing", FirestoreRepositoryImpl.getFirestoreCallType("outgoing"))
        
        assertEquals("missed call", FirestoreRepositoryImpl.getFirestoreCallType("MISSED"))
        assertEquals("missed call", FirestoreRepositoryImpl.getFirestoreCallType("missed"))
        
        // Not connected types
        assertEquals("not connected", FirestoreRepositoryImpl.getFirestoreCallType("REJECTED"))
        assertEquals("not connected", FirestoreRepositoryImpl.getFirestoreCallType("BLOCKED"))
        assertEquals("not connected", FirestoreRepositoryImpl.getFirestoreCallType("UNKNOWN"))
        assertEquals("not connected", FirestoreRepositoryImpl.getFirestoreCallType("FAILED"))
    }
}
