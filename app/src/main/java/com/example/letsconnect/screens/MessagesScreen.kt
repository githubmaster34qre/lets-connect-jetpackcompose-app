package com.example.letsconnect.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.letsconnect.network.AuthApi
import com.example.letsconnect.network.ExposedUser
import com.example.letsconnect.network.MessageResult
import com.example.letsconnect.network.MessagesApi
import com.example.letsconnect.network.UserSession
import com.example.letsconnect.ui.theme.Cyan500
import com.example.letsconnect.ui.theme.Cyan700
import com.example.letsconnect.ui.theme.Green600
import com.example.letsconnect.ui.theme.Teal600
import com.example.letsconnect.ui.theme.TextMuted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// ── Data ──────────────────────────────────────────────────────────────────────
data class Conversation(
    val id: Int,
    val userId: UInt, // Actual user ID from server
    val initials: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true,
    val gradientStart: Color,
    val gradientEnd: Color
)

private fun userStateKey(value: String): String = value.trim().lowercase(Locale.getDefault())

// ── Home Screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun Messages(
    onConversationClick: (Conversation) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lets_connect_saved_chats", Context.MODE_PRIVATE) }

    var isListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchFocused by remember { mutableStateOf(false) }
    var searchPressed by remember { mutableStateOf(false) }
    var composePressed by remember { mutableStateOf(false) }
    var micWaveActive by remember { mutableStateOf(false) }
    var micWaveProgress by remember { mutableFloatStateOf(0f) }
    var users by remember { mutableStateOf<List<ExposedUser>>(emptyList()) }
    var conversationLastMessages by remember { mutableStateOf<Map<UInt, String>>(emptyMap()) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var targetUsernameInput by remember { mutableStateOf("") }
    var isSendingRequest by remember { mutableStateOf(false) }

    var savedUserNames by remember {
        val initialSet = prefs.getStringSet("saved_user_names", null)
            ?: prefs.getStringSet("saved_user_ids", null)
            ?: emptySet()
        mutableStateOf(initialSet)
    }

    val blockedUserIds by remember {
        val initialSet = prefs.getStringSet("blocked_user_ids", null) ?: emptySet()
        mutableStateOf(initialSet)
    }

    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                speechRecognizer.startListening(speechIntent)
            } else {
                Toast.makeText(context, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                micWaveActive = true
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                micWaveActive = false
            }

            override fun onError(error: Int) {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) searchQuery = text
                isListening = false
                micWaveActive = false
            }

            override fun onPartialResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text != null) searchQuery = text
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose {
            speechRecognizer.destroy()
        }
    }

    // Automatically fetch users & check for incoming message conversations
    LaunchedEffect(Unit) {
        scope.launch {
            val fetchedUsers = AuthApi.getUsers()
            users = fetchedUsers

            val currentUserId = UserSession.userId ?: 1u
            val newDiscoveredSavedNames = savedUserNames.toMutableSet()
            val msgMap = mutableMapOf<UInt, String>()

            // Check conversations for all other users to automatically discover incoming requests/chats
            fetchedUsers.filter { it.username != UserSession.username }.forEach { otherUser ->
                val res = MessagesApi.getConversation(currentUserId, otherUser.id)
                if (res is MessageResult.SuccessList && res.messages.isNotEmpty()) {
                    newDiscoveredSavedNames.add(otherUser.username)
                    val lastMsg = res.messages.lastOrNull()?.content ?: "Tap to view conversation"
                    msgMap[otherUser.id] = lastMsg
                }
            }

            if (newDiscoveredSavedNames != savedUserNames) {
                savedUserNames = newDiscoveredSavedNames
                prefs.edit().putStringSet("saved_user_names", newDiscoveredSavedNames).apply()
            }
            conversationLastMessages = msgMap
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            micWaveActive = false
        }
    }

    LaunchedEffect(micWaveActive) {
        if (micWaveActive) {
            var startTime = 0L
            while (isActive && micWaveActive) {
                withFrameNanos { frameTime ->
                    if (startTime == 0L) startTime = frameTime
                    val elapsedMillis = (frameTime - startTime) / 1_000_000f
                    micWaveProgress = (elapsedMillis % 1800f) / 1800f
                }
            }
        } else {
            micWaveProgress = 0f
        }
    }

    LaunchedEffect(isPressed) {
        composePressed = isPressed
    }

    // Filter conversations for main screen: show saved & incoming chats, exclude blocked users
    val savedConversations = remember(users, savedUserNames, blockedUserIds, conversationLastMessages) {
        val otherUsers = users.filter {
            val stateKey = userStateKey(it.username)
            it.username != UserSession.username &&
                !blockedUserIds.contains(it.id.toString()) &&
                !blockedUserIds.contains(stateKey)
        }
        val activeSavedNames = if (savedUserNames.isEmpty() && otherUsers.isNotEmpty()) {
            val defaults = otherUsers.take(2).map { it.username }.toSet()
            prefs.edit().putStringSet("saved_user_names", defaults).apply()
            defaults
        } else {
            savedUserNames
        }

        otherUsers
            .filter { activeSavedNames.contains(it.username) }
            .mapIndexed { index, user ->
                val initials = user.username.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                val colors = listOf(
                    Color(0xFF06b6d4) to Color(0xFF0d9488),
                    Color(0xFF0d9488) to Color(0xFF059669),
                    Color(0xFF22d3ee) to Color(0xFF0891b2),
                    Color(0xFF34d399) to Color(0xFF0d9488),
                    Color(0xFF22d3ee) to Color(0xFF059669),
                    Color(0xFFa7f3d0) to Color(0xFF34d399),
                    Color(0xFF67e8f9) to Color(0xFF0d9488)
                )
                val (gradientStart, gradientEnd) = colors[index % colors.size]
                val lastMsgText = conversationLastMessages[user.id] ?: "Tap to view conversation"
                Conversation(
                    id = user.id.toInt(),
                    userId = user.id,
                    initials = initials.ifEmpty { "LC" },
                    name = user.username,
                    lastMessage = lastMsgText,
                    time = "Now",
                    unreadCount = if (index == 0) 1 else 0,
                    isOnline = index % 2 == 0,
                    gradientStart = gradientStart,
                    gradientEnd = gradientEnd
                )
            }
    }

    val searchScale by animateFloatAsState(
        targetValue = if (searchPressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "searchScale"
    )
    val composeScale by animateFloatAsState(
        targetValue = if (composePressed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "composeScale"
    )

    val filtered = remember(searchQuery, savedConversations, selectedFilter) {
        val baseList = when (selectedFilter) {
            "Unread" -> savedConversations.filter { it.unreadCount > 0 }
            "Online" -> savedConversations.filter { it.isOnline }
            else -> savedConversations
        }
        if (searchQuery.isEmpty()) baseList
        else baseList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
    }

    val barHeight = 50.dp

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── HEADER ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp)
                    .padding(top = 52.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Let's Connect",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan700,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── CONVERSATION LIST ──────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No results for \"$searchQuery\"" else "No $selectedFilter chats. Tap '+' to find a user!",
                                fontSize = 15.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { convo ->
                        SwipeableConversationRow(
                            conversation = convo,
                            onClick = { onConversationClick(convo) },
                            onDelete = {
                                val currentUserId = UserSession.userId ?: 1u
                                val updatedSaved = savedUserNames - convo.name
                                savedUserNames = updatedSaved
                                conversationLastMessages = conversationLastMessages - convo.userId
                                prefs.edit().putStringSet("saved_user_names", updatedSaved).apply()

                                scope.launch {
                                    val result = MessagesApi.deleteConversation(currentUserId, convo.userId)
                                    if (result is MessageResult.Error) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        HorizontalDivider(
                            color = Color(0xFFF0F9F9),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 82.dp, end = 18.dp)
                        )
                    }
                }
            }
        }

        // ── FLOATING BOTTOM ROW ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── SEARCH BAR ────────────────────────────────────────────────
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .scale(searchScale)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(999.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFd8f5ee))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                searchPressed = true
                                tryAwaitRelease()
                                searchPressed = false
                            }
                        )
                    }
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Teal600,
                        modifier = Modifier.size(20.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { searchFocused = it.isFocused },
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Cyan700
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Teal600),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search saved chats", fontSize = 15.sp, color = Teal600)
                            }
                            innerTextField()
                        }
                    )

                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = if (isListening) Cyan700 else Teal600,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    if (isListening) {
                                        speechRecognizer.stopListening()
                                        speechRecognizer.cancel()
                                        isListening = false
                                        micWaveActive = false
                                        return@clickable
                                    }
                                    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                        Toast.makeText(context, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                        )

                }

                if (micWaveActive) {
                    val progress = micWaveProgress
                    val startX = maxWidth - 20.dp
                    val endX = (maxWidth / 2) - 10.dp
                    val barHeights = listOf(14.dp, 22.dp, 30.dp, 38.dp, 28.dp, 20.dp, 16.dp)

                    Box(modifier = Modifier.matchParentSize()) {
                        barHeights.forEachIndexed { index, height ->
                            val barProgress = (progress + index * 0.14f) % 1f
                            val animatedX = startX + ((endX - startX) * barProgress)
                            val alpha = (1f - barProgress).coerceIn(0f, 1f) * 0.95f
                            val heightPulse = 0.82f + (0.18f * kotlin.math.sin((progress * 6.28f) + index).coerceAtLeast(0f))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = animatedX)
                                    .width(4.dp)
                                    .height(height * heightPulse)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF0f766e).copy(alpha = alpha),
                                                Cyan500.copy(alpha = alpha)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // ── COMPOSE BUTTON — OPENS DIALOG TO ENTER USERNAME ─────────────────
            IconButton(
                onClick = {
                    if (searchQuery.isNotEmpty()) {
                      searchQuery = ""
                    }
                else {
                        targetUsernameInput = ""
                        showRequestDialog = true
                    }
                },
                interactionSource = interactionSource,
                modifier = Modifier
                    .size(48.dp)
                    .scale(composeScale)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF06b6d4).copy(alpha = 0.4f),
                        spotColor = Color(0xFF06b6d4).copy(alpha = 0.6f)
                    )
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Cyan500, Green600),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        ),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if(searchQuery.isEmpty()) Icons.Default.Edit else Icons.Filled.Close,
                    contentDescription = "New chat request",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── CONNECT REQUEST DIALOG ─────────────────────────────────────────
        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { showRequestDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Cyan500,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "New Conversation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cyan700
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Enter the username of the person you want to text:",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        OutlinedTextField(
                            value = targetUsernameInput,
                            onValueChange = { targetUsernameInput = it },
                            placeholder = { Text("Username", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0FDFF),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Cyan500,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = targetUsernameInput.isNotBlank() && !isSendingRequest,
                        onClick = {
                            val cleanInput = targetUsernameInput.trim()
                            val matchingUser = users.firstOrNull {
                                it.username.equals(cleanInput, ignoreCase = true) && it.username != UserSession.username
                            }

                            if (matchingUser == null) {
                                Toast.makeText(context, "No user found with username '$cleanInput'", Toast.LENGTH_SHORT).show()
                            } else {
                                isSendingRequest = true
                                scope.launch {
                                    val currentUserId = UserSession.userId ?: 1u
                                    val currentUsername = UserSession.username ?: "User"
                                    val res = MessagesApi.sendMessage(
                                        senderId = currentUserId,
                                        receiverId = matchingUser.id,
                                        content = "$currentUsername wants to text you!"
                                    )
                                    if (res is MessageResult.Success) {
                                        val updatedSet = savedUserNames + matchingUser.username
                                        savedUserNames = updatedSet
                                        prefs.edit().putStringSet("saved_user_names", updatedSet).apply()

                                        showRequestDialog = false
                                        isSendingRequest = false

                                        val initials = matchingUser.username.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase().ifEmpty { "LC" }
                                        val convo = Conversation(
                                            id = matchingUser.id.toInt(),
                                            userId = matchingUser.id,
                                            initials = initials,
                                            name = matchingUser.username,
                                            lastMessage = "Sent connection request",
                                            time = "Now",
                                            unreadCount = 0,
                                            isOnline = true,
                                            gradientStart = Color(0xFF06b6d4),
                                            gradientEnd = Color(0xFF0d9488)
                                        )
                                        onConversationClick(convo)
                                    } else {
                                        Toast.makeText(context, "Failed to send request", Toast.LENGTH_SHORT).show()
                                        isSendingRequest = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(if (isSendingRequest) "Sending..." else "Send Request", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRequestDialog = false }) {
                        Text("Cancel", color = Teal600)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }

    }
}

// ── Conversation Row & Swipe To Delete ──────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    val density = LocalDensity.current

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val offsetPx = try {
                kotlin.math.abs(dismissState.requireOffset())
            } catch (_: Exception) {
                0f
            }
            val swipeWidthDp = with(density) { offsetPx.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (swipeWidthDp > 4.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(swipeWidthDp)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            if (swipeWidthDp >= 32.dp) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (swipeWidthDp >= 78.dp) {
                                Text(
                                    text = "Delete",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        ConversationRow(
            conversation = conversation,
            onClick = onClick
        )
    }
}

@Composable
fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(conversation.gradientStart, conversation.gradientEnd),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.initials,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            if (conversation.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF22c55e))
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Cyan700
                )
                Text(
                    text = conversation.time,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.lastMessage,
                fontSize = 13.sp,
                color = Color(0xFF64748b),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (conversation.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Cyan500, Teal600),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.unreadCount.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
