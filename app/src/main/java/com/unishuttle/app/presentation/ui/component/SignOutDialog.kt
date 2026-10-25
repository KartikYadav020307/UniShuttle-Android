package com.unishuttle.app.presentation.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme

/** Confirmation dialog shared by every sign-out entry point. */
@Composable
fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Sign out?") },
        text = { Text(text = "You'll need to sign in with your campus account again.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = "Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}

@Preview(name = "SignOutDialog - Light")
@Composable
private fun SignOutDialogPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        SignOutDialog(onConfirm = {}, onDismiss = {})
    }
}

@Preview(name = "SignOutDialog - Dark")
@Composable
private fun SignOutDialogPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        SignOutDialog(onConfirm = {}, onDismiss = {})
    }
}
