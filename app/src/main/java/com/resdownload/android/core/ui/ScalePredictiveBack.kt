package com.resdownload.android.core.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val SCALE_BACK_TARGET_SCALE = 0.85f
private const val SCALE_BACK_DIM_ALPHA = 0.5f
private const val SCALE_BACK_EXIT_MILLIS = 200
private const val SCALE_BACK_SETTLE_MILLIS = 300
private const val MAX_CONTENT_CHANGE_FRAMES = 10

internal fun scaleBackScale(progress: Float): Float {
    val easedProgress = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    return 1f - ((1f - SCALE_BACK_TARGET_SCALE) * easedProgress)
}

internal fun scaleBackPivotX(swipeEdge: Int): Float =
    if (swipeEdge == BackEventCompat.EDGE_LEFT) 0.8f else 0.2f

internal fun scaleBackPivotY(touchY: Float, containerHeightPx: Int): Float =
    if (touchY.isFinite() && containerHeightPx > 0) {
        (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
    } else {
        0.5f
    }

internal fun scaleBackExitDirection(swipeEdge: Int): Float =
    if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f

@Composable
internal fun ScalePredictiveBackLayout(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentKey: Any? = Unit,
    keepBackgroundComposed: Boolean = false,
    background: @Composable (Modifier) -> Unit,
    foreground: @Composable (Modifier) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }
    var inProgress by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var touchY by remember { mutableFloatStateOf(Float.NaN) }
    var widthPx by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableIntStateOf(0) }
    var queuedBackCount by remember { mutableIntStateOf(0) }
    var finishingBackCollectors by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val animationScope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBackFinished by rememberUpdatedState(onBackFinished)
    val currentContentKey by rememberUpdatedState(contentKey)
    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    PredictiveBackHandler(enabled = enabled) { events ->
        if (finishing) {
            finishingBackCollectors++
            try {
                events.collect { }
                queuedBackCount++
            } finally {
                finishingBackCollectors--
            }
            return@PredictiveBackHandler
        }

        val gestureGeneration = generation + 1
        generation = gestureGeneration
        try {
            settleJob?.cancel()
            progress.stop()
            exitProgress.stop()
            progress.snapTo(0f)
            exitProgress.snapTo(0f)
            swipeEdge = BackEventCompat.EDGE_LEFT
            touchY = Float.NaN
            inProgress = true
            events.collect { event ->
                progress.snapTo(event.progress.coerceIn(0f, 1f))
                swipeEdge = event.swipeEdge
                touchY = event.touchY
            }

            val gestureContentKey = contentKey
            finishing = true
            animationScope.launch {
                var replayQueuedBack = false
                var committed = false
                try {
                    coroutineScope {
                        launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = SCALE_BACK_EXIT_MILLIS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                        launch {
                            exitProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = SCALE_BACK_EXIT_MILLIS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    }
                    if (
                        gestureGeneration == generation &&
                        currentContentKey == gestureContentKey
                    ) {
                        currentOnBack()
                        committed = true
                        var contentChangeFrames = 0
                        while (
                            currentContentKey == gestureContentKey &&
                            contentChangeFrames < MAX_CONTENT_CHANGE_FRAMES
                        ) {
                            withFrameNanos { }
                            contentChangeFrames++
                        }
                        val contentChanged = currentContentKey != gestureContentKey
                        if (contentChanged) {
                            // Keep the outgoing layer off-screen until the destination has composed.
                            withFrameNanos { }
                        }
                        if (gestureGeneration == generation) {
                            inProgress = false
                        }
                        // Let the destination frame replace the transition layers before cleanup.
                        withFrameNanos { }
                        currentOnBackFinished()
                        if (contentChanged) {
                            // Apply the background-to-destination handoff before replaying another Back.
                            while (finishingBackCollectors > 0) {
                                withFrameNanos { }
                            }
                            if (queuedBackCount > 0) {
                                queuedBackCount--
                                replayQueuedBack = true
                            }
                        } else {
                            queuedBackCount = 0
                        }
                    }
                } finally {
                    if (gestureGeneration == generation) {
                        inProgress = false
                        finishing = false
                        if (!committed) queuedBackCount = 0
                    }
                }
                if (replayQueuedBack && gestureGeneration == generation) {
                    onBackPressedDispatcher?.onBackPressed()
                }
            }
        } catch (error: CancellationException) {
            if (gestureGeneration == generation && !finishing) {
                settleJob = animationScope.launch {
                    coroutineScope {
                        launch {
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = SCALE_BACK_SETTLE_MILLIS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                        launch {
                            exitProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = SCALE_BACK_EXIT_MILLIS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    }
                    if (gestureGeneration == generation) inProgress = false
                }
            }
            throw error
        } catch (error: Throwable) {
            if (gestureGeneration == generation) {
                progress.snapTo(0f)
                exitProgress.snapTo(0f)
                inProgress = false
                finishing = false
                queuedBackCount = 0
            }
            throw error
        }
    }

    val shape = RoundedCornerShape(rememberDeviceCornerRadius())
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                widthPx = it.width
                heightPx = it.height
            },
    ) {
        if (keepBackgroundComposed || inProgress) {
            background(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        if (inProgress) {
                            val dimAlpha = SCALE_BACK_DIM_ALPHA * (1f - exitProgress.value)
                            drawRect(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, SCALE_BACK_DIM_ALPHA)))
                        }
                    },
            )
        }
        foreground(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (inProgress) {
                        val scale = scaleBackScale(progress.value)
                        translationX = scaleBackExitDirection(swipeEdge) * widthPx * exitProgress.value
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(
                            pivotFractionX = scaleBackPivotX(swipeEdge),
                            pivotFractionY = scaleBackPivotY(touchY, heightPx),
                        )
                    } else {
                        translationX = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    this.shape = shape
                    clip = inProgress
                },
        )
    }
}

@Composable
private fun rememberDeviceCornerRadius(defaultRadius: Dp = 16.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current

    return remember(view, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            val corner = insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?: insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                ?: insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                ?: insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
            if (corner != null) {
                return@remember with(density) { corner.radius.toDp() }
            }
        }
        defaultRadius
    }
}
