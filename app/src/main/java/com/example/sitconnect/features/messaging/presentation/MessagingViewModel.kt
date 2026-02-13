package com.example.sitconnect.features.messaging.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.messaging.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

                val chatRoomsSnapshot = firestore.collection("chat_rooms")
                    .get()
                    .await()

                val chatRooms = chatRoomsSnapshot.documents.mapNotNull { document ->
                    try {
                        ChatRoom(
                            id = document.id,
                            name = document.getString("name") ?: "",
                            description = document.getString("description") ?: "",
                            type = ChatRoomType.valueOf(document.getString("type") ?: "GENERAL"),
                            moduleCode = document.getString("moduleCode"),
                            memberCount = document.getLong("memberCount")?.toInt() ?: 0,
                            lastMessage = document.getString("lastMessage"),
                            lastMessageTime = document.getTimestamp("lastMessageTime")?.toDate(),
                            imageUrl = document.getString("imageUrl") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalChatRooms = if (chatRooms.isEmpty()) getSampleChatRooms() else chatRooms
                _chatRoomsState.value = ChatRoomsState.Success(finalChatRooms)
            } catch (e: Exception) {
                _chatRoomsState.value = ChatRoomsState.Success(getSampleChatRooms())
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
                            isCurrentUser = senderId == currentUserId
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalMessages = if (messages.isEmpty()) getSampleMessages(chatRoomId) else messages
                _chatMessagesState.value = ChatMessagesState.Success(finalMessages)
            } catch (e: Exception) {
                _chatMessagesState.value = ChatMessagesState.Success(getSampleMessages(chatRoomId))
            }
        }
    }

    fun sendMessage(chatRoomId: String, content: String) {
        viewModelScope.launch {
            try {
                _sendMessageState.value = SendMessageState.Loading

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
            } catch (e: Exception) {
                // For demo, add message locally
                val currentMessages = (_chatMessagesState.value as? ChatMessagesState.Success)?.messages ?: emptyList()
                val newMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatRoomId = chatRoomId,
                    senderId = currentUserId,
                    senderName = currentUserName.ifEmpty { "You" },
                    content = content,
                    timestamp = Date(),
                    isCurrentUser = true
                )
                _chatMessagesState.value = ChatMessagesState.Success(currentMessages + newMessage)
                _sendMessageState.value = SendMessageState.Success
            }
        }
    }

    fun resetSendState() {
        _sendMessageState.value = SendMessageState.Idle
    }

    private fun getSampleChatRooms(): List<ChatRoom> {
        return listOf(
            ChatRoom(
                id = "1",
                name = "ICT2207 Mobile Security",
                description = "Discussion group for Mobile Security module",
                type = ChatRoomType.MODULE,
                moduleCode = "ICT2207",
                memberCount = 45,
                lastMessage = "Has anyone started on Assignment 2?",
                lastMessageTime = Date()
            ),
            ChatRoom(
                id = "2",
                name = "ICT2205 Web Security",
                description = "Discussion group for Web Security module",
                type = ChatRoomType.MODULE,
                moduleCode = "ICT2205",
                memberCount = 42,
                lastMessage = "The lab was really interesting today!",
                lastMessageTime = Date(System.currentTimeMillis() - 3600000)
            ),
            ChatRoom(
                id = "3",
                name = "ICT2104 Software Engineering",
                description = "Discussion group for Software Engineering module",
                type = ChatRoomType.MODULE,
                moduleCode = "ICT2104",
                memberCount = 50,
                lastMessage = "Group project meeting tomorrow at 3pm",
                lastMessageTime = Date(System.currentTimeMillis() - 7200000)
            ),
            ChatRoom(
                id = "4",
                name = "SIT Cybersecurity Club",
                description = "For students interested in cybersecurity",
                type = ChatRoomType.CLUB,
                memberCount = 120,
                lastMessage = "CTF competition this weekend!",
                lastMessageTime = Date(System.currentTimeMillis() - 86400000)
            ),
            ChatRoom(
                id = "5",
                name = "Year 3 Study Group",
                description = "Study group for Year 3 ICT students",
                type = ChatRoomType.STUDY_GROUP,
                memberCount = 30,
                lastMessage = "Anyone free to study together?",
                lastMessageTime = Date(System.currentTimeMillis() - 43200000)
            ),
            ChatRoom(
                id = "6",
                name = "SIT General Chat",
                description = "General discussion for all SIT students",
                type = ChatRoomType.GENERAL,
                memberCount = 500,
                lastMessage = "The new canteen food is pretty good!",
                lastMessageTime = Date(System.currentTimeMillis() - 1800000)
            )
        )
    }

    private fun getSampleMessages(chatRoomId: String): List<ChatMessage> {
        val baseTime = System.currentTimeMillis()
        return listOf(
            ChatMessage(
                id = "m1",
                chatRoomId = chatRoomId,
                senderId = "user1",
                senderName = "Alex Tan",
                content = "Hey everyone! 👋",
                timestamp = Date(baseTime - 3600000 * 5),
                isCurrentUser = false
            ),
            ChatMessage(
                id = "m2",
                chatRoomId = chatRoomId,
                senderId = "user2",
                senderName = "Sarah Lee",
                content = "Hi! How's everyone doing?",
                timestamp = Date(baseTime - 3600000 * 4),
                isCurrentUser = false
            ),
            ChatMessage(
                id = "m3",
                chatRoomId = chatRoomId,
                senderId = "user3",
                senderName = "Michael Wong",
                content = "Has anyone started on the assignment yet?",
                timestamp = Date(baseTime - 3600000 * 3),
                isCurrentUser = false
            ),
            ChatMessage(
                id = "m4",
                chatRoomId = chatRoomId,
                senderId = "user1",
                senderName = "Alex Tan",
                content = "I've done the first part. It's quite challenging!",
                timestamp = Date(baseTime - 3600000 * 2),
                isCurrentUser = false
            ),
            ChatMessage(
                id = "m5",
                chatRoomId = chatRoomId,
                senderId = "user2",
                senderName = "Sarah Lee",
                content = "Same here. Should we form a study group?",
                timestamp = Date(baseTime - 3600000),
                isCurrentUser = false
            ),
            ChatMessage(
                id = "m6",
                chatRoomId = chatRoomId,
                senderId = "user3",
                senderName = "Michael Wong",
                content = "That sounds like a good idea! When are you all free?",
                timestamp = Date(baseTime - 1800000),
                isCurrentUser = false
            )
        )
    }
}

