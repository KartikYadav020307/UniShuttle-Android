package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow

enum class UniButtonVariant {
    Primary,
    Secondary,
}

private val ButtonShape = RoundedCornerShape(999.dp)

@Composable
fun UniButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: UniButtonVariant = UniButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled && !isLoading) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "button_press_offset",
    )

    val currentShadowOffset by animateDpAsState(
        targetValue = if (isPressed && enabled && !isLoading) 2.dp else 4.dp,
        animationSpec = tween(durationMillis = 180),
        label = "button_shadow_offset",
    )

    val containerColor = when (variant) {
        UniButtonVariant.Primary -> if (enabled) MaterialTheme.colorScheme.primary else UniColors.Muted
        UniButtonVariant.Secondary -> if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (variant) {
        UniButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
        UniButtonVariant.Secondary -> MaterialTheme.colorScheme.onSurface
    }

    val shadowColor = when (variant) {
        UniButtonVariant.Primary -> MaterialTheme.colorScheme.outline
        UniButtonVariant.Secondary -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset(x = currentOffset, y = currentOffset)
            .hardShadow(
                shape = ButtonShape,
                offset = currentShadowOffset,
                color = shadowColor,
            )
            .clip(ButtonShape)
            .background(containerColor)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), ButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Preview(name = "UniButton - Light")
@Composable
private fun UniButtonPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            UniButton(text = "Alert me at 2 min", onClick = {})
        }
    }
}

@Preview(name = "UniButton - Dark")
@Composable
private fun UniButtonPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            UniButton(text = "Alert me at 2 min", onClick = {})
        }
    }
}
