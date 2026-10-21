package com.unishuttle.app.presentation.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

private val MapFrameShape = RoundedCornerShape(24.dp)

@Composable
fun MapFrame(
    modifier: Modifier = Modifier,
    overlays: (@Composable BoxScope.() -> Unit)? = null,
    mapContent: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MapFrameShape)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), MapFrameShape),
    ) {
        // Base map content (clipped to frame)
        mapContent()

        // Overlays (chips, header, card) sitting above map
        if (overlays != null) {
            overlays()
        }
    }
}

@Preview(name = "MapFrame - Light")
@Composable
private fun MapFramePreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MapFrame(
                modifier = Modifier
                    .fillMaxSize()
                    .height(300.dp),
                overlays = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                    ) {
                        EtaBadge(text = "3 min away")
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Google Map",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "MapFrame - Dark")
@Composable
private fun MapFramePreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            MapFrame(
                modifier = Modifier
                    .fillMaxSize()
                    .height(300.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Google Map",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
