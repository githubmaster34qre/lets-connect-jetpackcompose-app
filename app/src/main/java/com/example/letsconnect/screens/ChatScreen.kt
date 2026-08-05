package com.example.letsconnect.screens

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.letsconnect.network.Message
import com.example.letsconnect.network.MessageResult
import com.example.letsconnect.network.MessagesApi
import com.example.letsconnect.network.ServerConfig
import com.example.letsconnect.network.UserSession
import com.example.letsconnect.ui.theme.Cyan500
import com.example.letsconnect.ui.theme.Cyan700
import com.example.letsconnect.ui.theme.Green600
import com.example.letsconnect.ui.theme.Teal600
import com.example.letsconnect.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ── Audio Recorder Helper ──────────────────────────────────────────────────
class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(conversationTag: String): File? {
        val dir = File(context.filesDir, "voice_notes")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "vn_${conversationTag}_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            outputFile = null
            return null
        }
        return file
    }

    fun getAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) { 0 }
    }

    fun stopRecording(): File? {
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        return outputFile
    }

    fun cancelRecording() {
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

// ── Audio Player Helper ────────────────────────────────────────────────────
class AudioPlayerHelper {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf(false)
        private set
    var currentPlayingFile by mutableStateOf<String?>(null)
        private set

    fun play(filePath: String, onComplete: () -> Unit = {}) {
        stop()
        val helper = this
        player = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()
                helper.playing = true
                helper.currentPlayingFile = filePath
                setOnCompletionListener {
                    helper.playing = false
                    helper.currentPlayingFile = null
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                helper.playing = false
                helper.currentPlayingFile = null
            }
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: Exception) { }
        player?.release()
        player = null
        playing = false
        currentPlayingFile = null
    }

    fun togglePlayPause(filePath: String) {
        if (playing && currentPlayingFile == filePath) {
            stop()
        } else {
            play(filePath)
        }
    }
}

