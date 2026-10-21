package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow

@Composable
fun UniCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && onClick != null) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "card_press_offset",
    )

    val currentShadowOffset by animateDpAsState(
        targetValue = if (isPressed && onClick != null) (shadowOffset - 2.dp).coerceAtLeast(0.dp) else shadowOffset,
        animationSpec = tween(durationMillis = 180),
        label = "card_shadow_offset",
    )

    Box(
        modifier = modifier
            .offset(x = currentOffset, y = currentOffset)
            .then(
                if (currentShadowOffset > 0.dp) {
                    Modifier.hardShadow(
                        shape = shape,
                        offset = currentShadowOffset,
                        color = borderColor,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Preview(name = "UniCard - Light")
@Composable
private fun UniCardPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            UniCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SH-2",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "14 seats available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "UniCard - Dark")
@Composable
private fun UniCardPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            UniCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SH-2",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "14 seats available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
