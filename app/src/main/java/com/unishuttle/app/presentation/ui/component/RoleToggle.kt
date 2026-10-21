package com.unishuttle.app.presentation.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow

private val ToggleShape = RoundedCornerShape(999.dp)

@Composable
fun RoleToggle(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
    modifier: Modifier = Modifier,
    roles: List<UserRole> = listOf(UserRole.STUDENT, UserRole.DRIVER),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        roles.forEach { role ->
            val isSelected = role == selectedRole
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val currentOffset by animateDpAsState(
                targetValue = if (isPressed) 2.dp else 0.dp,
                animationSpec = tween(durationMillis = 180),
                label = "role_press_offset",
            )

            val currentShadowOffset by animateDpAsState(
                targetValue = if (isSelected) {
                    if (isPressed) 2.dp else 4.dp
                } else {
                    0.dp
                },
                animationSpec = tween(durationMillis = 180),
                label = "role_shadow_offset",
            )

            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surface
            }

            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            // In Login.png, selected role has a bold brick hard shadow
            val shadowColor = MaterialTheme.colorScheme.primary

            val roleLabel = when (role) {
                UserRole.STUDENT -> "Student"
                UserRole.DRIVER -> "Driver"
                UserRole.ADMIN -> "Admin"
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .offset(x = currentOffset, y = currentOffset)
                    .then(
                        if (isSelected) {
                            Modifier.hardShadow(
                                shape = ToggleShape,
                                offset = currentShadowOffset,
                                color = shadowColor,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(ToggleShape)
                    .background(containerColor)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), ToggleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onRoleSelected(role) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
            }
        }
    }
}

@Preview(name = "RoleToggle - Light")
@Composable
private fun RoleTogglePreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            RoleToggle(
                selectedRole = UserRole.STUDENT,
                onRoleSelected = {},
            )
        }
    }
}

@Preview(name = "RoleToggle - Dark")
@Composable
private fun RoleTogglePreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            RoleToggle(
                selectedRole = UserRole.STUDENT,
                onRoleSelected = {},
            )
        }
    }
}
