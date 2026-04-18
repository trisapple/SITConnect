package com.example.sitconnect2.features.facilitybooking.domain.model

import java.util.Date

enum class FacilityType {
    DISCUSSION_ROOM,
    SPORTS_HALL,
    COMPUTER_LAB,
    STUDY_ROOM,
    MEETING_ROOM,      // For lecturers - Staff Meeting Rooms
    LECTURE_HALL       // For lecturers - Lecture Halls
}

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}

data class Facility(
    val id: String = "",
    val name: String = "",
    val type: FacilityType = FacilityType.DISCUSSION_ROOM,
    val location: String = "",
    val capacity: Int = 0,
    val amenities: List<String> = emptyList(),
    val imageUrl: String = "",
    val availableSlots: List<TimeSlot> = emptyList()
)

data class TimeSlot(
    val id: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val isAvailable: Boolean = true
)

data class FacilityBooking(
    val id: String = "",
    val facilityId: String = "",
    val facilityName: String = "",
    val facilityType: FacilityType = FacilityType.DISCUSSION_ROOM,
    val userId: String = "",
    val userName: String = "",
    val bookingDate: Date = Date(),
    val timeSlot: String = "",
    val purpose: String = "",
    val status: BookingStatus = BookingStatus.PENDING,
    val createdAt: Date = Date()
)

