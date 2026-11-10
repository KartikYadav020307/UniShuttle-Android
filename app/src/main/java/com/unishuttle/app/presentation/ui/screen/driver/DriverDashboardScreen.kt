package com.unishuttle.app.presentation.ui.screen.driver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.domain.model.AppUser
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.driver.DutyState
import com.unishuttle.app.presentation.ui.component.SeatMeter
import com.unishuttle.app.presentation.ui.component.SignOutDialog
import com.unishuttle.app.presentation.ui.component.UniButton
import com.unishuttle.app.presentation.ui.component.UniButtonVariant
import com.unishuttle.app.presentation.ui.component.UniCard
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow
import com.unishuttle.app.presentation.viewmodel.DriverDashboardUiState
import com.unishuttle.app.presentation.viewmodel.DriverDashboardViewModel

@Composable
fun DriverDashboardScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriverDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.toggleDuty(hasLocationPermission = true)
        } else {
            viewModel.toggleDuty(hasLocationPermission = false)
        }
    }

    val onToggleDuty = {
        if (!uiState.isOnDuty) {
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocation) {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(perms.toTypedArray())
            } else {
                viewModel.toggleDuty(hasLocationPermission = true)
            }
        } else {
            viewModel.toggleDuty(hasLocationPermission = true)
        }
    }

    DriverDashboardContent(
        uiState = uiState,
        onToggleDuty = onToggleDuty,
        onIncrementSeat = viewModel::incrementSeat,
        onDecrementSeat = viewModel::decrementSeat,
        onRetryCleanup = viewModel::retryCleanup,
        onClearError = viewModel::clearError,
        onLogout = onLogout,
        modifier = modifier,
    )
}

