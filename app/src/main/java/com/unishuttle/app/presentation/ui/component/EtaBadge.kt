package com.unishuttle.app.presentation.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

private val BadgeShape = RoundedCornerShape(999.dp)

@Composable
fun EtaBadge(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(BadgeShape)
            .background(UniColors.Sun)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), BadgeShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = UniColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = UniColors.Ink,
            )
        }
    }
}

@Preview(name = "EtaBadge - Light")
@Composable
private fun EtaBadgePreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            EtaBadge(text = "3 min away")
        }
    }
}

@Preview(name = "EtaBadge - Dark")
@Composable
private fun EtaBadgePreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            EtaBadge(text = "3 min away")
        }
    }
}
