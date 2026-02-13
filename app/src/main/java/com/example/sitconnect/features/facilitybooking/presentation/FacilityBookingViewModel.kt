package com.example.sitconnect.features.facilitybooking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.facilitybooking.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

sealed class FacilityState {
    object Idle : FacilityState()
    object Loading : FacilityState()
    data class Success(
        val facilities: List<Facility>,
        val myBookings: List<FacilityBooking>
    ) : FacilityState()
    data class Error(val message: String) : FacilityState()
}

sealed class BookingFormState {
    object Idle : BookingFormState()
    object Loading : BookingFormState()
    object Success : BookingFormState()
    data class Error(val message: String) : BookingFormState()
}

class FacilityBookingViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _facilityState = MutableStateFlow<FacilityState>(FacilityState.Idle)
    val facilityState: StateFlow<FacilityState> = _facilityState

    private val _bookingFormState = MutableStateFlow<BookingFormState>(BookingFormState.Idle)
    val bookingFormState: StateFlow<BookingFormState> = _bookingFormState

    private val _selectedFacilityType = MutableStateFlow<FacilityType?>(null)
    val selectedFacilityType: StateFlow<FacilityType?> = _selectedFacilityType

    private var currentUserId: String = ""

    fun setSelectedFacilityType(type: FacilityType?) {
        _selectedFacilityType.value = type
    }

    fun fetchFacilities(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            try {
                _facilityState.value = FacilityState.Loading

                // Fetch facilities
                val facilitiesSnapshot = firestore.collection("facilities")
                    .get()
                    .await()

                val facilities = facilitiesSnapshot.documents.mapNotNull { document ->
                    try {
                        val slots = (document.get("availableSlots") as? List<*>)?.mapNotNull { item ->
                            val map = item as? Map<*, *>
                            if (map != null) {
                                TimeSlot(
                                    id = map["id"] as? String ?: "",
                                    startTime = map["startTime"] as? String ?: "",
                                    endTime = map["endTime"] as? String ?: "",
                                    isAvailable = map["isAvailable"] as? Boolean ?: true
                                )
                            } else null
                        } ?: emptyList()

                        Facility(
                            id = document.id,
                            name = document.getString("name") ?: "",
                            type = FacilityType.valueOf(document.getString("type") ?: "DISCUSSION_ROOM"),
                            location = document.getString("location") ?: "",
                            capacity = document.getLong("capacity")?.toInt() ?: 0,
                            amenities = (document.get("amenities") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            imageUrl = document.getString("imageUrl") ?: "",
                            availableSlots = slots
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Fetch user's bookings
                val bookingsSnapshot = firestore.collection("facility_bookings")
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val bookings = bookingsSnapshot.documents.mapNotNull { document ->
                    try {
                        FacilityBooking(
                            id = document.id,
                            facilityId = document.getString("facilityId") ?: "",
                            facilityName = document.getString("facilityName") ?: "",
                            facilityType = FacilityType.valueOf(document.getString("facilityType") ?: "DISCUSSION_ROOM"),
                            userId = document.getString("userId") ?: "",
                            userName = document.getString("userName") ?: "",
                            bookingDate = document.getTimestamp("bookingDate")?.toDate() ?: Date(),
                            timeSlot = document.getString("timeSlot") ?: "",
                            purpose = document.getString("purpose") ?: "",
                            status = BookingStatus.valueOf(document.getString("status") ?: "PENDING"),
                            createdAt = document.getTimestamp("createdAt")?.toDate() ?: Date()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalFacilities = if (facilities.isEmpty()) getSampleFacilities() else facilities

                _facilityState.value = FacilityState.Success(
                    facilities = finalFacilities,
                    myBookings = bookings
                )
            } catch (e: Exception) {
                _facilityState.value = FacilityState.Success(
                    facilities = getSampleFacilities(),
                    myBookings = emptyList()
                )
            }
        }
    }

    fun bookFacility(
        facility: Facility,
        userName: String,
        bookingDate: Date,
        timeSlot: String,
        purpose: String
    ) {
        viewModelScope.launch {
            try {
                _bookingFormState.value = BookingFormState.Loading

                val bookingData = hashMapOf(
                    "facilityId" to facility.id,
                    "facilityName" to facility.name,
                    "facilityType" to facility.type.name,
                    "userId" to currentUserId,
                    "userName" to userName,
                    "bookingDate" to com.google.firebase.Timestamp(bookingDate),
                    "timeSlot" to timeSlot,
                    "purpose" to purpose,
                    "status" to BookingStatus.CONFIRMED.name,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                firestore.collection("facility_bookings")
                    .add(bookingData)
                    .await()

                _bookingFormState.value = BookingFormState.Success

                // Refresh facilities
                fetchFacilities(currentUserId)
            } catch (e: Exception) {
                _bookingFormState.value = BookingFormState.Error(e.message ?: "Failed to book facility")
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("facility_bookings")
                    .document(bookingId)
                    .update("status", BookingStatus.CANCELLED.name)
                    .await()

                fetchFacilities(currentUserId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun resetBookingFormState() {
        _bookingFormState.value = BookingFormState.Idle
    }

    private fun getSampleFacilities(): List<Facility> {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Generate time slots - slots in the past are unavailable
        val defaultSlots = (9..17).map { hour ->
            TimeSlot(
                id = hour.toString(),
                startTime = String.format("%02d:00", hour),
                endTime = String.format("%02d:00", hour + 1),
                isAvailable = hour >= currentHour // Only future slots are available
            )
        }

        // Some facilities have random unavailable slots for realism
        val alternateSlots = defaultSlots.mapIndexed { index, slot ->
            if (slot.isAvailable && index % 3 == 0) {
                slot.copy(isAvailable = false) // Some slots are booked
            } else {
                slot
            }
        }

        return listOf(
            Facility(
                id = "dr1",
                name = "Discussion Room 1A",
                type = FacilityType.DISCUSSION_ROOM,
                location = "SIT@NYP Level 1",
                capacity = 8,
                amenities = listOf("Whiteboard", "TV Screen", "HDMI Cable", "Aircon"),
                availableSlots = defaultSlots
            ),
            Facility(
                id = "dr2",
                name = "Discussion Room 2B",
                type = FacilityType.DISCUSSION_ROOM,
                location = "SIT@NYP Level 2",
                capacity = 6,
                amenities = listOf("Whiteboard", "Projector", "Aircon"),
                availableSlots = alternateSlots
            ),
            Facility(
                id = "dr3",
                name = "Discussion Room 3C",
                type = FacilityType.DISCUSSION_ROOM,
                location = "SIT@Dover Level 3",
                capacity = 10,
                amenities = listOf("Whiteboard", "TV Screen", "Video Conferencing", "Aircon"),
                availableSlots = defaultSlots
            ),
            Facility(
                id = "sh1",
                name = "Badminton Court 1",
                type = FacilityType.SPORTS_HALL,
                location = "SIT Sports Complex",
                capacity = 4,
                amenities = listOf("Court", "Net", "Shuttlecocks Available"),
                availableSlots = defaultSlots.filter { it.startTime.substringBefore(":").toInt() in 9..15 }
            ),
            Facility(
                id = "sh2",
                name = "Basketball Court",
                type = FacilityType.SPORTS_HALL,
                location = "SIT Sports Complex",
                capacity = 10,
                amenities = listOf("Full Court", "Basketballs Available"),
                availableSlots = alternateSlots.filter { it.startTime.substringBefore(":").toInt() in 9..15 }
            ),
            Facility(
                id = "sh3",
                name = "Table Tennis Room",
                type = FacilityType.SPORTS_HALL,
                location = "SIT Sports Complex",
                capacity = 4,
                amenities = listOf("2 Tables", "Paddles & Balls Available"),
                availableSlots = defaultSlots
            ),
            Facility(
                id = "sr1",
                name = "Quiet Study Room A",
                type = FacilityType.STUDY_ROOM,
                location = "SIT Library Level 2",
                capacity = 1,
                amenities = listOf("Desk", "Power Outlet", "Lamp"),
                availableSlots = defaultSlots
            ),
            Facility(
                id = "cl1",
                name = "Computer Lab 4A",
                type = FacilityType.COMPUTER_LAB,
                location = "SIT@NYP Level 4",
                capacity = 30,
                amenities = listOf("Windows PCs", "Projector", "Printer Access"),
                availableSlots = defaultSlots.take(4)
            )
        )
    }
}

