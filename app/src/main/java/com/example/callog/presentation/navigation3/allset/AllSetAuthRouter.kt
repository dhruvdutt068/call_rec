package com.example.callog.presentation.navigation3.allset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/**
 * Authentication state model for state-driven conditional routing.
 * Keeps authentication status decoupled from individual screens.
 */
@Immutable
sealed interface AllSetAuthState {
    data object Unauthenticated : AllSetAuthState
    data class Authenticated(val userId: String, val userEmail: String? = null) : AllSetAuthState
}

/**
 * Conditional Authentication Router for AllSet Navigation 3.
 * Automatically presents Auth flows when unauthenticated, and Main CRM flows when authenticated.
 */
@Composable
fun AllSetAuthHost(
    authState: AllSetAuthState,
    onLoginSuccess: (userId: String) -> Unit,
    onLogout: () -> Unit,
    crmContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    when (authState) {
        is AllSetAuthState.Unauthenticated -> {
            val authBackStack = rememberAllSetBackStack<AllSetNavKey>(AllSetNavKey.Auth.Login)
            AllSetNavDisplay(
                backStack = authBackStack,
                modifier = modifier
            ) { key ->
                when (key) {
                    is AllSetNavKey.Auth.Login -> {
                        com.example.callog.presentation.screens.auth.LoginScreen(
                            onLoginSuccess = { onLoginSuccess("user_default") },
                            onRegisterClick = { authBackStack.navigate(AllSetNavKey.Auth.Register) },
                            onForgotPasswordClick = { authBackStack.navigate(AllSetNavKey.Auth.ForgotPassword) },
                            onOtpRequested = { address ->
                                authBackStack.navigate(AllSetNavKey.Auth.OtpVerification(address))
                            }
                        )
                    }
                    is AllSetNavKey.Auth.Register -> {
                        com.example.callog.presentation.screens.auth.RegisterScreen(
                            onRegisterSuccess = { onLoginSuccess("user_new") },
                            onBackClick = { authBackStack.pop() }
                        )
                    }
                    is AllSetNavKey.Auth.ForgotPassword -> {
                        com.example.callog.presentation.screens.auth.ForgotPasswordScreen(
                            onResetSent = { authBackStack.pop() },
                            onBackClick = { authBackStack.pop() }
                        )
                    }
                    is AllSetNavKey.Auth.OtpVerification -> {
                        com.example.callog.presentation.screens.auth.OtpVerificationScreen(
                            destinationAddress = key.destinationAddress,
                            onVerified = { onLoginSuccess("user_otp") },
                            onBackClick = { authBackStack.pop() }
                        )
                    }
                    else -> {}
                }
            }
        }
        is AllSetAuthState.Authenticated -> {
            crmContent()
        }
    }
}
