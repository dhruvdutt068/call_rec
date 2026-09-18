package com.example.callog.presentation.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.callog.presentation.screens.auth.ForgotPasswordScreen
import com.example.callog.presentation.screens.auth.LoginScreen
import com.example.callog.presentation.screens.auth.OtpVerificationScreen
import com.example.callog.presentation.screens.auth.RegisterScreen

/**
 * Authentication Navigation 3 Container.
 * Manages the backstack between Login, Register, ForgotPassword, and OtpVerification.
 */
@Composable
fun AuthNav3Container(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNav3BackStack<Nav3Key.Auth>(Nav3Key.Auth.Login)

    Nav3Display(
        backStack = backStack,
        modifier = modifier
    ) { key ->
        when (key) {
            is Nav3Key.Auth.Login -> {
                LoginScreen(
                    onLoginSuccess = onAuthSuccess,
                    onRegisterClick = { backStack.navigate(Nav3Key.Auth.Register) },
                    onForgotPasswordClick = { backStack.navigate(Nav3Key.Auth.ForgotPassword) },
                    onOtpRequested = { address ->
                        backStack.navigate(Nav3Key.Auth.OtpVerification(destinationAddress = address))
                    }
                )
            }
            is Nav3Key.Auth.Register -> {
                RegisterScreen(
                    onRegisterSuccess = onAuthSuccess,
                    onBackClick = { backStack.pop() }
                )
            }
            is Nav3Key.Auth.ForgotPassword -> {
                ForgotPasswordScreen(
                    onResetSent = { backStack.pop() },
                    onBackClick = { backStack.pop() }
                )
            }
            is Nav3Key.Auth.OtpVerification -> {
                OtpVerificationScreen(
                    destinationAddress = key.destinationAddress,
                    onVerified = onAuthSuccess,
                    onBackClick = { backStack.pop() }
                )
            }
            else -> {}
        }
    }
}
