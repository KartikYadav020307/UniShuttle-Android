package com.unishuttle.app.presentation.ui.screen.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.unishuttle.app.presentation.ui.component.AlertChip
import com.unishuttle.app.presentation.ui.component.EtaBadge
import com.unishuttle.app.presentation.ui.component.SeatMeter
import com.unishuttle.app.presentation.ui.component.UniBottomSheet
import com.unishuttle.app.presentation.ui.component.UniButton
import com.unishuttle.app.presentation.ui.component.UniButtonVariant
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

/** One upcoming shuttle arrival listed in the stop sheet. */
data class StopArrival(
    val shuttleId: String,
    val seatsFree: Int,
)

private val AlertThresholds = listOf(10, 5, 2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailSheet(
    stop: CampusStop,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = Color.Transparent,
        scrimColor = UniColors.Ink.copy(alpha = 0.35f),
        dragHandle = null,
    ) {
        UniBottomSheet {
            StopDetailSheetContent(stop = stop)
        }
    }
}

@Composable
private fun StopDetailSheetContent(stop: CampusStop) {
    val haptics = LocalHapticFeedback.current

    var selectedArrivalId by rememberSaveable(stop.id) {
        mutableStateOf(stop.arrivals.firstOrNull()?.shuttleId)
    }
    var selectedThreshold by rememberSaveable(stop.id) { mutableStateOf(AlertThresholds.last()) }
    var isAlertArmed by rememberSaveable(stop.id) { mutableStateOf(false) }

    val selectedArrival = stop.arrivals.firstOrNull { it.shuttleId == selectedArrivalId }
        ?: stop.arrivals.firstOrNull()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stop.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${stop.routePosition} · ${stop.routeName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EtaBadge(text = "${stop.eta} away")
            EtaBadge(
                text = stop.trafficLabel,
                containerColor = UniColors.Sky,
                showIcon = false,
            )
        }

        stop.arrivals.forEach { arrival ->
            ArrivalRow(
                arrival = arrival,
                selected = arrival.shuttleId == selectedArrival?.shuttleId,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedArrivalId = arrival.shuttleId
                },
            )
        }

        SeatMeter(
            occupiedSeats = selectedArrival?.seatsFree ?: 0,
            totalSeats = stop.totalSeats,
            label = "Seats on ${selectedArrival?.shuttleId ?: stop.nextShuttle}",
        )

        Text(
            text = "Alert me before arrival",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlertThresholds.forEach { minutes ->
                AlertChip(
                    text = "$minutes min",
                    selected = minutes == selectedThreshold,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedThreshold = minutes
                        isAlertArmed = false
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        UniButton(
            text = if (isAlertArmed) {
                "Alert set at $selectedThreshold min"
            } else {
                "Alert me at $selectedThreshold min"
            },
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                isAlertArmed = true
            },
            variant = if (isAlertArmed) UniButtonVariant.Secondary else UniButtonVariant.Primary,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ArrivalRow(
    arrival: StopArrival,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = arrival.shuttleId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "${arrival.seatsFree} seats",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PreviewStop = CampusStop(
    id = "stop_main_gate",
    name = "Main Gate",
    position = LatLng(12.9692, 79.1559),
)

@Preview(name = "StopDetailSheet - Light", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StopDetailSheetPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 24.dp),
        ) {
            UniBottomSheet {
                StopDetailSheetContent(stop = PreviewStop)
            }
        }
    }
}

@Preview(name = "StopDetailSheet - Dark", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StopDetailSheetPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 24.dp),
        ) {
            UniBottomSheet {
                StopDetailSheetContent(stop = PreviewStop)
            }
        }
    }
}
