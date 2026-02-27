package com.example.sitconnect.features.messaging.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.messaging.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.*

sealed class ChatRoomsState {
    object Idle : ChatRoomsState()
    object Loading : ChatRoomsState()
    data class Success(val chatRooms: List<ChatRoom>) : ChatRoomsState()
    data class Error(val message: String) : ChatRoomsState()
}

sealed class ChatMessagesState {
    object Idle : ChatMessagesState()
    object Loading : ChatMessagesState()
    data class Success(val messages: List<ChatMessage>) : ChatMessagesState()
    data class Error(val message: String) : ChatMessagesState()
}

sealed class SendMessageState {
    object Idle : SendMessageState()
    object Loading : SendMessageState()
    object Success : SendMessageState()
    data class Error(val message: String) : SendMessageState()
}

data class ChatRoomMember(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = ""
)

sealed class MembersState {
    object Idle : MembersState()
    object Loading : MembersState()
    data class Success(val members: List<ChatRoomMember>) : MembersState()
    data class Error(val message: String) : MembersState()
}

sealed class AvailableUsersState {
    object Idle : AvailableUsersState()
    object Loading : AvailableUsersState()
    data class Success(val users: List<ChatRoomMember>) : AvailableUsersState()
    data class Error(val message: String) : AvailableUsersState()
}

class MessagingViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private val _chatRoomsState = MutableStateFlow<ChatRoomsState>(ChatRoomsState.Idle)
    val chatRoomsState: StateFlow<ChatRoomsState> = _chatRoomsState

    private val _chatMessagesState = MutableStateFlow<ChatMessagesState>(ChatMessagesState.Idle)
    val chatMessagesState: StateFlow<ChatMessagesState> = _chatMessagesState

    private val _sendMessageState = MutableStateFlow<SendMessageState>(SendMessageState.Idle)
    val sendMessageState: StateFlow<SendMessageState> = _sendMessageState

    private val _selectedChatRoom = MutableStateFlow<ChatRoom?>(null)
    val selectedChatRoom: StateFlow<ChatRoom?> = _selectedChatRoom

    private val _membersState = MutableStateFlow<MembersState>(MembersState.Idle)
    val membersState: StateFlow<MembersState> = _membersState

    private val _availableUsersState = MutableStateFlow<AvailableUsersState>(AvailableUsersState.Idle)
    val availableUsersState: StateFlow<AvailableUsersState> = _availableUsersState

    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var isAdminUser: Boolean = false

    fun fetchChatRooms(userId: String, userName: String, isAdmin: Boolean = false) {
        currentUserId = userId
        currentUserName = userName
        isAdminUser = isAdmin
        viewModelScope.launch {
            try {
                _chatRoomsState.value = ChatRoomsState.Loading

                withTimeout(15000L) {
                    val chatRooms = mutableListOf<ChatRoom>()

                    // 1) MODULE chat rooms — generated directly from modules collection
                    val allModulesSnapshot = firestore.collection("modules").get().await()

                    for (moduleDoc in allModulesSnapshot.documents) {
                        val code = moduleDoc.getString("code") ?: continue
                        val name = moduleDoc.getString("name") ?: ""
                        val enrolledStudents = (moduleDoc.get("enrolledStudents") as? List<*>)
                            ?.mapNotNull { it as? String } ?: emptyList()
                        val lecturerId = moduleDoc.getString("lecturerId")

                        // Filter: non-admin users only see modules they belong to
                        if (!isAdmin) {
                            val userInModule = userId in enrolledStudents || userId == lecturerId
                            if (!userInModule) continue
                        }

                        val chatRoomId = "module_$code"
                        val memberCount = enrolledStudents.size + (if (lecturerId != null) 1 else 0)

                        // Try to get last message from the chat room document
                        var lastMessage: String? = null
                        var lastMessageTime: Date? = null
                        try {
                            val chatRoomDoc = firestore.collection("chat_rooms")
                                .document(chatRoomId)
                                .get()
                                .await()
                            if (chatRoomDoc.exists()) {
                                lastMessage = chatRoomDoc.getString("lastMessage")
                                lastMessageTime = chatRoomDoc.getTimestamp("lastMessageTime")?.toDate()
                            }
                        } catch (e: Exception) {
                            // Ignore — chat room doc may not exist yet
                        }

                        chatRooms.add(
                            ChatRoom(
                                id = chatRoomId,
                                name = "$code $name",
                                description = moduleDoc.getString("description") ?: "",
                                type = ChatRoomType.MODULE,
                                moduleCode = code,
                                memberCount = memberCount,
                                lastMessage = lastMessage,
                                lastMessageTime = lastMessageTime
                            )
                        )
                    }

                    // 2) Non-MODULE chat rooms — from chat_rooms collection (GENERAL, STUDY_GROUP, CLUB)
                    val chatRoomsSnapshot = firestore.collection("chat_rooms").get().await()
                    for (document in chatRoomsSnapshot.documents) {
                        try {
                            val type = ChatRoomType.valueOf(document.getString("type") ?: "GENERAL")
                            if (type == ChatRoomType.MODULE) continue // Skip — already handled above

                            chatRooms.add(
                                ChatRoom(
                                    id = document.id,
                                    name = document.getString("name") ?: "",
                                    description = document.getString("description") ?: "",
                                    type = type,
                                    moduleCode = null,
                                    memberCount = document.getLong("memberCount")?.toInt() ?: 0,
                                    lastMessage = document.getString("lastMessage"),
                                    lastMessageTime = document.getTimestamp("lastMessageTime")?.toDate(),
                                    imageUrl = document.getString("imageUrl") ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            // Skip malformed documents
                        }
                    }

                    _chatRoomsState.value = ChatRoomsState.Success(chatRooms)
                }
            } catch (e: TimeoutCancellationException) {
                _chatRoomsState.value = ChatRoomsState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _chatRoomsState.value = ChatRoomsState.Error(e.message ?: "Failed to fetch chat rooms")
            }
        }
    }

    fun selectChatRoom(chatRoom: ChatRoom) {
        _selectedChatRoom.value = chatRoom
        // Ensure the chat_rooms document exists in Firestore so messages can be stored
        viewModelScope.launch {
            try {
                val docRef = firestore.collection("chat_rooms").document(chatRoom.id)
                val doc = docRef.get().await()
                if (!doc.exists()) {
                    docRef.set(
                        hashMapOf(
                            "name" to chatRoom.name,
                            "description" to chatRoom.description,
                            "type" to chatRoom.type.name,
                            "moduleCode" to chatRoom.moduleCode,
                            "memberCount" to chatRoom.memberCount
                        )
                    ).await()
                }
            } catch (e: Exception) {
                // Ignore — messages will still work if doc creation fails
            }
        }
        fetchMessages(chatRoom.id)
    }

    fun clearSelectedChatRoom() {
        _selectedChatRoom.value = null
        _chatMessagesState.value = ChatMessagesState.Idle
    }

    fun fetchChatRoomMembers(chatRoom: ChatRoom) {
        viewModelScope.launch {
            try {
                _membersState.value = MembersState.Loading

                withTimeout(15000L) {
                    val members = mutableListOf<ChatRoomMember>()

                    if (chatRoom.type == ChatRoomType.MODULE && chatRoom.moduleCode != null) {
                        // For MODULE rooms: get enrolled students + lecturer directly from the module
                        val moduleSnapshot = firestore.collection("modules")
                            .whereEqualTo("code", chatRoom.moduleCode)
                            .get()
                            .await()

                        val moduleDoc = moduleSnapshot.documents.firstOrNull()
                        if (moduleDoc != null) {
                            val enrolledStudents = (moduleDoc.get("enrolledStudents") as? List<*>)
                                ?.mapNotNull { it as? String } ?: emptyList()
                            val lecturerId = moduleDoc.getString("lecturerId")
                            val memberUids = enrolledStudents.toMutableSet()
                            if (lecturerId != null) memberUids.add(lecturerId)

                            for (uid in memberUids) {
                                try {
                                    val userDoc = firestore.collection("users")
                                        .document(uid)
                                        .get()
                                        .await()

                                    if (userDoc.exists()) {
                                        val rolesMap = userDoc.get("roles") as? Map<*, *>
                                        val userIsAdmin = rolesMap?.get("admin") == true
                                        // Hide admin users from non-admin viewers
                                        if (userIsAdmin && !isAdminUser) continue

                                        val role = when {
                                            userIsAdmin -> "Admin"
                                            rolesMap?.get("lecturer") == true -> "Lecturer"
                                            rolesMap?.get("student") == true -> "Student"
                                            else -> "User"
                                        }
                                        members.add(
                                            ChatRoomMember(
                                                uid = uid,
                                                name = userDoc.getString("name") ?: "",
                                                email = userDoc.getString("email") ?: "",
                                                role = role
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Skip this member if fetch fails
                                }
                            }
                        }
                    } else {
                        // For GENERAL, STUDY_GROUP, CLUB rooms: show all non-admin users (admins visible only to admins)
                        val usersSnapshot = firestore.collection("users").get().await()

                        for (doc in usersSnapshot.documents) {
                            val rolesMap = doc.get("roles") as? Map<*, *>
                            val userIsAdmin = rolesMap?.get("admin") == true
                            // Hide admin users from non-admin viewers
                            if (userIsAdmin && !isAdminUser) continue

                            val role = when {
                                userIsAdmin -> "Admin"
                                rolesMap?.get("lecturer") == true -> "Lecturer"
                                rolesMap?.get("student") == true -> "Student"
                                else -> "User"
                            }
                            members.add(
                                ChatRoomMember(
                                    uid = doc.id,
                                    name = doc.getString("name") ?: "",
                                    email = doc.getString("email") ?: "",
                                    role = role
                                )
                            )
                        }
                    }

                    _membersState.value = MembersState.Success(
                        members.sortedWith(compareBy<ChatRoomMember> {
                            when (it.role) {
                                "Lecturer" -> 0
                                "Student" -> 1
                                "Admin" -> 2
                                else -> 3
                            }
                        }.thenBy { it.name.lowercase() })
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _membersState.value = MembersState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _membersState.value = MembersState.Error(e.message ?: "Failed to fetch members")
            }
        }
    }

    fun clearMembersState() {
        _membersState.value = MembersState.Idle
        _availableUsersState.value = AvailableUsersState.Idle
    }

    fun fetchAvailableUsersForModule(moduleCode: String) {
        viewModelScope.launch {
            try {
                _availableUsersState.value = AvailableUsersState.Loading

                withTimeout(15000L) {
                    // Get the module to find current members
                    val moduleSnapshot = firestore.collection("modules")
                        .whereEqualTo("code", moduleCode)
                        .get()
                        .await()

                    val moduleDoc = moduleSnapshot.documents.firstOrNull()
                    val enrolledStudents = if (moduleDoc != null) {
                        (moduleDoc.get("enrolledStudents") as? List<*>)
                            ?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                    } else {
                        emptySet()
                    }
                    val lecturerId = moduleDoc?.getString("lecturerId")

                    // Get all users who are NOT in this module
                    val usersSnapshot = firestore.collection("users").get().await()
                    val available = mutableListOf<ChatRoomMember>()

                    for (doc in usersSnapshot.documents) {
                        val uid = doc.id
                        if (uid in enrolledStudents || uid == lecturerId) continue

                        val rolesMap = doc.get("roles") as? Map<*, *>
                        val userIsAdmin = rolesMap?.get("admin") == true
                        if (userIsAdmin) continue // Don't show admin in the add list

                        val role = when {
                            rolesMap?.get("lecturer") == true -> "Lecturer"
                            rolesMap?.get("student") == true -> "Student"
                            else -> "User"
                        }
                        available.add(
                            ChatRoomMember(
                                uid = uid,
                                name = doc.getString("name") ?: "",
                                email = doc.getString("email") ?: "",
                                role = role
                            )
                        )
                    }

                    _availableUsersState.value = AvailableUsersState.Success(
                        available.sortedBy { it.name.lowercase() }
                    )
                }
            } catch (e: Exception) {
                _availableUsersState.value = AvailableUsersState.Error(e.message ?: "Failed to fetch available users")
            }
        }
    }

    fun addMemberToModule(moduleCode: String, userUid: String) {
        viewModelScope.launch {
            try {
                // Find the module document by code
                val moduleSnapshot = firestore.collection("modules")
                    .whereEqualTo("code", moduleCode)
                    .get()
                    .await()

                val moduleDoc = moduleSnapshot.documents.firstOrNull() ?: return@launch
                val moduleId = moduleDoc.id

                // Add to module's enrolledStudents
                firestore.collection("modules")
                    .document(moduleId)
                    .update("enrolledStudents", FieldValue.arrayUnion(userUid))
                    .await()

                // Also update all schedules for this module
                val schedulesSnapshot = firestore.collection("schedules")
                    .whereEqualTo("moduleId", moduleId)
                    .get()
                    .await()

                for (scheduleDoc in schedulesSnapshot.documents) {
                    firestore.collection("schedules")
                        .document(scheduleDoc.id)
                        .update("enrolledStudents", FieldValue.arrayUnion(userUid))
                        .await()
                }

                // Refresh members and available users
                val currentRoom = _selectedChatRoom.value
                if (currentRoom != null) {
                    fetchChatRoomMembers(currentRoom)
                    fetchAvailableUsersForModule(moduleCode)
                }

                // Refresh chat rooms
                fetchChatRooms(currentUserId, currentUserName, isAdminUser)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun removeMemberFromModule(moduleCode: String, userUid: String) {
        viewModelScope.launch {
            try {
                // Find the module document by code
                val moduleSnapshot = firestore.collection("modules")
                    .whereEqualTo("code", moduleCode)
                    .get()
                    .await()

                val moduleDoc = moduleSnapshot.documents.firstOrNull() ?: return@launch
                val moduleId = moduleDoc.id

                // Remove from module's enrolledStudents
                firestore.collection("modules")
                    .document(moduleId)
                    .update("enrolledStudents", FieldValue.arrayRemove(userUid))
                    .await()

                // Also update all schedules for this module
                val schedulesSnapshot = firestore.collection("schedules")
                    .whereEqualTo("moduleId", moduleId)
                    .get()
                    .await()

                for (scheduleDoc in schedulesSnapshot.documents) {
                    firestore.collection("schedules")
                        .document(scheduleDoc.id)
                        .update("enrolledStudents", FieldValue.arrayRemove(userUid))
                        .await()
                }

                // Refresh members
                val currentRoom = _selectedChatRoom.value
                if (currentRoom != null) {
                    fetchChatRoomMembers(currentRoom)
                }

                // Refresh chat rooms
                fetchChatRooms(currentUserId, currentUserName, isAdminUser)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun fetchMessages(chatRoomId: String) {
        viewModelScope.launch {
            try {
                _chatMessagesState.value = ChatMessagesState.Loading

                withTimeout(15000L) { // 15 second timeout
                    val messagesSnapshot = firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .collection("messages")
                        .orderBy("timestamp", Query.Direction.ASCENDING)
                        .get()
                        .await()

                    val messages = messagesSnapshot.documents.mapNotNull { document ->
                        try {
                            val senderId = document.getString("senderId") ?: ""
                            ChatMessage(
                                id = document.id,
                                chatRoomId = chatRoomId,
                                senderId = senderId,
                                senderName = document.getString("senderName") ?: "",
                                content = document.getString("content") ?: "",
                                timestamp = document.getTimestamp("timestamp")?.toDate() ?: Date(),
                                isCurrentUser = senderId == currentUserId,
                                attachmentUrl = document.getString("attachmentUrl") ?: "",
                                attachmentName = document.getString("attachmentName") ?: "",
                                attachmentType = document.getString("attachmentType") ?: "",
                                isEdited = document.getBoolean("isEdited") ?: false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    _chatMessagesState.value = ChatMessagesState.Success(messages)
                }
            } catch (e: TimeoutCancellationException) {
                _chatMessagesState.value = ChatMessagesState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _chatMessagesState.value = ChatMessagesState.Error(e.message ?: "Failed to fetch messages")
            }
        }
    }

    fun sendMessage(chatRoomId: String, content: String) {
        viewModelScope.launch {
            try {
                _sendMessageState.value = SendMessageState.Loading

                withTimeout(15000L) { // 15 second timeout
                    val messageData = hashMapOf(
                        "senderId" to currentUserId,
                        "senderName" to currentUserName,
                        "content" to content,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .collection("messages")
                        .add(messageData)
                        .await()

                    // Update last message in chat room
                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .update(
                            mapOf(
                                "lastMessage" to content,
                                "lastMessageTime" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    _sendMessageState.value = SendMessageState.Success

                    // Refresh messages
                    fetchMessages(chatRoomId)
                }
            } catch (e: TimeoutCancellationException) {
                _sendMessageState.value = SendMessageState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Failed to send message")
            }
        }
    }

    fun resetSendState() {
        _sendMessageState.value = SendMessageState.Idle
    }

    fun sendMessageWithAttachment(
        context: Context,
        chatRoomId: String,
        content: String,
        fileUri: Uri?,
        fileName: String
    ) {
        viewModelScope.launch {
            try {
                _sendMessageState.value = SendMessageState.Loading

                withTimeout(60000L) { // 60 second timeout for file upload
                    var attachmentUrl = ""
                    var attachmentName = ""
                    var attachmentType = ""

                    // Upload file to Firebase Storage if URI is provided
                    if (fileUri != null) {
                        val storageRef = storage.reference
                        val timestamp = System.currentTimeMillis()
                        attachmentName = fileName.ifEmpty { "attachment_$timestamp" }
                        attachmentType = getFileType(context, fileUri)
                        val fileRef = storageRef.child("chat_attachments/$chatRoomId/${timestamp}_$attachmentName")

                        // Upload file
                        fileRef.putFile(fileUri).await()

                        // Get download URL
                        attachmentUrl = fileRef.downloadUrl.await().toString()
                    }

                    val messageContent = if (content.isNotBlank()) content else if (attachmentName.isNotEmpty()) "📎 $attachmentName" else ""

                    val messageData = hashMapOf(
                        "senderId" to currentUserId,
                        "senderName" to currentUserName,
                        "content" to messageContent,
                        "attachmentUrl" to attachmentUrl,
                        "attachmentName" to attachmentName,
                        "attachmentType" to attachmentType,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .collection("messages")
                        .add(messageData)
                        .await()

                    // Update last message in chat room
                    val lastMessageText = if (content.isNotBlank()) content else "📎 $attachmentName"
                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .update(
                            mapOf(
                                "lastMessage" to lastMessageText,
                                "lastMessageTime" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    _sendMessageState.value = SendMessageState.Success

                    // Refresh messages
                    fetchMessages(chatRoomId)
                }
            } catch (e: TimeoutCancellationException) {
                _sendMessageState.value = SendMessageState.Error("Upload timed out. Please try again with a smaller file.")
            } catch (e: Exception) {
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Failed to send message")
            }
        }
    }

    fun deleteMessage(chatRoomId: String, messageId: String) {
        viewModelScope.launch {
            try {
                // Delete the message
                firestore.collection("chat_rooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .document(messageId)
                    .delete()
                    .await()

                // Get the latest message to update lastMessage in chat room
                val latestMessageSnapshot = firestore.collection("chat_rooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val latestMessage = latestMessageSnapshot.documents.firstOrNull()

                // Update chat room with new lastMessage (or clear it if no messages left)
                if (latestMessage != null) {
                    val lastMessageText = latestMessage.getString("content") ?: ""
                    val lastMessageTime = latestMessage.getTimestamp("timestamp")

                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .update(
                            mapOf(
                                "lastMessage" to lastMessageText,
                                "lastMessageTime" to lastMessageTime
                            )
                        )
                        .await()
                } else {
                    // No messages left, clear both lastMessage and lastMessageTime
                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .update(
                            mapOf(
                                "lastMessage" to com.google.firebase.firestore.FieldValue.delete(),
                                "lastMessageTime" to com.google.firebase.firestore.FieldValue.delete()
                            )
                        )
                        .await()
                }

                // Refresh messages and chat rooms
                fetchMessages(chatRoomId)
                fetchChatRooms(currentUserId, currentUserName)
            } catch (e: Exception) {
                // Handle error silently but still refresh
                fetchMessages(chatRoomId)
                fetchChatRooms(currentUserId, currentUserName)
            }
        }
    }

    fun editMessage(chatRoomId: String, messageId: String, newContent: String) {
        viewModelScope.launch {
            try {
                _sendMessageState.value = SendMessageState.Loading

                // Update both content and isEdited flag
                firestore.collection("chat_rooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .document(messageId)
                    .update(
                        mapOf(
                            "content" to newContent,
                            "isEdited" to true
                        )
                    )
                    .await()

                // Check if this was the last message and update chat room if so
                val latestMessageSnapshot = firestore.collection("chat_rooms")
                    .document(chatRoomId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val latestMessage = latestMessageSnapshot.documents.firstOrNull()
                if (latestMessage?.id == messageId) {
                    firestore.collection("chat_rooms")
                        .document(chatRoomId)
                        .update("lastMessage", newContent)
                        .await()
                }

                _sendMessageState.value = SendMessageState.Success

                // Refresh messages
                fetchMessages(chatRoomId)
            } catch (e: Exception) {
                _sendMessageState.value = SendMessageState.Error(e.message ?: "Failed to edit message")
            }
        }
    }

    private fun getFileType(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == "application/pdf" -> "pdf"
            mimeType?.startsWith("video/") == true -> "video"
            mimeType?.startsWith("audio/") == true -> "audio"
            else -> "file"
        }
    }
}