@Composable
fun DriverDashboardContent(
    uiState: DriverDashboardUiState,
    onToggleDuty: () -> Unit,
    onIncrementSeat: () -> Unit,
    onDecrementSeat: () -> Unit,
    onRetryCleanup: () -> Unit,
    onClearError: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 1. Top Bar Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Driver Cockpit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "UniShuttle",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Sign out action button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .hardShadow(
                        shape = CircleShape,
                        offset = 3.dp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showSignOutDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 2. Driver & Vehicle Info Card ──
            UniCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DRIVER SESSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.driver?.displayName?.ifBlank { "Driver" } ?: "Driver",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val shuttleId = uiState.driver?.shuttleId?.trim().orEmpty()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (shuttleId.isNotBlank()) UniColors.Sun else MaterialTheme.colorScheme.error)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (shuttleId.isNotBlank()) "Shuttle #$shuttleId" else "Unassigned Shuttle",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = UniColors.Ink,
                            )
                        }

                        Text(
                            text = if (shuttleId.isNotBlank()) "Authoritative route" else "Contact admin to assign",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 3. Shift & Duty Card ──
            UniCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "SHIFT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )

                        // Status pill badge
                        val (statusBadgeBg, statusBadgeText, statusBadgeColor) = when (uiState.dutyState) {
                            is DutyState.OnDuty -> Triple(UniColors.Lime, "ON DUTY", UniColors.Ink)
                            is DutyState.Starting -> Triple(UniColors.Sun, "STARTING...", UniColors.Ink)
                            is DutyState.Stopping -> Triple(UniColors.Sun, "STOPPING...", UniColors.Ink)
                            is DutyState.CleanupFailed -> Triple(UniColors.Alert, "CLEANUP FAILED", Color.White)
                            is DutyState.Error -> Triple(UniColors.Alert, "ERROR", Color.White)
                            is DutyState.OffDuty -> Triple(MaterialTheme.colorScheme.surfaceVariant, "OFF DUTY", MaterialTheme.colorScheme.onSurface)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusBadgeBg)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusBadgeColor,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when (uiState.dutyState) {
                            is DutyState.OnDuty -> "Live location is actively broadcasting to students on campus."
                            is DutyState.Starting -> "Acquiring GPS fix and registering foreground service..."
                            is DutyState.Stopping -> "Stopping location provider and unregistering..."
                            is DutyState.CleanupFailed -> "Tracking stopped locally, but remote cleanup failed."
                            is DutyState.Error -> "An error occurred with the duty service."
                            is DutyState.OffDuty -> "Location is private. Shuttle will not appear on student map."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big tactile duty button
                    UniButton(
                        text = when {
                            uiState.dutyState is DutyState.OnDuty -> "End Shift (Go Off Duty)"
                            uiState.dutyState is DutyState.Starting -> "Starting Service..."
                            uiState.dutyState is DutyState.Stopping -> "Stopping Service..."
                            else -> "Start Shift (Go On Duty)"
                        },
                        onClick = onToggleDuty,
                        variant = if (uiState.isOnDuty) UniButtonVariant.Secondary else UniButtonVariant.Primary,
                        enabled = !uiState.isTransitioning && uiState.driver?.shuttleId?.isNotBlank() == true,
                        isLoading = uiState.isTransitioning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── 4. Cleanup Failed Banner (if applicable) ──
            AnimatedVisibility(
                visible = uiState.dutyState is DutyState.CleanupFailed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val cleanup = uiState.dutyState as? DutyState.CleanupFailed
                UniCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = UniColors.NightSurface,
                    borderColor = UniColors.Alert,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = UniColors.Alert,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = "Remote Cleanup Incomplete",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UniColors.Alert,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = cleanup?.message ?: "Local tracking stopped, but server cleanup failed. Students may still see this shuttle as active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        UniButton(
                            text = "Retry Remote Cleanup",
                            onClick = onRetryCleanup,
                            variant = UniButtonVariant.Primary,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── 5. General Error Banner ──
            AnimatedVisibility(
                visible = uiState.errorMessage != null && uiState.dutyState !is DutyState.CleanupFailed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                UniCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = UniColors.NightSurface,
                    borderColor = UniColors.Alert,
                    onClick = onClearError,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = UniColors.Alert,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = UniColors.Alert,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // ── 6. Seat Capacity Cockpit Card ──
            UniCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "AVAILABLE SEATS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )

                        if (uiState.isSaving) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Syncing...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Large tactile seat numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "${uiState.availableSeats}",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                            fontWeight = FontWeight.Black,
                            color = if (uiState.availableSeats > 0) MaterialTheme.colorScheme.onSurface else UniColors.Alert,
                        )
                        Text(
                            text = "of ${uiState.totalSeats} seats free",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Visual meter bar
                    val occupiedSeats = (uiState.totalSeats - uiState.availableSeats).coerceAtLeast(0)
                    SeatMeter(
                        occupiedSeats = occupiedSeats,
                        totalSeats = uiState.totalSeats,
                        showHeader = false,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Large 72dp tactile buttons designed for moving vehicle ergonomics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Decrement seat button (-1 free seat / passenger boarded)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .hardShadow(
                                    shape = RoundedCornerShape(12.dp),
                                    offset = if (uiState.availableSeats > 0 && !uiState.isSaving) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.availableSeats > 0 && !uiState.isSaving) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = uiState.availableSeats > 0 && !uiState.isSaving,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDecrementSeat()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "− 1 Seat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.availableSeats > 0 && !uiState.isSaving) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }

                        // Increment seat button (+1 free seat / passenger alighted)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp)
                                .hardShadow(
                                    shape = RoundedCornerShape(12.dp),
                                    offset = if (uiState.availableSeats < uiState.totalSeats && !uiState.isSaving) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.availableSeats < uiState.totalSeats && !uiState.isSaving) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = uiState.availableSeats < uiState.totalSeats && !uiState.isSaving,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onIncrementSeat()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+ 1 Seat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.availableSeats < uiState.totalSeats && !uiState.isSaving) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    if (!uiState.isOnDuty) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Note: Changes update seat capacity while off duty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                showSignOutDialog = false
                onLogout()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }
}

@Preview(name = "DriverDashboard - On Duty Light", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun DriverDashboardPreviewOnDutyLight() {
    UniShuttleTheme(darkTheme = false) {
        DriverDashboardContent(
            uiState = DriverDashboardUiState(
                driver = AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Ramesh Kumar", role = UserRole.DRIVER, shuttleId = "101"),
                dutyState = DutyState.OnDuty,
                availableSeats = 14,
                totalSeats = 24,
            ),
            onToggleDuty = {},
            onIncrementSeat = {},
            onDecrementSeat = {},
            onRetryCleanup = {},
            onClearError = {},
            onLogout = {},
        )
    }
}

@Preview(name = "DriverDashboard - Off Duty Dark", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun DriverDashboardPreviewOffDutyDark() {
    UniShuttleTheme(darkTheme = true) {
        DriverDashboardContent(
            uiState = DriverDashboardUiState(
                driver = AppUser(uid = "d1", email = "driver@campus.edu", displayName = "Ramesh Kumar", role = UserRole.DRIVER, shuttleId = "101"),
                dutyState = DutyState.OffDuty,
                availableSeats = 24,
                totalSeats = 24,
            ),
            onToggleDuty = {},
            onIncrementSeat = {},
            onDecrementSeat = {},
            onRetryCleanup = {},
            onClearError = {},
            onLogout = {},
        )
    }
}
