package com.resdownload.android.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val FloatingActionMenuAnimationDurationMillis = 320
private const val FloatingActionContentAnimationDurationMillis = 180
private const val FloatingActionSubmenuSpringStiffness = 1_200f
private const val FloatingActionSubmenuVisibilityThreshold = 0.002f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionDock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

internal class FloatingActionMenuBoundsState internal constructor() {
    internal var boundsInWindow: Rect = Rect.Zero
}

@Composable
internal fun rememberFloatingActionMenuBoundsState(): FloatingActionMenuBoundsState =
    remember { FloatingActionMenuBoundsState() }

internal fun Modifier.trackFloatingActionMenuBounds(
    state: FloatingActionMenuBoundsState,
): Modifier = onGloballyPositioned { state.boundsInWindow = it.boundsInWindow() }

private class FloatingActionMenuLayoutState {
    private var targetExpanded: Boolean? = null
    private var segmentStartProgress = 0f
    private var segmentStartSize = IntSize.Zero
    private var currentSize = IntSize.Zero

    fun resolveSize(
        progress: Float,
        expanded: Boolean,
        collapsedSize: IntSize,
        naturalExpandedSize: IntSize,
    ): IntSize {
        if (targetExpanded == null) {
            targetExpanded = expanded
            currentSize = if (expanded) naturalExpandedSize else collapsedSize
            segmentStartProgress = progress
            segmentStartSize = currentSize
            return currentSize
        }

        if (targetExpanded != expanded) {
            targetExpanded = expanded
            segmentStartProgress = progress
            segmentStartSize = currentSize
        }

        currentSize = when {
            expanded && progress >= 1f -> naturalExpandedSize
            !expanded && progress <= 0f -> collapsedSize
            expanded -> {
                val remainingProgress = 1f - segmentStartProgress
                val fraction = if (remainingProgress > 0f) {
                    (progress - segmentStartProgress) / remainingProgress
                } else {
                    1f
                }
                lerpIntSize(segmentStartSize, naturalExpandedSize, fraction)
            }
            else -> {
                val fraction = if (segmentStartProgress > 0f) {
                    (segmentStartProgress - progress) / segmentStartProgress
                } else {
                    1f
                }
                lerpIntSize(segmentStartSize, collapsedSize, fraction)
            }
        }
        return currentSize
    }
}

private class FloatingActionMenuHostState {
    var positionInWindow: Offset = Offset.Zero
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    toggleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(targetState = expanded, label = "floatingActionMenu")
    val layoutState = remember { FloatingActionMenuLayoutState() }
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = FloatingActionMenuAnimationDurationMillis,
                easing = FastOutSlowInEasing,
            )
        },
        label = "floatingActionMenuProgress",
    ) { isExpanded -> if (isExpanded) 1f else 0f }

    FloatingActionDock(modifier = modifier) {
        FloatingActionMenuLayout(
            progress = progress,
            expanded = expanded,
            state = layoutState,
            toggleModifier = toggleModifier,
            onExpandedChange = onExpandedChange,
            content = content,
        )
    }
}

@Composable
fun FloatingActionSubmenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    toggle: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val transition = updateTransition(targetState = visible, label = "floatingActionSubmenu")
    val animatedProgress by transition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = 1f,
                stiffness = FloatingActionSubmenuSpringStiffness,
                visibilityThreshold = FloatingActionSubmenuVisibilityThreshold,
            )
        },
        label = "floatingActionSubmenuProgress",
    ) { isVisible -> if (isVisible) 1f else 0f }
    val progress = animatedProgress.coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val spacing = 2.dp.roundToPx()
                    val animatedHeight = lerpInt(
                        start = 0,
                        end = placeable.height + spacing,
                        fraction = progress,
                    )
                    layout(placeable.width, animatedHeight) {
                        placeable.placeRelative(
                            x = 0,
                            y = animatedHeight - spacing - placeable.height,
                        )
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 8.dp.toPx()
                    },
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
        toggle()
    }
}

