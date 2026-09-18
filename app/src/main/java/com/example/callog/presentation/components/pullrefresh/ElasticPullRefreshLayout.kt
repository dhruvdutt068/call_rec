package com.example.callog.presentation.components.pullrefresh

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.theme.CallogMotion
import com.example.callog.presentation.theme.CallogShapes
import com.example.callog.presentation.theme.CallogTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * State holder for [ElasticPullRefreshLayout].
 */
@Stable
class ElasticPullRefreshState internal constructor(
    val thresholdPx: Float,
    val refreshingHoldPx: Float,
    val maxPullPx: Float,
    private val onRefreshCallback: () -> Unit
) {
    var rawOffset by mutableFloatStateOf(0f)
        private set

    val offset: Float
        get() = rawOffset

    val progress: Float
        get() = (rawOffset / thresholdPx).coerceIn(0f, 2f)

    val isThresholdReached: Boolean
        get() = rawOffset >= thresholdPx

    internal suspend fun snapTo(value: Float) {
        rawOffset = value
    }

    internal suspend fun animateTo(value: Float, animationSpec: AnimationSpec<Float> = CallogMotion.bouncySpring()) {
        val anim = Animatable(rawOffset)
        anim.animateTo(value, animationSpec) {
            rawOffset = this.value
        }
    }

    internal fun dispatchScroll(delta: Float): Float {
        val newOffset = (rawOffset + delta).coerceIn(0f, maxPullPx)
        val consumed = newOffset - rawOffset
        rawOffset = newOffset
        return consumed
    }

    internal fun triggerRefresh() {
        onRefreshCallback()
    }
}

/**
 * Creates and remembers an [ElasticPullRefreshState].
 */
@Composable
fun rememberElasticPullRefreshState(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    refreshThreshold: Dp = 80.dp,
    refreshingHoldOffset: Dp = 64.dp,
    maxPullDistance: Dp = 150.dp
): ElasticPullRefreshState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { refreshThreshold.toPx() }
    val refreshingHoldPx = with(density) { refreshingHoldOffset.toPx() }
    val maxPullPx = with(density) { maxPullDistance.toPx() }

    val state = remember(thresholdPx, refreshingHoldPx, maxPullPx) {
        ElasticPullRefreshState(
            thresholdPx = thresholdPx,
            refreshingHoldPx = refreshingHoldPx,
            maxPullPx = maxPullPx,
            onRefreshCallback = onRefresh
        )
    }

    val coroutineScope = rememberCoroutineScope()

    // React to external isRefreshing changes
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            state.animateTo(refreshingHoldPx, CallogMotion.snappySpring())
        } else {
            state.animateTo(0f, CallogMotion.bouncySpring())
        }
    }

    return state
}

/**
 * A fluid, elastic Pull-to-Refresh container with:
 * 1. Pinned or push-down Top App Bar
 * 2. Rotating -> spinning refresh indicator positioned above/inside the pull gap
 * 3. Rubber-band overscroll content stretch
 * 4. Animated "Updated just now" completion banner on refresh finish
 */
@Composable
fun ElasticPullRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    statusMessage: String = "Updated just now",
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val state = rememberElasticPullRefreshState(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    )

    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    var showUpdatedBanner by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }

    // Detect refresh completion to show transient "Updated just now" badge
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            showUpdatedBanner = true
            delay(2500)
            showUpdatedBanner = false
        }
        wasRefreshing = isRefreshing
    }

    val nestedScrollConnection = remember(state, isRefreshing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user scrolls up while pulled down, consume delta to collapse pull offset
                return if (available.y < 0 && state.rawOffset > 0f) {
                    val consumed = state.dispatchScroll(available.y)
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isRefreshing) return Offset.Zero

                // If user drags down when at top of scroll
                return if (available.y > 0) {
                    // Damping factor: parabolic resistance as pull gets deeper
                    val pullFraction = (state.rawOffset / state.maxPullPx).coerceIn(0f, 1f)
                    val damping = (1f - pullFraction * 0.7f).coerceIn(0.2f, 1f)
                    val delta = available.y * damping * 0.6f

                    val consumedY = state.dispatchScroll(delta)

                    if (state.isThresholdReached && !hasTriggeredHaptic) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        hasTriggeredHaptic = true
                    } else if (!state.isThresholdReached) {
                        hasTriggeredHaptic = false
                    }

                    Offset(0f, consumedY)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (state.rawOffset > 0f) {
                    if (state.isThresholdReached && !isRefreshing) {
                        state.triggerRefresh()
                    } else if (!isRefreshing) {
                        state.animateTo(0f, CallogMotion.bouncySpring())
                    }
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. Pull Refresh Indicator (Positioned centered above the app bar)
        val indicatorOffset = (state.offset * 0.5f) - with(density) { 36.dp.toPx() }
        if (state.offset > 5f || isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, indicatorOffset.roundToInt().coerceAtLeast(16)) }
                    .zIndex(10f),
                contentAlignment = Alignment.TopCenter
            ) {
                PullRefreshIndicator(
                    progress = state.progress,
                    isRefreshing = isRefreshing
                )
            }
        }

        // 2. Main Column containing Top Bar + Stretchable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // App Bar & list move naturally down with rubber-band translation
                    translationY = state.offset
                    // Subtle elastic stretch on the vertical axis
                    val stretchScale = 1f + (state.offset / 2400f).coerceAtMost(0.04f)
                    scaleY = stretchScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
        ) {
            // Top App Bar
            topBar()

            // 3. Transient "Updated just now" completion banner
            AnimatedVisibility(
                visible = showUpdatedBanner,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CallogShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = statusMessage,
                                style = CallogTypography.statusLabel,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Screen Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                content()
            }
        }
    }
}
