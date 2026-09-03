package com.example.callog.navigation3

import com.example.callog.presentation.navigation3.allset.AllSetAuthState
import com.example.callog.presentation.navigation3.allset.AllSetBackStack
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import org.junit.Assert.*
import org.junit.Test

class AllSetAuthRoutingTest {

    @Test
    fun testUnauthenticatedStatePresentsAuthFlow() {
        var authState: AllSetAuthState = AllSetAuthState.Unauthenticated
        val authStack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Auth.Login))

        assertEquals(AllSetNavKey.Auth.Login, authStack.currentKey)

        authStack.navigate(AllSetNavKey.Auth.Register)
        assertEquals(AllSetNavKey.Auth.Register, authStack.currentKey)

        authStack.navigate(AllSetNavKey.Auth.OtpVerification("+15551234"))
        assertEquals(AllSetNavKey.Auth.OtpVerification("+15551234"), authStack.currentKey)

        // On successful authentication
        authState = AllSetAuthState.Authenticated(userId = "usr_100", userEmail = "agent@allset.crm")
        assertTrue(authState is AllSetAuthState.Authenticated)
        assertEquals("usr_100", (authState as AllSetAuthState.Authenticated).userId)
    }

    @Test
    fun testAuthBackNavigation() {
        val authStack = AllSetBackStack<AllSetNavKey>(listOf(AllSetNavKey.Auth.Login))
        authStack.navigate(AllSetNavKey.Auth.ForgotPassword)
        assertTrue(authStack.canPop)

        authStack.pop()
        assertEquals(AllSetNavKey.Auth.Login, authStack.currentKey)
        assertFalse(authStack.canPop)
    }
}
