package com.unishuttle.app.presentation.ui.screen.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.presentation.ui.component.SeatAvailabilityMeter
import com.unishuttle.app.presentation.viewmodel.DriverSeatViewModel

@Composable
fun DriverSeatCounterScreen(
    modifier: Modifier = Modifier,
    viewModel: DriverSeatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seats = SeatAvailability(
        shuttleId = uiState.shuttleId,
        availableSeats = uiState.availableSeats,
        totalSeats = uiState.totalSeats,
        updatedAtMillis = 0L,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Driver Seats",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = uiState.availableSeats.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "available of ${uiState.totalSeats}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SeatAvailabilityMeter(
                seatAvailability = seats,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::decrementAvailableSeats,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    enabled = !uiState.isSaving && uiState.availableSeats > 0,
                ) {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }

                Button(
                    onClick = viewModel::incrementAvailableSeats,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    enabled = !uiState.isSaving && uiState.availableSeats < uiState.totalSeats,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
