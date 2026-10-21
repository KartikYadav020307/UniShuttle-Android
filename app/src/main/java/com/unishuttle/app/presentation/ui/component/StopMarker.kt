package com.unishuttle.app.presentation.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

@Composable
fun StopMarker(
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentDesc: String = "Bus stop marker",
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val sunColor = UniColors.Sun

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = contentDesc
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val centerOffset = center

            // Selected stop gets a dashed outer ring
            if (isSelected) {
                drawCircle(
                    color = outlineColor,
                    radius = 16.dp.toPx(),
                    center = centerOffset,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                    ),
                )
            }

            // 20dp Sun circle with 2dp Ink ring
            val innerRadius = 10.dp.toPx()
            drawCircle(
                color = sunColor,
                radius = innerRadius,
                center = centerOffset,
            )
            drawCircle(
                color = outlineColor,
                radius = innerRadius,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Preview(name = "StopMarker - Light")
@Composable
private fun StopMarkerPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            StopMarker(isSelected = true)
        }
    }
}

@Preview(name = "StopMarker - Dark")
@Composable
private fun StopMarkerPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            StopMarker(isSelected = false)
        }
    }
}
