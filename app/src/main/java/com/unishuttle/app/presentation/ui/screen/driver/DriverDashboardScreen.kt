package com.unishuttle.app.presentation.ui.screen.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.presentation.ui.component.SeatAvailabilityMeter
import com.unishuttle.app.presentation.viewmodel.DriverDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDashboardScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriverDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Driver Dashboard") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Driver info ──
            uiState.driver?.let { driver ->
                Text(
                    text = "Hello, ${driver.displayName}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Shuttle ID: ${driver.shuttleId.ifBlank { "Not assigned" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Duty toggle ──
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isOnDuty) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (uiState.isOnDuty) "On Duty" else "Off Duty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (uiState.isOnDuty) {
                                "Broadcasting location to students"
                            } else {
                                "Location is private — not visible to students"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.isOnDuty,
                        onCheckedChange = { viewModel.toggleDuty() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            // ── Seat counter (only visible on-duty) ──
            if (uiState.isOnDuty) {
                val seats = SeatAvailability(
                    shuttleId = uiState.driver?.shuttleId.orEmpty(),
                    availableSeats = uiState.availableSeats,
                    totalSeats = uiState.totalSeats,
                    updatedAtMillis = System.currentTimeMillis(),
                )

                Text(
                    text = "Seat Availability",
                    style = MaterialTheme.typography.titleMedium,
                )

                SeatAvailabilityMeter(
                    seatAvailability = seats,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::decrementSeat,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        enabled = !uiState.isSaving && uiState.availableSeats > 0,
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineMedium)
                    }
                    Button(
                        onClick = viewModel::incrementSeat,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        enabled = !uiState.isSaving && uiState.availableSeats < uiState.totalSeats,
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            // ── Error ──
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