@Composable
internal fun Modifier.dismissFloatingActionMenuOnOutsideTap(
    enabled: Boolean,
    menuBounds: FloatingActionMenuBoundsState,
    onDismiss: () -> Unit,
): Modifier {
    val hostState = remember { FloatingActionMenuHostState() }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    return this
        .onGloballyPositioned { hostState.positionInWindow = it.positionInWindow() }
        .pointerInput(Unit) {
            // Observe the whole gesture without consuming it so the tapped control still runs.
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val positionInWindow = down.position + hostState.positionInWindow
                val currentMenuBounds = menuBounds.boundsInWindow
                if (
                    currentEnabled &&
                    currentMenuBounds != Rect.Zero &&
                    !currentMenuBounds.contains(positionInWindow)
                ) {
                    currentOnDismiss()
                }
            }
        }
}

private enum class FloatingActionMenuSlot {
    Actions,
    ToggleMeasure,
    Toggle,
}

@Composable
private fun FloatingActionMenuLayout(
    progress: Float,
    expanded: Boolean,
    state: FloatingActionMenuLayoutState,
    toggleModifier: Modifier,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier.clipToBounds(),
    ) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val spacing = 2.dp.roundToPx()
        val collapsedWidth = 56.dp.roundToPx()
        val collapsedHeight = 56.dp.roundToPx()

        val toggleMeasure = subcompose(FloatingActionMenuSlot.ToggleMeasure) {
            FloatingActionMenuToggle(
                expanded = true,
                progress = 1f,
                fillWidth = false,
                modifier = Modifier.clearAndSetSemantics { },
                onExpandedChange = {},
            )
        }.single().measure(childConstraints)

        val actionConstraints = childConstraints.copy(
            minWidth = toggleMeasure.width.coerceAtMost(childConstraints.maxWidth),
        )

        val actionPlaceable = subcompose(FloatingActionMenuSlot.Actions) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 16.dp.toPx()
                    }
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { }),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }.single().measure(actionConstraints)

        val naturalExpandedSize = IntSize(
            width = maxOf(actionPlaceable.width, toggleMeasure.width),
            height = actionPlaceable.height + spacing + toggleMeasure.height,
        )
        val animatedSize = state.resolveSize(
            progress = progress,
            expanded = expanded,
            collapsedSize = IntSize(collapsedWidth, collapsedHeight),
            naturalExpandedSize = naturalExpandedSize,
        )
        val boundedSize = constraints.constrain(animatedSize)

        val togglePlaceable = subcompose(FloatingActionMenuSlot.Toggle) {
            FloatingActionMenuToggle(
                expanded = expanded,
                progress = progress,
                fillWidth = true,
                modifier = toggleModifier,
                onExpandedChange = onExpandedChange,
            )
        }.single().measure(Constraints.fixed(boundedSize.width, toggleMeasure.height))

        layout(boundedSize.width, boundedSize.height) {
            val toggleX = boundedSize.width - togglePlaceable.width
            val toggleY = boundedSize.height - togglePlaceable.height
            togglePlaceable.placeRelative(toggleX, toggleY)

            if (progress > 0f) {
                val actionX = boundedSize.width - actionPlaceable.width
                val actionY = toggleY - spacing - actionPlaceable.height
                actionPlaceable.placeRelative(actionX, actionY)
            }
        }
    }
}

private fun lerpInt(start: Int, end: Int, fraction: Float): Int =
    (start + (end - start) * fraction.coerceIn(0f, 1f)).roundToInt()

