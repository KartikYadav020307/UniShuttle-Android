package com.unishuttle.app.presentation.ui.screen.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopAlert
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.presentation.ui.component.AlertTimePicker
import com.unishuttle.app.presentation.ui.component.SeatAvailabilityMeter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailBottomSheet(
    stop: ShuttleStop,
    shuttles: List<ShuttleLocation>,
    seatAvailability: List<SeatAvailability>,
    stopEtas: List<StopEta>,
    activeAlert: StopAlert?,
    onDismiss: () -> Unit,
    onSetAlert: (thresholdMinutes: Int) -> Unit,
    onClearAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val rows = shuttles
                .map { shuttle ->
                    StopShuttleRowData(
                        shuttle = shuttle,
                        eta = stopEtas
                            .filter { it.stopId == stop.id && it.shuttleId == shuttle.id }
                            .minByOrNull { it.durationSeconds },
                        seats = seatAvailability.firstOrNull { it.shuttleId == shuttle.id },
                    )
                }
                .sortedBy { it.eta?.durationSeconds ?: Long.MAX_VALUE }

            if (rows.isEmpty()) {
                Text(
                    text = "No active shuttles yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEachIndexed { index, row ->
                    StopShuttleRow(row = row)
                    if (index < rows.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }

            // ── Alert Me Section ──
            HorizontalDivider()

            if (showTimePicker || activeAlert != null) {
                AlertTimePicker(
                    currentThreshold = activeAlert?.thresholdMinutes,
                    onSelect = { minutes ->
                        onSetAlert(minutes)
                        showTimePicker = false
                    },
                    onClear = {
                        onClearAlert()
                        showTimePicker = false
                    },
                )
            } else {
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🔔  Alert Me")
                }
            }
        }
    }
}

@Composable
private fun StopShuttleRow(row: StopShuttleRowData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Shuttle ${row.shuttle.id}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "ETA ${row.eta?.etaText ?: "calculating"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = row.seats?.let { "${it.availableSeats}/${it.totalSeats}" } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SeatAvailabilityMeter(
            seatAvailability = row.seats,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private data class StopShuttleRowData(
    val shuttle: ShuttleLocation,
    val eta: StopEta?,
    val seats: SeatAvailability?,
)
