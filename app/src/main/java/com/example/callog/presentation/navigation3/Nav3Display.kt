package com.example.callog.presentation.navigation3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Declarative Navigation 3 Display composable.
 * Observes the current key of the [backStack] and renders it using the provided [contentResolver].
 * Automatically intercepts back presses via [BackHandler] when the stack contains multiple entries.
 */
@Composable
fun <T : Any> Nav3Display(
    backStack: Nav3BackStack<T>,
    modifier: Modifier = Modifier,
    enableBackHandler: Boolean = true,
    onStackEmptyBack: (() -> Unit)? = null,
    contentResolver: @Composable (key: T) -> Unit
) {
    if (enableBackHandler) {
        BackHandler(enabled = true) {
            val popped = backStack.pop()
            if (!popped) {
                onStackEmptyBack?.invoke()
            }
        }
    }

    val currentKey = backStack.currentKey ?: return

    AnimatedContent(
        targetState = currentKey,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { fullWidth -> fullWidth / 4 }
            )).togetherWith(
                fadeOut(animationSpec = tween(180)) + slideOutHorizontally(
                    animationSpec = tween(180),
                    targetOffsetX = { fullWidth -> -fullWidth / 4 }
                )
            )
        },
        label = "Nav3DisplayTransition",
        modifier = modifier
    ) { key ->
        Box(modifier = Modifier.fillMaxSize()) {
            contentResolver(key)
        }
    }
}