private fun userStateKey(value: String): String = value.trim().lowercase(Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Chat(
    modifier: Modifier = Modifier,
    conversationId: Int = 0,
    receiverId: UInt = 2u,
    receiverName: String = "Arjun Mehta",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lets_connect_saved_chats", Context.MODE_PRIVATE) }
    val receiverStateKey = remember(receiverName) { userStateKey(receiverName) }

    var blockedUserIds by remember {
        val set = prefs.getStringSet("blocked_user_ids", null) ?: emptySet()
        mutableStateOf(set)
    }
    var acceptedUserIds by remember {
        val set = prefs.getStringSet("accepted_user_ids", null) ?: emptySet()
        mutableStateOf(set)
    }

    var isBlocked by remember(blockedUserIds, receiverId, receiverStateKey) {
        mutableStateOf(blockedUserIds.contains(receiverId.toString()) || blockedUserIds.contains(receiverStateKey))
    }

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<UInt?>(null) }
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var composePressed by remember { mutableStateOf(false) }

    // Voice recording states
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var audioWaveProgress by remember { mutableFloatStateOf(0f) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }
    val audioRecorder = remember { AudioRecorderHelper(context) }
    val audioPlayer = remember { AudioPlayerHelper() }

    // Live amplitude bars for iMessage-style waveform
    var liveAmplitudes by remember { mutableStateOf(List(20) { 0f }) }

    val composeScale by animateFloatAsState(
        targetValue = if (composePressed) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "composeScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val receiverInitials = remember(receiverName) {
        receiverName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase().ifEmpty { "LC" }
    }

    // Permission launcher for audio recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = audioRecorder.startRecording("${conversationId}_${receiverId}")
            if (file != null) {
                currentRecordingFile = file
                isRecordingAudio = true
            } else {
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(conversationId) {
        scope.launch {
            val currentUserId = UserSession.userId ?: 1u
            val result = MessagesApi.getConversation(currentUserId, receiverId)
            if (result is MessageResult.SuccessList) {
                messages = result.messages
            }
        }
    }

    LaunchedEffect(isPressed) {
        composePressed = isPressed
    }

    // Audio recording timer & live amplitude sampling
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingDurationSec = 0
            // Duration counter
            scope.launch {
                while (isActive && isRecordingAudio) {
                    delay(1000L)
                    recordingDurationSec++
                }
            }
            // Live amplitude sampling for waveform animation
            while (isActive && isRecordingAudio) {
                val amp = audioRecorder.getAmplitude()
                val normalized = (amp / 32768f).coerceIn(0f, 1f)
                liveAmplitudes = (liveAmplitudes.drop(1) + normalized)
                delay(80L) // ~12.5 fps
            }
        } else {
            recordingDurationSec = 0
            liveAmplitudes = List(20) { 0f }
        }
    }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Check request status
    val currentUserId = UserSession.userId ?: 1u
    val requestMsg = remember(messages) {
        messages.firstOrNull { it.content.contains("wants to text you") || it.content.contains("wants to chat") }
    }
    val acceptMsg = remember(messages) {
        messages.firstOrNull { it.content.contains("Accepted connection request") || it.content.contains("accepted your request") }
    }

    val isAcceptedLocally = acceptedUserIds.contains(receiverId.toString()) || acceptedUserIds.contains(receiverStateKey)
    val isAccepted = acceptMsg != null || isAcceptedLocally

    val isSenderOfRequest = requestMsg != null && requestMsg.senderId == currentUserId
    val isReceiverOfRequest = requestMsg != null && requestMsg.senderId == receiverId
    val showRequestBox = isReceiverOfRequest && !isAccepted && !isBlocked
    val showPendingBox = isSenderOfRequest && !isAccepted && !isBlocked
    val isMessagingDisabled = (requestMsg != null && !isAccepted) || isBlocked

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                shadowElevation = 4.dp,
                color = Color.White
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
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
                                    text = receiverInitials,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = receiverName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan700
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isBlocked -> Color.Red
                                                    !isAccepted && requestMsg != null -> Color(0xFFF59E0B)
                                                    else -> Color(0xFF10b981)
                                                }
                                            )
                                    )
                                    Text(
                                        text = when {
                                            isBlocked -> "Blocked"
                                            !isAccepted && requestMsg != null -> "Pending request"
                                            else -> "Active now"
                                        },
                                        fontSize = 11.sp,
                                        color = when {
                                            isBlocked -> Color.Red
                                            !isAccepted && requestMsg != null -> Color(0xFFD97706)
                                            else -> Color(0xFF059669)
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Cyan700)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
        ) {
            // INCOMING REQUEST BOX (FOR RECEIVER: YES / NO)
            if (showRequestBox) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0FDFF),
                    border = BorderStroke(1.dp, Cyan500.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connection Request",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan700
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$receiverName wants to text you! Do you want to accept or block?",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // YES (Accept)
                            Button(
                                onClick = {
                                    val updatedAccepted = acceptedUserIds + setOf(receiverId.toString(), receiverStateKey)
                                    acceptedUserIds = updatedAccepted
                                    prefs.edit().putStringSet("accepted_user_ids", updatedAccepted).apply()
                                    scope.launch {
                                        val res = MessagesApi.sendMessage(currentUserId, receiverId, "Accepted connection request! Let's chat.")
                                        if (res is MessageResult.Success) {
                                            messages = messages + res.message
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                            ) {
                                Text("Yes (Accept)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }

                            // NO (Block)
                            Button(
                                onClick = {
                                    val updatedBlocked = blockedUserIds + setOf(receiverId.toString(), receiverStateKey)
                                    blockedUserIds = updatedBlocked
                                    prefs.edit().putStringSet("blocked_user_ids", updatedBlocked).apply()
                                    isBlocked = true
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text("No (Block)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // PENDING WAIT BOX (FOR SENDER)
            if (showPendingBox) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "⏳",
                            fontSize = 22.sp
                        )
                        Column {
                            Text(
                                text = "Pending Connection Request",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "Waiting for $receiverName to accept your chat request. You cannot send messages until accepted.",
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFE2E8F0).copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isFromCurrentUser = message.senderId == currentUserId,
                        context = context,
                        audioPlayer = audioPlayer,
                        showActions = actionMessage?.id == message.id,
                        onLongPress = { messageToManage ->
                            actionMessage = messageToManage
                        },
                        onEdit = { messageToEdit ->
                            if (messageToEdit.content.startsWith("VOICE_NOTE_")) {
                                Toast.makeText(context, "Voice messages can't be edited", Toast.LENGTH_SHORT).show()
                            } else {
                                editingMessageId = messageToEdit.id
                                messageText = messageToEdit.content
                            }
                            actionMessage = null
                        },
                        onDelete = { messageToDelete ->
                            val messageId = messageToDelete.id
                            actionMessage = null
                            if (messageId != null) {
                                scope.launch {
                                    val result = MessagesApi.deleteMessage(messageId)
                                    if (result is MessageResult.Success) {
                                        messages = messages.filterNot { it.id == messageId }
                                    } else if (result is MessageResult.Error) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // BOTTOM BAR: INPUT FIELD / VOICE RECORDING MODE / BLOCKED BANNER
            if (isBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    color = Color(0xFFFFFCFC),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF43F5E))
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFE4E6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blocked",
                                color = Color(0xFF881337),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "This person can’t send messages until you unblock them.",
                                color = Color(0xFF9F1239).copy(alpha = 0.80f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Surface(
                            onClick = {
                                val updatedBlocked = blockedUserIds - setOf(receiverId.toString(), receiverStateKey)
                                blockedUserIds = updatedBlocked
                                prefs.edit().putStringSet("blocked_user_ids", updatedBlocked).apply()
                                isBlocked = false
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFFFE4E6),
                            border = BorderStroke(1.dp, Color(0xFFFDA4AF))
                        ) {
                            Text(
                                text = "Unblock",
                                color = Color(0xFFE11D48),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            } else if (isMessagingDisabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFFBF7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Teal600,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSenderOfRequest) "Waiting for $receiverName to accept your request..." else "Accept the request above to reply.",
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                            Text(
                                text = "Messages stay locked until the connection is accepted.",
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else if (isRecordingAudio) {
                // ── iMESSAGE-STYLE VOICE RECORDING BAR ──────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel / Delete Button
                    IconButton(
                        onClick = {
                            audioRecorder.cancelRecording()
                            isRecordingAudio = false
                            currentRecordingFile = null
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel Recording",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Recording Animated Waveform Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFEFFBF7))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Pulsing red dot
                                val redDotAlpha by animateFloatAsState(
                                    targetValue = if (recordingDurationSec % 2 == 0) 1f else 0.3f,
                                    animationSpec = spring(dampingRatio = 0.7f),
                                    label = "redDot"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = redDotAlpha))
                                )
                                val minSec = String.format(Locale.getDefault(), "%d:%02d", recordingDurationSec / 60, recordingDurationSec % 60)
                                Text(
                                    text = minSec,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Cyan700
                                )
                            }

                            // Live Amplitude Waveform Bars (iMessage style)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                liveAmplitudes.forEach { amp ->
                                    val barH = (4 + 26 * amp).dp
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(barH)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(Cyan500, Teal600)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Send Audio Button
                    IconButton(
                        onClick = {
                            val file = audioRecorder.stopRecording()
                            isRecordingAudio = false
                            if (file != null && file.exists()) {
                                val durationText = String.format(Locale.getDefault(), "%d:%02d", recordingDurationSec / 60, recordingDurationSec % 60)
                                scope.launch {
                                    val result = MessagesApi.sendVoiceMessage(currentUserId, receiverId, file, durationText)
                                    if (result is MessageResult.Success) {
                                        messages = messages + result.message
                                        listState.animateScrollToItem(messages.size - 1)
                                        file.delete()
                                    } else if (result is MessageResult.Error) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            currentRecordingFile = null
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 10.dp,
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
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Voice Note",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // ── STANDARD TEXT INPUT BAR WITH MIC BUTTON ─────────────────
                editingMessageId?.let {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFEFFBF7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Editing message",
                                color = Cyan700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    editingMessageId = null
                                    messageText = ""
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Cancel", color = Teal600, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(999.dp),
                                ambientColor = Color(0xFF0d9488).copy(alpha = 0.22f),
                                spotColor = Color(0xFF0d9488).copy(alpha = 0.32f)
                            )
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFD8F5EE))
                            .border(BorderStroke(1.dp, Color(0xFFCCFBF1)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 16.dp),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = Cyan700
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Teal600),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (messageText.isEmpty()) {
                                        Text(
                                            "Type a message...",
                                            fontSize = 15.sp,
                                            color = Teal600.copy(alpha = 0.72f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )

                    // Send or Mic button
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                scope.launch {
                                    val editingId = editingMessageId
                                    val result = if (editingId != null) {
                                        MessagesApi.editMessage(editingId, messageText.trim())
                                    } else {
                                        MessagesApi.sendMessage(currentUserId, receiverId, messageText)
                                    }

                                    when (result) {

                                        is MessageResult.Success -> {
                                            if (editingId != null) {
                                                messages = messages.map {
                                                    if (it.id == editingId) result.message else it
                                                }
                                                editingMessageId = null
                                            } else {
                                                messages = messages + result.message
                                                listState.animateScrollToItem(messages.size - 1)
                                            }
                                            messageText = ""
                                        }
                                        is MessageResult.Error -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                        else -> Unit
                                    }
                                }
                            } else {
                                // Start audio recording (request permission first)
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        interactionSource = interactionSource,
                            modifier = Modifier
                                .size(48.dp)
                                .scale(composeScale)
                                .shadow(
                                    elevation = 10.dp,
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
                            imageVector = if (messageText.isBlank()) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun MessageBubble(
    message: Message,
    isFromCurrentUser: Boolean,
    context: Context = LocalContext.current,
    audioPlayer: AudioPlayerHelper? = null,
    showActions: Boolean = false,
    onLongPress: (Message) -> Unit = {},
    onEdit: (Message) -> Unit = {},
    onDelete: (Message) -> Unit = {}
) {
    val bubbleShape = if (isFromCurrentUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }
    val isVoiceNote = message.content.startsWith("VOICE_NOTE_URL:") || message.content.startsWith("VOICE_NOTE_DATA:")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (isFromCurrentUser && message.id != null) {
                            onLongPress(message)
                        }
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = if (isFromCurrentUser) 2.dp else 1.dp,
                        shape = bubbleShape,
                        ambientColor = if (isFromCurrentUser) Cyan500.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.05f),
                        spotColor = if (isFromCurrentUser) Cyan500.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f)
                    )
                    .background(
                        brush = if (isFromCurrentUser) {
                            Brush.linearGradient(
                                colors = listOf(Cyan500, Teal600),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        } else {
                            SolidColor(Color(0xFFF1F5F9))
                        },
                        shape = bubbleShape
                    )
                    .then(
                        if (!isFromCurrentUser) {
                            Modifier.border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)), bubbleShape)
                        } else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Column {
                    if (isVoiceNote) {
                        val isUrlVoiceNote = message.content.startsWith("VOICE_NOTE_URL:")
                        val rawContent = if (isUrlVoiceNote) {
                            message.content.removePrefix("VOICE_NOTE_URL:")
                        } else {
                            message.content.removePrefix("VOICE_NOTE_DATA:")
                        }
                        val parts = rawContent.split("|")
                        val audioSource = if (parts.size >= 2) parts[0] else ""
                        val duration = if (parts.size >= 2) parts[1] else "0:00"

                        var filePath = ""
                        if (isUrlVoiceNote) {
                            filePath = if (audioSource.startsWith("http")) {
                                audioSource
                            } else {
                                ServerConfig.BASE_URL.trimEnd('/') + audioSource
                            }
                        } else if (audioSource.isNotEmpty()) {
                            try {
                                val bytes = android.util.Base64.decode(audioSource, android.util.Base64.DEFAULT)
                                val tempFile = File(context.cacheDir, "voice_note_${message.hashCode()}.m4a")
                                if (!tempFile.exists()) {
                                    tempFile.writeBytes(bytes)
                                }
                                filePath = tempFile.absolutePath
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        val fileExists = filePath.startsWith("http") || (filePath.isNotEmpty() && File(filePath).exists())
                        val isThisPlaying = audioPlayer?.playing == true && audioPlayer.currentPlayingFile == filePath

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFromCurrentUser) Color.White.copy(alpha = 0.25f) else Cyan500.copy(alpha = 0.15f)
                                    )
                                    .clickable(enabled = fileExists) {
                                        if (fileExists && audioPlayer != null) {
                                            audioPlayer.togglePlayPause(filePath)
                                        } else if (!fileExists) {
                                            Toast.makeText(context, "Voice note is not available", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Audio",
                                    tint = if (isFromCurrentUser) Color.White else Cyan700,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val barHeights = listOf(10, 18, 14, 24, 16, 28, 20, 12, 22, 16, 26, 14, 18, 10)
                                barHeights.forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(h.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (isFromCurrentUser) Color.White.copy(alpha = 0.85f) else Cyan500
                                            )
                                    )
                                }
                            }

                            Text(
                                text = duration,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFromCurrentUser) Color.White.copy(alpha = 0.9f) else Cyan700
                            )
                        }
                    } else {
                        Text(
                            text = message.content,
                            color = if (isFromCurrentUser) Color.White else Color(0xFF0F172A),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            color = if (isFromCurrentUser) Color.White.copy(alpha = 0.8f) else Color(0xFF64748B),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (isFromCurrentUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (showActions && isFromCurrentUser ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (-10).dp)
                        .padding(top = if (isVoiceNote) 90.dp else 67.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (!isVoiceNote) {
                        BubbleActionChip(
                            icon = Icons.Default.Edit,
                            label = "Edit",
                            tint = Color(0xFF0F766E),
                            background = Color(0xFFE6FFFB),
                            onClick = { onEdit(message) }
                        )
                    }
                    BubbleActionChip(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        tint = Color(0xFFE11D48),
                        background = Color(0xFFFFE4E6),
                        onClick = { onDelete(message) }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val instant = java.time.Instant.parse(timestamp)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            formatter.format(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()))
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp)
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            outputFormat.format(date!!)
        }
    } catch (e: Exception) {
        timestamp
    }
}

@Composable
private fun BubbleActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
