package com.example.sitconnect.features.messaging.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.messaging.domain.model.ChatMessage
import com.example.sitconnect.features.messaging.domain.model.ChatRoom
import com.example.sitconnect.features.messaging.domain.model.ChatRoomType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    messagingViewModel: MessagingViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val chatRoomsState by messagingViewModel.chatRoomsState.collectAsState()
    val selectedChatRoom by messagingViewModel.selectedChatRoom.collectAsState()

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            messagingViewModel.fetchChatRooms(uid, currentUser.email ?: "User")
        }
    }

    if (selectedChatRoom != null) {
        ChatRoomScreen(
            chatRoom = selectedChatRoom!!,
            currentUserId = currentUser?.uid ?: "",
            currentUserName = currentUser?.email ?: "User",
            messagingViewModel = messagingViewModel,
            onBack = { messagingViewModel.clearSelectedChatRoom() }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Messaging",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Join group discussions in chatrooms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (chatRoomsState) {
                is ChatRoomsState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ChatRoomsState.Success -> {
                    val chatRooms = (chatRoomsState as ChatRoomsState.Success).chatRooms

                    if (chatRooms.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No chat rooms available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chatRooms) { chatRoom ->
                                ChatRoomListItem(
                                    chatRoom = chatRoom,
                                    onClick = { messagingViewModel.selectChatRoom(chatRoom) }
                                )
                            }
                        }
                    }
                }
                is ChatRoomsState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: ${(chatRoomsState as ChatRoomsState.Error).message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is ChatRoomsState.Idle -> {
                    // Initial state
                }
            }
        }
    }
}

@Composable
fun ChatRoomListItem(
    chatRoom: ChatRoom,
    onClick: () -> Unit
) {
    val typeColor = when (chatRoom.type) {
        ChatRoomType.MODULE -> Color(0xFF2196F3)
        ChatRoomType.STUDY_GROUP -> Color(0xFF4CAF50)
        ChatRoomType.CLUB -> Color(0xFF9C27B0)
        ChatRoomType.GENERAL -> Color(0xFFFF9800)
    }

    val typeEmoji = when (chatRoom.type) {
        ChatRoomType.MODULE -> "📚"
        ChatRoomType.STUDY_GROUP -> "👥"
        ChatRoomType.CLUB -> "🎯"
        ChatRoomType.GENERAL -> "💬"
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeEmoji,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatRoom.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    chatRoom.lastMessageTime?.let { time ->
                        Text(
                            text = if (isToday(time)) timeFormat.format(time) else dateFormat.format(time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                chatRoom.lastMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = typeColor.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = chatRoom.type.name.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatRoom: ChatRoom,
    currentUserId: String,
    currentUserName: String,
    messagingViewModel: MessagingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messagesState by messagingViewModel.chatMessagesState.collectAsState()
    val sendState by messagingViewModel.sendMessageState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // File/image picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            // Get file name from URI
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        selectedFileName = c.getString(nameIndex)
                    }
                }
            }
            if (selectedFileName.isEmpty()) {
                selectedFileName = it.lastPathSegment ?: "attachment"
            }
        }
    }

    LaunchedEffect(messagesState) {
        if (messagesState is ChatMessagesState.Success) {
            val messages = (messagesState as ChatMessagesState.Success).messages
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(sendState) {
        if (sendState is SendMessageState.Success) {
            messageText = ""
            selectedFileUri = null
            selectedFileName = ""
            messagingViewModel.resetSendState()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chatRoom.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Messages
        when (messagesState) {
            is ChatMessagesState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ChatMessagesState.Success -> {
                val messages = (messagesState as ChatMessagesState.Success).messages

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { message ->
                        val isOwner = message.senderId == currentUserId || message.isCurrentUser
                        MessageBubble(
                            message = message,
                            isCurrentUser = isOwner,
                            onDelete = if (isOwner) {
                                { messagingViewModel.deleteMessage(chatRoom.id, message.id) }
                            } else null,
                            onEdit = if (isOwner && message.content.isNotEmpty() && !message.content.startsWith("📎")) {
                                { newContent -> messagingViewModel.editMessage(chatRoom.id, message.id, newContent) }
                            } else null
                        )
                    }
                }
            }
            is ChatMessagesState.Error -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error loading messages",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is ChatMessagesState.Idle -> {
                Box(modifier = Modifier.weight(1f))
            }
        }

        // Selected file preview
        if (selectedFileUri != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📎 $selectedFileName",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedFileUri = null
                        selectedFileName = ""
                    }) {
                        Text("Remove")
                    }
                }
            }
        }

        // Message Input
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment button
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = sendState !is SendMessageState.Loading
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 3,
                    enabled = sendState !is SendMessageState.Loading
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank() || selectedFileUri != null) {
                            messagingViewModel.sendMessageWithAttachment(
                                context = context,
                                chatRoomId = chatRoom.id,
                                content = messageText.trim(),
                                fileUri = selectedFileUri,
                                fileName = selectedFileName
                            )
                        }
                    },
                    enabled = (messageText.isNotBlank() || selectedFileUri != null) && sendState !is SendMessageState.Loading
                ) {
                    if (sendState is SendMessageState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank() || selectedFileUri != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    onDelete: (() -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        if (!isCurrentUser) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        Surface(
            color = if (isCurrentUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isCurrentUser) 4.dp else 16.dp
            ),
            modifier = if (isCurrentUser && (onDelete != null || onEdit != null)) {
                Modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { showOptionsDialog = true }
                )
            } else Modifier
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Display image attachment
                if (message.attachmentUrl.isNotEmpty() && message.attachmentType == "image") {
                    AsyncImage(
                        model = message.attachmentUrl,
                        contentDescription = "Image attachment",
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Display file attachment (non-image)
                if (message.attachmentUrl.isNotEmpty() && message.attachmentType != "image") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentUser)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (message.attachmentType) {
                                    "pdf" -> "📄"
                                    "video" -> "🎬"
                                    "audio" -> "🎵"
                                    else -> "📎"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.attachmentName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentUser)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Display text content if present
                if (message.content.isNotEmpty() && !message.content.startsWith("📎")) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentUser)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeFormat.format(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrentUser)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (message.isEdited) {
                        Text(
                            text = "• edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentUser)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // Options dialog (Edit/Delete) - Using a simpler bottom sheet style dialog
    if (showOptionsDialog && isCurrentUser) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onEdit != null && message.content.isNotEmpty() && !message.content.startsWith("📎")) {
                        Surface(
                            onClick = {
                                showOptionsDialog = false
                                showEditDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "✏️  Edit Message",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    if (onDelete != null) {
                        Surface(
                            onClick = {
                                showOptionsDialog = false
                                showDeleteDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "🗑️  Delete Message",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit dialog
    if (showEditDialog && onEdit != null) {
        var editedContent by remember { mutableStateOf(message.content) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedContent.isNotBlank()) {
                            onEdit(editedContent)
                            showEditDialog = false
                        }
                    },
                    enabled = editedContent.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun isToday(date: Date): Boolean {
    val today = Calendar.getInstance()
    val dateCalendar = Calendar.getInstance().apply { time = date }
    return today.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR)
}