private fun lerpIntSize(start: IntSize, end: IntSize, fraction: Float): IntSize = IntSize(
    width = lerpInt(start.width, end.width, fraction),
    height = lerpInt(start.height, end.height, fraction),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingActionMenuToggle(
    expanded: Boolean,
    progress: Float,
    fillWidth: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (expanded) "收起菜单" else "更多操作"
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val showExpandedLabel = expanded || progress > 0f
    val labelProgress = smoothStep(((progress - 0.25f) / 0.75f).coerceIn(0f, 1f))
    Layout(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .widthIn(min = 56.dp)
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = { onExpandedChange(!expanded) },
            ),
        content = {
            if (showExpandedLabel) {
                Text(
                    text = "收起菜单",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.graphicsLayer {
                        alpha = labelProgress
                        translationX = (1f - labelProgress) * 12.dp.toPx()
                    },
                )
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    FloatingActionMenuIcon(
                        progress = progress,
                        color = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = Constraints.Infinity,
            minHeight = 0,
        )
        val iconPlaceable = measurables.last().measure(childConstraints)
        val labelPlaceable = if (showExpandedLabel) {
            measurables.first().measure(childConstraints)
        } else {
            null
        }
        val collapsedInset = 10.dp.roundToPx()
        val expandedInset = 12.dp.roundToPx()
        val labelSpacing = 10.dp.roundToPx()
        val desiredWidth = if (labelPlaceable == null) {
            collapsedInset * 2 + iconPlaceable.width
        } else {
            expandedInset * 2 + labelPlaceable.width + labelSpacing + iconPlaceable.width
        }
        val desiredHeight = maxOf(
            56.dp.roundToPx(),
            iconPlaceable.height,
            labelPlaceable?.height ?: 0,
        )
        val size = constraints.constrain(IntSize(desiredWidth, desiredHeight))

        layout(size.width, size.height) {
            val iconInset = lerpInt(collapsedInset, expandedInset, progress)
            val iconX = (size.width - iconInset - iconPlaceable.width).coerceAtLeast(0)
            val iconY = (size.height - iconPlaceable.height) / 2
            iconPlaceable.placeRelative(iconX, iconY)

            labelPlaceable?.let { placeable ->
                val labelX = iconX - labelSpacing - placeable.width
                val labelY = (size.height - placeable.height) / 2
                placeable.placeRelative(labelX, labelY)
            }
        }
    }
}

@Composable
private fun FloatingActionMenuIcon(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val morph = progress.coerceIn(0f, 1f)
    val dotsToCenter = smoothStep((morph / 0.55f).coerceIn(0f, 1f))
    val linesFromCenter = smoothStep(((morph - 0.25f) / 0.75f).coerceIn(0f, 1f))
    val dotsFade = 1f - smoothStep(((morph - 0.18f) / 0.5f).coerceIn(0f, 1f))

    Canvas(modifier = modifier) {
        val center = size.width / 2f
        val dotRadius = size.minDimension * 0.11f
        val dotSpread = size.height * 0.31f * (1f - dotsToCenter)
        val dotAlpha = dotsFade
        for (position in -1..1) {
            drawCircle(
                color = color.copy(alpha = dotAlpha),
                radius = dotRadius * (0.85f + 0.15f * (1f - dotsToCenter)),
                center = Offset(
                    x = center,
                    y = center + position * dotSpread,
                ),
            )
        }

        val arm = size.minDimension * 0.32f * linesFromCenter
        val strokeWidth = size.minDimension * 0.12f
        val lineColor = color.copy(alpha = linesFromCenter)
        drawLine(
            color = lineColor,
            start = Offset(center - arm, center - arm),
            end = Offset(center + arm, center + arm),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = lineColor,
            start = Offset(center + arm, center - arm),
            end = Offset(center - arm, center + arm),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val containerColor = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    widthReferenceLabel: String = label,
    animateContentChanges: Boolean = false,
) {
    val containerColor = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val contentEffectsSpec = tween<Float>(
        durationMillis = FloatingActionContentAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )
    val contentOffsetSpec = tween<IntOffset>(
        durationMillis = FloatingActionContentAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (animateContentChanges) {
                    AnimatedContent(
                        targetState = icon,
                        transitionSpec = {
                            (fadeIn(contentEffectsSpec) + scaleIn(
                                contentEffectsSpec,
                                initialScale = 0.82f,
                            )).togetherWith(
                                fadeOut(contentEffectsSpec) + scaleOut(
                                    contentEffectsSpec,
                                    targetScale = 0.82f,
                                ),
                            )
                        },
                        contentAlignment = Alignment.Center,
                        label = "floatingActionIcon",
                    ) { animatedIcon ->
                        Icon(
                            imageVector = animatedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box {
            if (widthReferenceLabel != label) {
                Text(
                    text = widthReferenceLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .graphicsLayer { alpha = 0f },
                )
            }
            if (animateContentChanges) {
                AnimatedContent(
                    targetState = label,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(contentEffectsSpec) + slideInVertically(
                            contentOffsetSpec,
                        ) { height -> height / 3 }).togetherWith(
                            fadeOut(contentEffectsSpec) + slideOutVertically(
                                contentOffsetSpec,
                            ) { height -> -height / 3 },
                        )
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "floatingActionLabel",
                ) { animatedLabel ->
                    FloatingActionLabel(animatedLabel)
                }
            } else {
                FloatingActionLabel(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingActionLabel(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLargeEmphasized,
        maxLines = 1,
        softWrap = false,
    )
}
