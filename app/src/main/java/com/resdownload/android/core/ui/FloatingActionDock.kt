package com.resdownload.android.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.getValue
import kotlin.math.roundToInt

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
    val progress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 320, easing = FastOutSlowInEasing) },
        label = "floatingActionMenuProgress",
    ) { isExpanded -> if (isExpanded) 1f else 0f }

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (transition.currentState || transition.targetState) {
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = true,
                    clippingEnabled = false,
                ),
            ) {
                FloatingActionMenuDock(
                    progress = progress,
                    expanded = expanded,
                    modifier = modifier,
                    toggleModifier = toggleModifier,
                    onExpandedChange = onExpandedChange,
                    content = content,
                )
            }
        } else {
            FloatingActionMenuDock(
                progress = progress,
                expanded = expanded,
                modifier = modifier,
                toggleModifier = toggleModifier,
                onExpandedChange = onExpandedChange,
                content = content,
            )
        }
    }
}

@Composable
private fun FloatingActionMenuDock(
    progress: Float,
    expanded: Boolean,
    modifier: Modifier,
    toggleModifier: Modifier,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FloatingActionDock(modifier = modifier) {
        FloatingActionMenuLayout(
            progress = progress,
            expanded = expanded,
            toggleModifier = toggleModifier,
            onExpandedChange = onExpandedChange,
            content = content,
        )
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

        val expandedWidth = maxOf(actionPlaceable.width, toggleMeasure.width)
        val expandedHeight = actionPlaceable.height + spacing + toggleMeasure.height
        val width = lerpInt(collapsedWidth, expandedWidth, progress)
        val height = lerpInt(collapsedHeight, expandedHeight, progress)
        val boundedSize = constraints.constrain(IntSize(width, height))

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
    (start + (end - start) * fraction).roundToInt()

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
    val labelProgress = if (expanded) {
        ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
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
            )
            .padding(
                horizontal = if (expanded) 12.dp else 0.dp,
                vertical = 8.dp,
            ),
        horizontalArrangement = if (expanded) {
            Arrangement.End
        } else {
            Arrangement.Center
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded) {
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
            Spacer(Modifier.width(10.dp))
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
    }
}
