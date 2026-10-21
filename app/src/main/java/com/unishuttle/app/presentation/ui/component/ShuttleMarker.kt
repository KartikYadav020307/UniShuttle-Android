package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow

private val MarkerShape = RoundedCornerShape(999.dp)

@Composable
fun ShuttleMarker(
    label: String,
    heading: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val animatedHeading by animateFloatAsState(
        targetValue = heading,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "shuttle_marker_heading",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .hardShadow(shape = MarkerShape, offset = 3.dp, color = MaterialTheme.colorScheme.outline)
                .clip(MarkerShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), MarkerShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Shuttle heading",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(animatedHeading),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Preview(name = "ShuttleMarker - Light")
@Composable
private fun ShuttleMarkerPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(24.dp)) {
            ShuttleMarker(label = "SH-2 · 3 min", heading = 45f)
        }
    }
}

@Preview(name = "ShuttleMarker - Dark")
@Composable
private fun ShuttleMarkerPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(24.dp)) {
            ShuttleMarker(label = "SH-2 · 3 min", heading = 45f)
        }
    }
}
