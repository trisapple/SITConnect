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
    val imageUrl: String = ""
)

enum class ChatRoomType {
    MODULE,
    STUDY_GROUP,
    CLUB,
    GENERAL
}

data class ChatMessage(
    val id: String = "",
    val chatRoomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Date = Date(),
    val isCurrentUser: Boolean = false
)

data class ChatMember(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "member",
    val joinedAt: Date = Date()
)

