package com.example.sitconnect.features.messaging.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.UserDataState
import com.example.sitconnect.UserViewModel
import com.example.sitconnect.features.attendance.presentation.getCurrentLocation
import com.example.sitconnect.features.messaging.domain.model.ChatMessage
import com.example.sitconnect.features.messaging.domain.model.ChatRoom
import com.example.sitconnect.features.messaging.domain.model.ChatRoomType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    messagingViewModel: MessagingViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val userDataState by userViewModel.userDataState.collectAsState()
    val isAdmin = (userDataState as? UserDataState.Success)?.userData?.roles?.admin == true
    val isStudent = (userDataState as? UserDataState.Success)?.userData?.roles?.student == true
    val userName = (userDataState as? UserDataState.Success)?.userData?.name ?: "User"
    val chatRoomsState by messagingViewModel.chatRoomsState.collectAsState()
    val selectedChatRoom by messagingViewModel.selectedChatRoom.collectAsState()
    val createGroupState by messagingViewModel.createGroupState.collectAsState()
    val deleteGroupState by messagingViewModel.deleteGroupState.collectAsState()
    val directMessageState by messagingViewModel.directMessageState.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showNewDMDialog by remember { mutableStateOf(false) }
    var chatRoomToDelete by remember { mutableStateOf<ChatRoom?>(null) }

    // Fetch user data to determine role
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Fetch chat rooms once role is known
    LaunchedEffect(currentUser?.uid, userDataState) {
        if (currentUser?.uid != null && userDataState is UserDataState.Success) {
            messagingViewModel.fetchChatRooms(
                currentUser.uid,
                userName,
                isAdmin
            )
        }
    }

    // Dismiss create group dialog on success
    LaunchedEffect(createGroupState) {
        if (createGroupState is CreateGroupState.Success) {
            showCreateGroupDialog = false
            messagingViewModel.resetCreateGroupState()
        }
    }

    // Reset delete state on success
    LaunchedEffect(deleteGroupState) {
        if (deleteGroupState is DeleteGroupState.Success) {
            chatRoomToDelete = null
            messagingViewModel.resetDeleteGroupState()
        }
    }

    // Dismiss DM dialog on success
    LaunchedEffect(directMessageState) {
        if (directMessageState is DirectMessageState.Success) {
            showNewDMDialog = false
            messagingViewModel.resetDirectMessageState()
        }
    }

    if (selectedChatRoom != null) {
        // key() forces fresh composition when switching rooms (e.g. opening a DM from member profile)
        key(selectedChatRoom!!.id) {
            ChatRoomScreen(
                chatRoom = selectedChatRoom!!,
                currentUserId = currentUser?.uid ?: "",
                currentUserName = userName,
                messagingViewModel = messagingViewModel,
                isReadOnly = isStudent && selectedChatRoom!!.type == ChatRoomType.GENERAL,
                isAdmin = isAdmin,
                onBack = { messagingViewModel.clearSelectedChatRoom() }
            )
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
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
                    text = "Group chats and direct messages",
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
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 80.dp) // space for FAB
                            ) {
                                items(chatRooms) { chatRoom ->
                                    ChatRoomListItem(
                                        chatRoom = chatRoom,
                                        onClick = { messagingViewModel.selectChatRoom(chatRoom) },
                                        isAdmin = isAdmin,
                                        onDelete = { chatRoomToDelete = chatRoom }
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

            // FABs
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Admin: Create group FAB
                if (isAdmin) {
                    FloatingActionButton(
                        onClick = { showCreateGroupDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Group",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // All users: New Direct Message FAB
                FloatingActionButton(
                    onClick = {
                        messagingViewModel.fetchAllUsersForDM()
                        showNewDMDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "New Direct Message",
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }

        // Create Group Dialog
        if (showCreateGroupDialog && isAdmin) {
            CreateGroupDialog(
                createGroupState = createGroupState,
                messagingViewModel = messagingViewModel,
                onDismiss = {
                    showCreateGroupDialog = false
                    messagingViewModel.resetCreateGroupState()
                },
                onCreate = { name, description, type, selectedModuleCodes ->
                    messagingViewModel.createChatRoom(name, description, type, selectedModuleCodes)
                }
            )
        }

        // Delete Chat Confirmation Dialog (groups for admin, DMs for any user)
        chatRoomToDelete?.let { chatRoom ->
            val isDM = chatRoom.type == ChatRoomType.DIRECT_MESSAGE
            AlertDialog(
                onDismissRequest = {
                    if (deleteGroupState !is DeleteGroupState.Loading) {
                        chatRoomToDelete = null
                        messagingViewModel.resetDeleteGroupState()
                    }
                },
                title = { Text(if (isDM) "Remove Conversation" else "Delete Group") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (isDM)
                                "Are you sure you want to remove your conversation with \"${chatRoom.name}\"? All messages will be permanently deleted for both users."
                            else
                                "Are you sure you want to delete \"${chatRoom.name}\"? All messages in this group will be permanently deleted."
                        )
                        if (deleteGroupState is DeleteGroupState.Error) {
                            Text(
                                text = (deleteGroupState as DeleteGroupState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { messagingViewModel.deleteChatRoom(chatRoom) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = deleteGroupState !is DeleteGroupState.Loading
                    ) {
                        if (deleteGroupState is DeleteGroupState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Text(if (isDM) "Remove" else "Delete")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            chatRoomToDelete = null
                            messagingViewModel.resetDeleteGroupState()
                        },
                        enabled = deleteGroupState !is DeleteGroupState.Loading
                    ) {
                    Text("Cancel")
                }
            }
            )
        }

        // New Direct Message Dialog
        if (showNewDMDialog) {
            NewDirectMessageDialog(
                messagingViewModel = messagingViewModel,
                onDismiss = {
                    showNewDMDialog = false
                    messagingViewModel.resetDirectMessageState()
                    messagingViewModel.resetAllUsersState()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRoomListItem(
    chatRoom: ChatRoom,
    onClick: () -> Unit,
    isAdmin: Boolean = false,
    onDelete: () -> Unit = {}
) {
    val typeColor = when (chatRoom.type) {
        ChatRoomType.MODULE -> Color(0xFF2196F3)
        ChatRoomType.STUDY_GROUP -> Color(0xFF4CAF50)
        ChatRoomType.CLUB -> Color(0xFF9C27B0)
        ChatRoomType.GENERAL -> Color(0xFFFF9800)
        ChatRoomType.DIRECT_MESSAGE -> Color(0xFF00BCD4)
    }

    val typeEmoji = when (chatRoom.type) {
        ChatRoomType.MODULE -> "📚"
        ChatRoomType.STUDY_GROUP -> "👥"
        ChatRoomType.CLUB -> "🎯"
        ChatRoomType.GENERAL -> "💬"
        ChatRoomType.DIRECT_MESSAGE -> "✉️"
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isAdmin || chatRoom.type == ChatRoomType.DIRECT_MESSAGE) {
                    Modifier.combinedClickable(
                        onClick = { onClick() },
                        onLongClick = { onDelete() }
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            ),
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
                if (chatRoom.type == ChatRoomType.DIRECT_MESSAGE) {
                    Text(
                        text = chatRoom.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = typeEmoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatRoomScreen(
    chatRoom: ChatRoom,
    currentUserId: String,
    currentUserName: String,
    messagingViewModel: MessagingViewModel,
    isReadOnly: Boolean = false,
    isAdmin: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messagesState by messagingViewModel.chatMessagesState.collectAsState()
    val sendState by messagingViewModel.sendMessageState.collectAsState()
    val membersState by messagingViewModel.membersState.collectAsState()
    val availableUsersState by messagingViewModel.availableUsersState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showMembersDialog by remember { mutableStateOf(false) }
    var showMemberProfile by remember { mutableStateOf<ChatRoomMember?>(null) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showDeleteDMDialog by remember { mutableStateOf(false) }
    val deleteGroupState by messagingViewModel.deleteGroupState.collectAsState()
    var isFetchingLocation by remember { mutableStateOf(false) }

    // Location permission state
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

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
                    if (chatRoom.type == ChatRoomType.DIRECT_MESSAGE) {
                        Text(
                            text = "Direct message",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Members button (hide for DMs)
                if (chatRoom.type != ChatRoomType.DIRECT_MESSAGE) {
                    IconButton(onClick = {
                        messagingViewModel.fetchChatRoomMembers(chatRoom)
                        showMembersDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "View Members"
                        )
                    }
                }

                // Delete button for DMs
                if (chatRoom.type == ChatRoomType.DIRECT_MESSAGE) {
                    IconButton(onClick = { showDeleteDMDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Conversation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
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
                        val isDM = chatRoom.type == ChatRoomType.DIRECT_MESSAGE
                        MessageBubble(
                            message = message,
                            isCurrentUser = isOwner,
                            isAdmin = isAdmin,
                            onDelete = if (isOwner || isAdmin || isDM) {
                                { messagingViewModel.deleteMessage(chatRoom.id, message.id) }
                            } else null,
                            onEdit = if (isOwner && message.content.isNotEmpty() && !message.content.startsWith("📎") && message.attachmentType != "location") {
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
        if (!isReadOnly && selectedFileUri != null) {
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

        if (isReadOnly) {
            // Read-only notice for students in announcement channels
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📢 This is an announcement channel. Only admins and lecturers can send messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
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
                        enabled = sendState !is SendMessageState.Loading && !isFetchingLocation
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Location share button
                    IconButton(
                        onClick = {
                            if (locationPermissions.allPermissionsGranted) {
                                isFetchingLocation = true
                                scope.launch {
                                    val location = getCurrentLocation(context)
                                    if (location != null) {
                                        messagingViewModel.sendLocationMessage(
                                            chatRoomId = chatRoom.id,
                                            latitude = location.first,
                                            longitude = location.second
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Could not get location. Ensure GPS is enabled.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isFetchingLocation = false
                                }
                            } else {
                                locationPermissions.launchMultiplePermissionRequest()
                            }
                        },
                        enabled = sendState !is SendMessageState.Loading && !isFetchingLocation
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Share location",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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

    // Members Dialog
    if (showMembersDialog) {
        MembersDialog(
            membersState = membersState,
            isAdmin = isAdmin,
            isModuleRoom = chatRoom.type == ChatRoomType.MODULE,
            onDismiss = {
                showMembersDialog = false
                messagingViewModel.clearMembersState()
            },
            onMemberClick = { member -> showMemberProfile = member },
            onRemoveMember = { member ->
                chatRoom.moduleCode?.let { code ->
                    messagingViewModel.removeMemberFromModule(code, member.uid)
                }
            },
            onAddMemberClick = {
                chatRoom.moduleCode?.let { code ->
                    messagingViewModel.fetchAvailableUsersForModule(code)
                }
                showAddMemberDialog = true
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog && isAdmin && chatRoom.type == ChatRoomType.MODULE) {
        AddMemberDialog(
            availableUsersState = availableUsersState,
            onDismiss = { showAddMemberDialog = false },
            onAddMember = { userUid ->
                chatRoom.moduleCode?.let { code ->
                    messagingViewModel.addMemberToModule(code, userUid)
                }
            }
        )
    }

    // Member Profile Dialog
    showMemberProfile?.let { member ->
        MemberProfileDialog(
            member = member,
            onDismiss = { showMemberProfile = null },
            onSendDirectMessage = { userId, userName ->
                messagingViewModel.createOrOpenDirectMessage(userId, userName)
            }
        )
    }

    // Delete DM Confirmation Dialog
    if (showDeleteDMDialog && chatRoom.type == ChatRoomType.DIRECT_MESSAGE) {
        AlertDialog(
            onDismissRequest = {
                if (deleteGroupState !is DeleteGroupState.Loading) {
                    showDeleteDMDialog = false
                    messagingViewModel.resetDeleteGroupState()
                }
            },
            title = { Text("Remove Conversation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to remove your conversation with \"${chatRoom.name}\"? All messages will be permanently deleted for both users.")
                    if (deleteGroupState is DeleteGroupState.Error) {
                        Text(
                            text = (deleteGroupState as DeleteGroupState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { messagingViewModel.deleteChatRoom(chatRoom) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = deleteGroupState !is DeleteGroupState.Loading
                ) {
                    if (deleteGroupState is DeleteGroupState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Remove")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDMDialog = false
                        messagingViewModel.resetDeleteGroupState()
                    },
                    enabled = deleteGroupState !is DeleteGroupState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MembersDialog(
    membersState: MembersState,
    isAdmin: Boolean = false,
    isModuleRoom: Boolean = false,
    onDismiss: () -> Unit,
    onMemberClick: (ChatRoomMember) -> Unit,
    onRemoveMember: (ChatRoomMember) -> Unit = {},
    onAddMemberClick: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Members")
                if (isAdmin && isModuleRoom) {
                    IconButton(onClick = onAddMemberClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Member"
                        )
                    }
                }
            }
        },
        text = {
            when (membersState) {
                is MembersState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is MembersState.Success -> {
                    val members = membersState.members
                    if (members.isEmpty()) {
                        Text(
                            text = "No members found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            Text(
                                text = "${members.size} member${if (members.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 400.dp)
                            ) {
                                items(members) { member ->
                                    MemberListItem(
                                        member = member,
                                        onClick = { onMemberClick(member) },
                                        showRemoveButton = isAdmin && isModuleRoom && member.role != "Lecturer" && member.role != "Admin",
                                        onRemove = { onRemoveMember(member) }
                                    )
                                }
                            }
                        }
                    }
                }
                is MembersState.Error -> {
                    Text(
                        text = "Error: ${membersState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is MembersState.Idle -> {
                    // Initial state
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MemberListItem(
    member: ChatRoomMember,
    onClick: () -> Unit,
    showRemoveButton: Boolean = false,
    onRemove: () -> Unit = {}
) {
    val roleColor = when (member.role) {
        "Admin" -> MaterialTheme.colorScheme.error
        "Lecturer" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    var showRemoveConfirm by remember { mutableStateOf(false) }

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
            // Avatar initial
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(roleColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleSmall,
                    color = roleColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = roleColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = member.role,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = roleColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showRemoveButton) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Member") },
            text = { Text("Remove ${member.name} from this module?") },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showRemoveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddMemberDialog(
    availableUsersState: AvailableUsersState,
    onDismiss: () -> Unit,
    onAddMember: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Member") },
        text = {
            when (availableUsersState) {
                is AvailableUsersState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AvailableUsersState.Success -> {
                    val users = availableUsersState.users
                    if (users.isEmpty()) {
                        Text(
                            text = "No available users to add",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            items(users) { user ->
                                val roleColor = when (user.role) {
                                    "Lecturer" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
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
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(roleColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.name.firstOrNull()?.uppercase() ?: "?",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = roleColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${user.email} • ${user.role}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Button(
                                            onClick = { onAddMember(user.uid) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text("Add", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is AvailableUsersState.Error -> {
                    Text(
                        text = "Error: ${availableUsersState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is AvailableUsersState.Idle -> {}
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MemberProfileDialog(
    member: ChatRoomMember,
    onDismiss: () -> Unit,
    onSendDirectMessage: ((String, String) -> Unit)? = null
) {
    val roleColor = when (member.role) {
        "Admin" -> MaterialTheme.colorScheme.error
        "Lecturer" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(roleColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = roleColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Role badge
                Surface(
                    color = roleColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = member.role,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = roleColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Email card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Email",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = member.email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Send Direct Message button
                if (onSendDirectMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSendDirectMessage(member.uid, member.name)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00BCD4)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Direct Message")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    isAdmin: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
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
            modifier = if ((isCurrentUser && (onDelete != null || onEdit != null)) || (isAdmin && onDelete != null)) {
                Modifier.combinedClickable(
                    onClick = {
                        if (message.attachmentType == "location" && message.latitude != null && message.longitude != null) {
                            try {
                                val geoUri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}(Shared+Location)")
                                val intent = Intent(Intent.ACTION_VIEW, geoUri)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        } else if (message.attachmentUrl.isNotEmpty()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.attachmentUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                    },
                    onLongClick = { showOptionsDialog = true }
                )
            } else if (message.attachmentType == "location" && message.latitude != null && message.longitude != null) {
                Modifier.clickable {
                    try {
                        val geoUri = Uri.parse("geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}(Shared+Location)")
                        val intent = Intent(Intent.ACTION_VIEW, geoUri)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            } else if (message.attachmentUrl.isNotEmpty()) {
                // Non-owner/non-admin: still allow tap to open attachment
                Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.attachmentUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            } else Modifier
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Display location message
                if (message.attachmentType == "location" && message.latitude != null && message.longitude != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentUser)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "📍 Shared Location",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%.6f, %.6f", message.latitude, message.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap to open in Maps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

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
                            Column {
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
                                Text(
                                    text = "Tap to open",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrentUser)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Display text content if present (skip for location messages – already shown in card)
                if (message.content.isNotEmpty() && !message.content.startsWith("📎") && message.attachmentType != "location") {
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
    if (showOptionsDialog && (isCurrentUser || isAdmin)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    createGroupState: CreateGroupState,
    messagingViewModel: MessagingViewModel,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, type: ChatRoomType, selectedModuleCodes: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChatRoomType.GENERAL) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val selectedModuleCodes = remember { mutableStateListOf<String>() }

    val modulesListState by messagingViewModel.modulesListState.collectAsState()

    // Fetch modules when dialog opens
    LaunchedEffect(Unit) {
        messagingViewModel.fetchModulesForSelection()
    }

    AlertDialog(
        onDismissRequest = {
            if (createGroupState !is CreateGroupState.Loading) onDismiss()
        },
        title = { Text("Create New Group") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        groupName = it
                        nameError = null
                    },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g. Study Group") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = createGroupState !is CreateGroupState.Loading
                )

                OutlinedTextField(
                    value = groupDescription,
                    onValueChange = { groupDescription = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    enabled = createGroupState !is CreateGroupState.Loading
                )

                // Type selector
                Text(
                    text = "Group Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ChatRoomType.MODULE, ChatRoomType.GENERAL).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = {
                                    Text(
                                        text = when (type) {
                                            ChatRoomType.MODULE -> "📚 Module"
                                            ChatRoomType.GENERAL -> "💬 General"
                                            else -> type.name
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                enabled = createGroupState !is CreateGroupState.Loading
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(ChatRoomType.STUDY_GROUP, ChatRoomType.CLUB).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = {
                                    Text(
                                        text = when (type) {
                                            ChatRoomType.STUDY_GROUP -> "👥 Study Group"
                                            ChatRoomType.CLUB -> "🎯 Club"
                                            else -> type.name
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                enabled = createGroupState !is CreateGroupState.Loading
                            )
                        }
                    }
                }

                // Module selector
                Text(
                    text = "Link Modules (auto-add enrolled students)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (modulesListState) {
                    is ModulesListState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Loading modules...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is ModulesListState.Success -> {
                        val modules = (modulesListState as ModulesListState.Success).modules
                        if (modules.isEmpty()) {
                            Text(
                                text = "No modules available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(modules) { module ->
                                    val isSelected = module.code in selectedModuleCodes
                                    val studentCount = module.enrolledStudents.size
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = createGroupState !is CreateGroupState.Loading) {
                                                if (isSelected) {
                                                    selectedModuleCodes.remove(module.code)
                                                } else {
                                                    selectedModuleCodes.add(module.code)
                                                }
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    if (isSelected) {
                                                        selectedModuleCodes.remove(module.code)
                                                    } else {
                                                        selectedModuleCodes.add(module.code)
                                                    }
                                                },
                                                enabled = createGroupState !is CreateGroupState.Loading,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${module.code} ${module.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$studentCount student${if (studentCount != 1) "s" else ""} enrolled",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ModulesListState.Error -> {
                        Text(
                            text = "Failed to load modules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is ModulesListState.Idle -> {}
                }

                // Selected modules summary
                if (selectedModuleCodes.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📚 ${selectedModuleCodes.size} module${if (selectedModuleCodes.size != 1) "s" else ""} selected: ${selectedModuleCodes.joinToString(", ")}. " +
                                    "Enrolled students will be auto-added to the group.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Error from create state
                if (createGroupState is CreateGroupState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (createGroupState as CreateGroupState.Error).message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        nameError = "Group name is required"
                    } else {
                        onCreate(groupName.trim(), groupDescription.trim(), selectedType, selectedModuleCodes.toList())
                    }
                },
                enabled = createGroupState !is CreateGroupState.Loading
            ) {
                if (createGroupState is CreateGroupState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = createGroupState !is CreateGroupState.Loading
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDirectMessageDialog(
    messagingViewModel: MessagingViewModel,
    onDismiss: () -> Unit
) {
    val allUsersState by messagingViewModel.allUsersState.collectAsState()
    val directMessageState by messagingViewModel.directMessageState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (directMessageState !is DirectMessageState.Loading) onDismiss()
        },
        title = { Text("New Direct Message") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or email...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = directMessageState !is DirectMessageState.Loading
                )

                when (allUsersState) {
                    is AllUsersState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is AllUsersState.Success -> {
                        val users = (allUsersState as AllUsersState.Success).users
                        val filteredUsers = if (searchQuery.isBlank()) users
                        else users.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.email.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredUsers.isEmpty()) {
                            Text(
                                text = if (searchQuery.isBlank()) "No users available" else "No users match your search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 400.dp)
                            ) {
                                items(filteredUsers) { user ->
                                    val roleColor = when (user.role) {
                                        "Admin" -> MaterialTheme.colorScheme.error
                                        "Lecturer" -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = directMessageState !is DirectMessageState.Loading) {
                                                messagingViewModel.createOrOpenDirectMessage(user.uid, user.name)
                                            },
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
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(roleColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = user.name.firstOrNull()?.uppercase() ?: "?",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = roleColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${user.email} • ${user.role}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Email,
                                                contentDescription = "Message",
                                                tint = Color(0xFF00BCD4),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is AllUsersState.Error -> {
                        Text(
                            text = "Error: ${(allUsersState as AllUsersState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is AllUsersState.Idle -> {}
                }

                if (directMessageState is DirectMessageState.Loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Opening conversation...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (directMessageState is DirectMessageState.Error) {
                    Text(
                        text = (directMessageState as DirectMessageState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = directMessageState !is DirectMessageState.Loading
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun isToday(date: Date): Boolean {
    val today = Calendar.getInstance()
    val dateCalendar = Calendar.getInstance().apply { time = date }
    return today.get(Calendar.YEAR) == dateCalendar.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR)
}

