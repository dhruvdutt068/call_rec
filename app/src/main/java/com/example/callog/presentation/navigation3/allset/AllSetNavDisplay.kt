package com.example.callog.presentation.navigation3.allset

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Navigation 3 Display composable for AllSet.
 * Uses decoupled entry rendering and handles back events.
 */
@Composable
fun AllSetNavDisplay(
    backStack: AllSetBackStack<AllSetNavKey>,
    modifier: Modifier = Modifier,
    enableBackHandler: Boolean = true,
    onStackEmptyBack: (() -> Unit)? = null,
    entryProvider: @Composable (key: AllSetNavKey) -> Unit
) {
    if (enableBackHandler) {
        BackHandler(enabled = backStack.canPop) {
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
        label = "AllSetNav3DisplayTransition",
        modifier = modifier
    ) { key ->
        Box(modifier = Modifier.fillMaxSize()) {
            entryProvider(key)
        }
    }
}
