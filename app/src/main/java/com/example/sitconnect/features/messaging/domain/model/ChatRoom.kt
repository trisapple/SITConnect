package com.example.sitconnect.features.messaging.domain.model

import java.util.Date

data class ChatRoom(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: ChatRoomType = ChatRoomType.MODULE,
    val moduleCode: String? = null,
    val memberCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageTime: Date? = null,
    val imageUrl: String = "",
    val participants: List<String> = emptyList() // For DIRECT_MESSAGE: list of 2 user UIDs
)

enum class ChatRoomType {
    MODULE,
    STUDY_GROUP,
    CLUB,
    GENERAL,
    DIRECT_MESSAGE
}

data class ChatMessage(
    val id: String = "",
    val chatRoomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Date = Date(),
    val isCurrentUser: Boolean = false,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val attachmentType: String = "",  // "image", "pdf", "video", "audio", "file"
    val isEdited: Boolean = false
)

data class ChatMember(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "member",
    val joinedAt: Date = Date()
)

