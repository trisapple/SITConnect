package com.example.sitconnect.features.messaging.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.messaging.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
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

    private var currentUserId: String = ""
    private var currentUserName: String = ""

    fun fetchChatRooms(userId: String, userName: String) {
        currentUserId = userId
        currentUserName = userName
        viewModelScope.launch {
            try {
                _chatRoomsState.value = ChatRoomsState.Loading

                withTimeout(15000L) { // 15 second timeout
                    val chatRoomsSnapshot = firestore.collection("chat_rooms")
                        .get()
                        .await()

                    val chatRooms = chatRoomsSnapshot.documents.mapNotNull { document ->
                        try {
                            // Get member count from messages - count unique senders
                            val messagesSnapshot = firestore.collection("chat_rooms")
                                .document(document.id)
                                .collection("messages")
                                .get()
                                .await()

                            val uniqueSenders = messagesSnapshot.documents
                                .mapNotNull { it.getString("senderId") }
                                .toSet()
                                .size

                            // Use at least 1 if there's a stored memberCount, or the unique senders count
                            val storedMemberCount = document.getLong("memberCount")?.toInt() ?: 0
                            val actualMemberCount = maxOf(storedMemberCount, uniqueSenders)

                            ChatRoom(
                                id = document.id,
                                name = document.getString("name") ?: "",
                                description = document.getString("description") ?: "",
                                type = ChatRoomType.valueOf(document.getString("type") ?: "GENERAL"),
                                moduleCode = document.getString("moduleCode"),
                                memberCount = actualMemberCount,
                                lastMessage = document.getString("lastMessage"),
                                lastMessageTime = document.getTimestamp("lastMessageTime")?.toDate(),
                                imageUrl = document.getString("imageUrl") ?: ""
                            )
                        } catch (e: Exception) {
                            null
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
        fetchMessages(chatRoom.id)
    }

    fun clearSelectedChatRoom() {
        _selectedChatRoom.value = null
        _chatMessagesState.value = ChatMessagesState.Idle
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
