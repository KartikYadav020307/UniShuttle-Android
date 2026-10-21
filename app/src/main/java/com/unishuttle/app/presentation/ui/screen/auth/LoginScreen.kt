package com.unishuttle.app.presentation.ui.screen.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.domain.model.UserRole
import com.unishuttle.app.presentation.ui.component.RoleToggle
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow
import com.unishuttle.app.presentation.viewmodel.AuthViewModel

private val LogoContainerShape = RoundedCornerShape(36.dp)
private val GoogleButtonShape = RoundedCornerShape(999.dp)

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Activity context required by Credential Manager to anchor its system sheet
    val context = LocalContext.current

    // Persisted across recompositions so the chip stays selected on rotation
    var selectedRole by rememberSaveable { mutableStateOf(UserRole.STUDENT) }

    LoginContent(
        modifier = modifier,
        selectedRole = selectedRole,
        isLoading = uiState.isLoading,
        errorMessage = uiState.error,
        onRoleSelected = { role ->
            selectedRole = role
            viewModel.clearError()
        },
        onSignInClick = {
            viewModel.signInWithGoogle(context, selectedRole)
        },
    )
}

@Composable
fun LoginContent(
    selectedRole: UserRole,
    isLoading: Boolean,
    errorMessage: String?,
    onRoleSelected: (UserRole) -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // ── Top Shuttle Illustration Container ──
        Box(
            modifier = Modifier
                .size(164.dp)
                .hardShadow(
                    shape = LogoContainerShape,
                    offset = 5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
                .clip(LogoContainerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), LogoContainerShape),
            contentAlignment = Alignment.Center,
        ) {
            ShuttleIcon(
                modifier = Modifier.size(76.dp),
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ── Brand Title & Tagline ──
        Text(
            text = "UniShuttle",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.02).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Campus shuttles, live on your map.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(36.dp))

        // ── Role Selector ──
        Text(
            text = "I am a...",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Start,
        )

        RoleToggle(
            selectedRole = selectedRole,
            onRoleSelected = onRoleSelected,
            roles = listOf(UserRole.STUDENT, UserRole.DRIVER),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Role Requirement Helper Text ──
        Text(
            text = if (selectedRole == UserRole.STUDENT) {
                "Requires a @vitstudent.ac.in email"
            } else {
                "Any Google account is accepted"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(32.dp))

        // ── Sign in with Google Button ──
        GoogleSignInButton(
            isLoading = isLoading,
            onClick = onSignInClick,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Bottom Helper Note ──
        Text(
            text = "Only campus accounts can sign in.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // ── Optional Error Message ──
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Neo-brutalist Google sign-in button matching Login.png:
 * Pill container with white/surface fill, 2dp ink border, 4dp hard shadow,
 * and the signature yellow 'G' circle badge.
 */
@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && !isLoading) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "google_button_press_offset",
    )

    val currentShadowOffset by animateDpAsState(
        targetValue = if (isPressed && !isLoading) 2.dp else 4.dp,
        animationSpec = tween(durationMillis = 180),
        label = "google_button_shadow_offset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset(x = currentOffset, y = currentOffset)
            .hardShadow(
                shape = GoogleButtonShape,
                offset = currentShadowOffset,
                color = MaterialTheme.colorScheme.outline,
            )
            .clip(GoogleButtonShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), GoogleButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isLoading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.5.dp,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                // Yellow circle badge with black 'G'
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(UniColors.Sun)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "G",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        ),
                        color = UniColors.Ink,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Sign in with Google",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Custom canvas vector rendering the shuttle front from Login.png.
 */
@Composable
private fun ShuttleIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = 3.5.dp.toPx()
        val w = size.width
        val h = size.height

        // Outer chassis
        val bodyLeft = w * 0.12f
        val bodyTop = h * 0.08f
        val bodyWidth = w * 0.76f
        val bodyHeight = h * 0.66f
        val bodyCornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = bodyCornerRadius,
            style = Stroke(width = strokeWidthPx),
        )

        // Windshield
        val windowLeft = w * 0.22f
        val windowTop = h * 0.19f
        val windowWidth = w * 0.56f
        val windowHeight = h * 0.28f
        val windowCornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        drawRoundRect(
            color = color,
            topLeft = Offset(windowLeft, windowTop),
            size = Size(windowWidth, windowHeight),
            cornerRadius = windowCornerRadius,
            style = Stroke(width = strokeWidthPx * 0.85f),
        )

        // Headlights (filled circles)
        val lightRadius = 4.2.dp.toPx()
        val lightCenterY = h * 0.58f
        val leftLightCenterX = w * 0.33f
        val rightLightCenterX = w * 0.67f

        drawCircle(
            color = color,
            radius = lightRadius,
            center = Offset(leftLightCenterX, lightCenterY),
        )

        drawCircle(
            color = color,
            radius = lightRadius,
            center = Offset(rightLightCenterX, lightCenterY),
        )

        // Track legs (angled supports)
        val legLeftTop = Offset(w * 0.28f, bodyTop + bodyHeight)
        val legLeftBottom = Offset(w * 0.18f, h * 0.90f)
        drawLine(
            color = color,
            start = legLeftTop,
            end = legLeftBottom,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        val legRightTop = Offset(w * 0.72f, bodyTop + bodyHeight)
        val legRightBottom = Offset(w * 0.82f, h * 0.90f)
        drawLine(
            color = color,
            start = legRightTop,
            end = legRightBottom,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        // Base rail connecting legs
        val railStart = Offset(w * 0.14f, h * 0.90f)
        val railEnd = Offset(w * 0.86f, h * 0.90f)
        drawLine(
            color = color,
            start = railStart,
            end = railEnd,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }
}

// ── Light and Dark Previews ──

@Preview(name = "Login - Light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun LoginPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        LoginContent(
            selectedRole = UserRole.STUDENT,
            isLoading = false,
            errorMessage = null,
            onRoleSelected = {},
            onSignInClick = {},
        )
    }
}

@Preview(name = "Login - Dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun LoginPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        LoginContent(
            selectedRole = UserRole.STUDENT,
            isLoading = false,
            errorMessage = null,
            onRoleSelected = {},
            onSignInClick = {},
        )
    }
}
