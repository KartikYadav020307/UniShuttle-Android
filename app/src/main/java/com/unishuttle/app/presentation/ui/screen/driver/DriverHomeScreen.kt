package com.unishuttle.app.presentation.ui.screen.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.presentation.ui.component.SignOutDialog
import com.unishuttle.app.presentation.ui.component.UniButton
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

/**
 * Step 1 driver placeholder. Step 4 replaces this with the real cockpit.
 */
@Composable
fun DriverHomeScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Driver home (coming soon)",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        UniButton(
            text = "Sign out",
            onClick = { showSignOutDialog = true },
            modifier = Modifier.fillMaxWidth(0.65f),
        )
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

@Preview(name = "DriverHome - Light", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun DriverHomePreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        DriverHomeScreen(onLogout = {})
    }
}

@Preview(name = "DriverHome - Dark", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun DriverHomePreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        DriverHomeScreen(onLogout = {})
    }
}
