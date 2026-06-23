package com.storelense.mobile.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storelense.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    vm: LoginViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    LaunchedEffect(vm.isAlreadyLoggedIn) {
        if (vm.isAlreadyLoggedIn) onLoginSuccess()
    }

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is LoginEvent.Success) onLoginSuccess() }
    }

    // ── Vibrant Background ──────────────────────────────────────────────────
    val backgroundBrush = Brush.verticalGradient(listOf(DeepNavy, Color(0xFF1E293B)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Subtle Background Accents
        Box(Modifier.size(300.dp).align(Alignment.TopEnd).offset(x = 100.dp, y = (-100).dp).background(EnergyEmerald.copy(0.05f), CircleShape))
        Box(Modifier.size(200.dp).align(Alignment.BottomStart).offset(x = (-50).dp, y = 50.dp).background(EnergyTeal.copy(0.05f), CircleShape))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Logo Section ────────────────────────────────────────────────
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = EnergyEmerald.copy(0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = EnergyEmerald,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "StoreLense",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = "Inventory Excellence",
                style = MaterialTheme.typography.bodyMedium,
                color = EnergyTeal,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(48.dp))

            // ── Inputs ──────────────────────────────────────────────────────
            VibrantInput(
                value = state.username,
                onValueChange = vm::onUsername,
                label = "Username",
                icon = Icons.Default.Person,
                imeAction = ImeAction.Next,
                onAction = { focus.moveFocus(FocusDirection.Down) }
            )
            
            Spacer(Modifier.height(16.dp))

            VibrantInput(
                value = state.password,
                onValueChange = vm::onPassword,
                label = "Password",
                icon = Icons.Default.Lock,
                isPassword = true,
                showPassword = showPass,
                onTogglePassword = { showPass = !showPass },
                imeAction = ImeAction.Done,
                onAction = { focus.clearFocus(); vm.login() }
            )

            Spacer(Modifier.height(12.dp))

            // Error Message
            Box(Modifier.height(24.dp)) {
                state.error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFB7185),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Sign In Button ──────────────────────────────────────────────
            Button(
                onClick = vm::login,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(18.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = !state.isLoading
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(EnergyEmerald, EnergyTeal))),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                    } else {
                        Text(
                            "SIGN IN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Secure Terminal • LK001",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText
            )
        }
    }
}

@Composable
fun VibrantInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: () -> Unit = {},
    imeAction: ImeAction,
    onAction: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = EnergyTeal, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MutedText
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EnergyEmerald,
            unfocusedBorderColor = SurfaceSlate,
            focusedLabelColor = EnergyEmerald,
            unfocusedLabelColor = MutedText,
            cursorColor = EnergyEmerald,
            focusedContainerColor = SurfaceSlate.copy(0.3f),
            unfocusedContainerColor = SurfaceSlate.copy(0.3f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}
