package com.letsconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.letsconnect.network.AuthApi
import com.example.letsconnect.network.AuthResult
import com.example.letsconnect.network.UserSession
import com.example.letsconnect.screens.LCFieldLabel
import com.example.letsconnect.screens.LCGradientButton
import com.example.letsconnect.screens.LCTextField
import com.example.letsconnect.ui.theme.CoolWhite
import com.example.letsconnect.ui.theme.Cyan500
import com.example.letsconnect.ui.theme.FairYellow
import com.example.letsconnect.ui.theme.Green600
import com.example.letsconnect.ui.theme.StrongGreen
import com.example.letsconnect.ui.theme.Teal600
import com.example.letsconnect.ui.theme.TextMuted
import com.example.letsconnect.ui.theme.WeakRed
import kotlinx.coroutines.launch

val SignUpHeaderGradient = Brush.linearGradient(
    colors = listOf(Green600, Teal600, Cyan500),
    start = Offset(Float.POSITIVE_INFINITY, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY)
)

fun passwordStrength(password: String): Pair<Int, String> {
    var score = 1
    if (password.length >= 8) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    val label = when (score) {
        1 -> "Weak"
        2    -> "Fair"
        3    -> "Strong"
        else -> "Very Strong"
    }
    return Pair(score, label)
}

@Composable
fun SignUp(
    onNavigateToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val keyboardHeightPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val isKeyboardOpen = keyboardHeightPx > 0
    val coroutineScope = rememberCoroutineScope()

    var fullName        by remember { mutableStateOf("") }
    var username        by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val usernameAvailable = username.length >= 3
    val (strengthScore, strengthLabel) = passwordStrength(password)
    val strengthColor = when (strengthScore) {
        1 -> WeakRed
        2    -> FairYellow
        3    -> Teal600
        else -> StrongGreen
    }

    fun submitSignUp() {
        val cleanFullName = fullName.trim()
        val cleanUsername = username.trim()
        if (cleanFullName.isBlank() || cleanUsername.isBlank() || password.isBlank()) {
            errorMessage = "Enter your full name, username, and password."
            return
        }
        if (cleanUsername.length < 3) {
            errorMessage = "Username must be at least 3 characters."
            return
        }

        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            when (val result = AuthApi.signUp(cleanFullName, cleanUsername, password)) {
                is AuthResult.Success -> {
                    UserSession.userId = result.userId
                    UserSession.fullName = result.fullName
                    UserSession.username = result.username
                    onSignUpSuccess()
                }
                is AuthResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── HEADER — 35% of screen ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .background(brush = SignUpHeaderGradient)
                .padding(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = (-50).dp, y = (-50).dp)
                    .align(Alignment.TopStart)
                    .background(
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .offset(x = 20.dp, y = 20.dp)
                    .align(Alignment.BottomEnd)
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = "Create Account",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 40.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Join Let's Connect today",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }

        // ── FORM — 65% of screen, scrollable ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .background(Color.White)
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState(), isKeyboardOpen) ){
            // Full Name
            LCFieldLabel("FULL NAME")
                Spacer(modifier = Modifier.height(10.dp))
            LCTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    errorMessage = null
                },
                placeholder = "e.g. John Doe",
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Teal600)
                }
            )
                Spacer(modifier = Modifier.height(12.dp))

            // Username
            LCFieldLabel("USERNAME")
                Spacer(modifier = Modifier.height(10.dp))

                LCTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                placeholder = "Username",
                leadingIcon = {
                    Icon(
                        Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = if (username.isNotEmpty()) Cyan500 else Teal600
                    )
                },
                trailingIcon = if (username.isNotEmpty() && usernameAvailable) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StrongGreen) }
                } else null
            )
            if (username.isNotEmpty()) {
                Text(
                    text = if (usernameAvailable) "Username looks good" else "Username too short",
                    fontSize = 11.sp,
                    color = if (usernameAvailable) StrongGreen else WeakRed,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
                Spacer(modifier = Modifier.height(12.dp))

            // Password
            LCFieldLabel("PASSWORD")
                Spacer(modifier = Modifier.height(10.dp))

                LCTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                placeholder = "Password",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Teal600)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = TextMuted
                        )
                    }
                }
            )
            // Strength bar
                Spacer(modifier = Modifier.height(7.dp))

                if (password.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(
                                    color = if (index < strengthScore) strengthColor
                                    else CoolWhite,
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                    }
                }
                Text(
                    text = strengthLabel,
                    fontSize = 11.sp,
                    color = strengthColor,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = WeakRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Create Account button
            LCGradientButton(
                text = if (isLoading) "Creating Account..." else "Create Account",
                enabled = !isLoading,
                onClick = { submitSignUp() }
            )

            // Login link
           // Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", fontSize = 14.sp)
                TextButton(
                    onClick = onNavigateToLogin,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "Sign In",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Teal600
                    )
                }
            }
        }
    }
}
