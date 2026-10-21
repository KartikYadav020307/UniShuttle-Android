package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.unishuttle.app.domain.model.SeatAvailability

@Composable
fun SeatAvailabilityMeter(
    seatAvailability: SeatAvailability?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val fullnessRatio = seatAvailability?.occupiedRatio ?: 0f
    val barColor by animateColorAsState(
        targetValue = seatAvailability?.meterColor() ?: MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 350),
        label = "seat-meter-color",
    )
    val animatedRatio by animateFloatAsState(
        targetValue = fullnessRatio,
        animationSpec = tween(durationMillis = 350),
        label = "seat-meter-fill",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = seatAvailability?.labelText().orEmpty().ifBlank { "Seats pending" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = seatAvailability?.statusText().orEmpty().ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelMedium,
                    color = barColor,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio)
                    .height(10.dp)
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun SeatAvailability.meterColor(): Color =
    when {
        isFull -> MaterialTheme.colorScheme.error
        availableRatio > 0.50f -> Color(0xFF1B7F4C)
        availableRatio > 0.20f -> Color(0xFFFFB020)
        else -> Color(0xFFD32F2F)
    }

private fun SeatAvailability.labelText(): String =
    "$availableSeats of $totalSeats seats available"

private fun SeatAvailability.statusText(): String =
    when {
        isFull -> "Full"
        availableRatio > 0.50f -> "Open"
        availableRatio > 0.20f -> "Filling"
        else -> "Low"
    }
