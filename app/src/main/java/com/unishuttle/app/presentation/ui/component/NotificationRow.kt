package com.unishuttle.app.presentation.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

enum class NotificationCategory {
    ARRIVAL,
    SYSTEM,
    DELAY,
}

private val CategoryChipShape = RoundedCornerShape(999.dp)

@Composable
fun NotificationRow(
    category: NotificationCategory,
    title: String,
    message: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chipContainerColor = when (category) {
        NotificationCategory.ARRIVAL -> UniColors.Sun
        NotificationCategory.SYSTEM -> UniColors.Sky
        NotificationCategory.DELAY -> MaterialTheme.colorScheme.error
    }

    val chipContentColor = when (category) {
        NotificationCategory.ARRIVAL -> UniColors.Ink
        NotificationCategory.SYSTEM -> UniColors.Ink
        NotificationCategory.DELAY -> Color.White
    }

    val categoryLabel = when (category) {
        NotificationCategory.ARRIVAL -> "Arrival"
        NotificationCategory.SYSTEM -> "System"
        NotificationCategory.DELAY -> "Delay"
    }

    UniCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CategoryChipShape)
                        .background(chipContainerColor)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CategoryChipShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = chipContentColor,
                    )
                }

                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "NotificationRow - Light")
@Composable
private fun NotificationRowPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationRow(
                category = NotificationCategory.ARRIVAL,
                title = "Shuttle approaching Main Gate",
                message = "SH-2 will arrive at Main Gate in approximately 2 minutes.",
                timestamp = "2m ago",
            )
            NotificationRow(
                category = NotificationCategory.DELAY,
                title = "Route 3 traffic delay",
                message = "Heavy traffic reported near Technology Tower. Expect a 5-minute delay.",
                timestamp = "14m ago",
            )
            NotificationRow(
                category = NotificationCategory.SYSTEM,
                title = "Schedule update",
                message = "Night shuttle service has been extended until 11:30 PM today.",
                timestamp = "1h ago",
            )
        }
    }
}

@Preview(name = "NotificationRow - Dark")
@Composable
private fun NotificationRowPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationRow(
                category = NotificationCategory.ARRIVAL,
                title = "Shuttle approaching Main Gate",
                message = "SH-2 will arrive at Main Gate in approximately 2 minutes.",
                timestamp = "2m ago",
            )
        }
    }
}
