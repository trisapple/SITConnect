package com.example.sitconnect.features.facilitybooking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.facilitybooking.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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

                withTimeout(15000L) { // 15 second timeout
                    // Fetch facilities from Firestore (seeded via Admin panel)
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
                    }.sortedByDescending { it.createdAt }

                    _facilityState.value = FacilityState.Success(
                        facilities = facilities,
                        myBookings = bookings
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _facilityState.value = FacilityState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _facilityState.value = FacilityState.Error(e.message ?: "Failed to fetch facilities")
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

                withTimeout(15000L) { // 15 second timeout
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
                }
            } catch (e: TimeoutCancellationException) {
                _bookingFormState.value = BookingFormState.Error("Request timed out. Please check your internet connection.")
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
}

