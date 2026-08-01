package com.example.letsconnect.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.letsconnect.network.AuthApi
import com.example.letsconnect.network.AuthResult
import com.example.letsconnect.network.UserSession
import com.example.letsconnect.ui.theme.BorderColor
import com.example.letsconnect.ui.theme.Cyan500
import com.example.letsconnect.ui.theme.Cyan700
import com.example.letsconnect.ui.theme.Green600
import com.example.letsconnect.ui.theme.Surface_
import com.example.letsconnect.ui.theme.Teal600
import com.example.letsconnect.ui.theme.TextMuted
import kotlinx.coroutines.launch
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.example.letsconnect.R
import com.example.letsconnect.ui.theme.WeakRed

sealed interface GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
    object Canceled : GoogleSignInResult
}

suspend fun triggerGoogleSignIn(context: Context): GoogleSignInResult {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId("252830645568-t3hahq6j1oco2l2aagtpe14ub2730vhn.apps.googleusercontent.com")
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            GoogleSignInResult.Success(credential.idToken)
        } else if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(googleIdTokenCredential.idToken)
        } else {
            GoogleSignInResult.Error("Unsupported credential type: ${credential.type}")
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleSignInResult.Canceled
    } catch (e: Exception) {
        e.printStackTrace()
        GoogleSignInResult.Error(e.localizedMessage ?: e.toString())
    }
}

val HeaderGradient = Brush.linearGradient(
    colors = listOf(Cyan500, Teal600, Green600),
    start = Offset(Float.POSITIVE_INFINITY, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY)
)

val ButtonGradient = Brush.linearGradient(
    colors = listOf(Cyan500, Teal600, Green600),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, 0f)
)

@Composable
fun Login(
    onNavigateToSignUp: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun submitGoogleLogin(idToken: String) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            when (val result = AuthApi.googleLogin(idToken)) {
                is AuthResult.Success -> {
                    UserSession.userId = result.userId
                    UserSession.fullName = result.fullName
                    UserSession.username = result.username
                    onLoginSuccess()
                }
                is AuthResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                submitGoogleLogin(idToken)
            } else {
                errorMessage = "Could not retrieve Google ID Token."
            }
        } catch (e: ApiException) {
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                errorMessage = "Google Sign-In failed (${e.statusCode}): ${e.message}"
            }
        }
    }

    fun launchFullGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("252830645568-t3hahq6j1oco2l2aagtpe14ub2730vhn.apps.googleusercontent.com")
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    // Automatically trigger One Tap pop-up when screen opens
    LaunchedEffect(Unit) {
        when (val res = triggerGoogleSignIn(context)) {
            is GoogleSignInResult.Success -> submitGoogleLogin(res.idToken)
            is GoogleSignInResult.Error -> {}
            is GoogleSignInResult.Canceled -> {}
        }
    }

    fun submitLogin() {
        val cleanUsername = username.trim()
        if (cleanUsername.isBlank() || password.isBlank()) {
            errorMessage = "Enter your username and password."
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            when (val result = AuthApi.login(cleanUsername, password)) {
                is AuthResult.Success -> {
                    UserSession.userId = result.userId
                    UserSession.fullName = result.fullName
                    UserSession.username = result.username
                    onLoginSuccess()
                }
                is AuthResult.Error -> errorMessage = result.message
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .background(brush = HeaderGradient)
                .padding(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = 60.dp, y = (-60).dp)
                    .align(Alignment.TopEnd)
                    .background(
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .offset(x = (-20).dp, y = 20.dp)
                    .align(Alignment.BottomStart)
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = "Let's Connect",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 42.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sign in to your account",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .background(Color.White)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
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
                        isFocused = username.isNotEmpty(),
                    )
                }

                Column {
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
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Teal600) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = TextMuted
                                )
                            }
                        }
                    )
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = WeakRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LCGradientButton(
                    text = if (isLoading) "Signing In..." else "Sign In",
                    enabled = !isLoading,
                    onClick = { submitLogin() }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                    Text(
                        text = "OR",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderColor)
                }

                LCGoogleButton(
                    enabled = !isLoading,
                    onClick = {
                        errorMessage = null
                        launchFullGoogleSignIn()
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Don't have an account? ", color = Color.Black)
                    TextButton(
                        onClick = { onNavigateToSignUp() },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Sign Up",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Teal600
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LCFieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Teal600,
        letterSpacing = 0.06.sp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LCTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isFocused: Boolean = false,
    error: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Surface_,
            focusedContainerColor = Color(0xFFf0fdff),
            unfocusedBorderColor = BorderColor,
            focusedBorderColor = Cyan500,
            unfocusedLeadingIconColor = Teal600,
            focusedLeadingIconColor = Cyan500,
            unfocusedPlaceholderColor = TextMuted,
            focusedPlaceholderColor = TextMuted,
            unfocusedTextColor = Cyan700,
            focusedTextColor = Cyan700,
            cursorColor = Cyan700,
            errorBorderColor = WeakRed,
        ),
        placeholder = { Text(placeholder, fontSize = 15.sp) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
    )
}

@Composable
fun LCGradientButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var buttonPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 1.1f else 1.0f,
        label = "ButtonScaleAnimation"
    )

    val barHeight = 52.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .scale(buttonScale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = Color(0xFF0d9488).copy(alpha = 0.25f),
                spotColor = Color(0xFF0d9488).copy(alpha = 0.38f)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(brush = ButtonGradient)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        buttonPressed = true
                        tryAwaitRelease()
                        buttonPressed = false
                        onClick()
                    }
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
        )
    }
}

@Composable
fun LCGoogleButton(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var buttonPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 1.1f else 1f,
        label = "GoogleButtonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(buttonScale)
            .clip(RoundedCornerShape(999.dp))
            .background(Surface_)
            .border(1.dp, BorderColor, RoundedCornerShape(999.dp))
            .alpha(if (enabled) 0.9f else 0.5f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        buttonPressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            buttonPressed = false
                        }
                        onClick()
                    }
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.google__g__logo),
                contentDescription = "Google Logo",
                modifier = Modifier.padding(end = 10.dp)
            )
            Text(
                text = "Continue with Google",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Cyan700
            )
        }
    }
}
