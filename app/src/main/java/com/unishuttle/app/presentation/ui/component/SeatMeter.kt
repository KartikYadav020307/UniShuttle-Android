package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

private val BarShape = RoundedCornerShape(6.dp)

@Composable
fun SeatMeter(
    occupiedSeats: Int,
    totalSeats: Int,
    modifier: Modifier = Modifier,
    label: String? = "Seats on shuttle",
    showHeader: Boolean = true,
) {
    val ratio = if (totalSeats > 0) {
        (occupiedSeats.toFloat() / totalSeats.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "seat_meter_fill",
    )

    // Above 50 percent is Lime, 20 to 50 percent is Sun, under 20 percent is Alert
    val targetColor: Color = when {
        ratio > 0.50f -> UniColors.Lime
        ratio >= 0.20f -> UniColors.Sun
        else -> MaterialTheme.colorScheme.error
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "seat_meter_color",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader && !label.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$occupiedSeats / $totalSeats",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(BarShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), BarShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio)
                    .fillMaxHeight()
                    .background(animatedColor),
            )
        }
    }
}

@Composable
fun SeatMeter(
    seatAvailability: SeatAvailability?,
    modifier: Modifier = Modifier,
    label: String = "Seats on shuttle",
) {
    val occupied = seatAvailability?.occupiedSeats ?: 0
    val total = seatAvailability?.totalSeats ?: 0
    SeatMeter(
        occupiedSeats = occupied,
        totalSeats = total,
        modifier = modifier,
        label = label,
    )
}

@Preview(name = "SeatMeter - Light")
@Composable
private fun SeatMeterPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SeatMeter(occupiedSeats = 14, totalSeats = 24, label = "Seats on SH-2")
            SeatMeter(occupiedSeats = 8, totalSeats = 24, label = "Seats on SH-3")
            SeatMeter(occupiedSeats = 3, totalSeats = 24, label = "Seats on SH-5")
        }
    }
}

@Preview(name = "SeatMeter - Dark")
@Composable
private fun SeatMeterPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SeatMeter(occupiedSeats = 14, totalSeats = 24, label = "Seats on SH-2")
            SeatMeter(occupiedSeats = 8, totalSeats = 24, label = "Seats on SH-3")
            SeatMeter(occupiedSeats = 3, totalSeats = 24, label = "Seats on SH-5")
        }
    }
}
