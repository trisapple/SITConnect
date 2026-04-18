package com.example.sitconnect2.features.facilitybooking.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect2.features.facilitybooking.domain.model.*
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

    private val _bookedSlotsForDate = MutableStateFlow<Set<String>>(emptySet())
    val bookedSlotsForDate: StateFlow<Set<String>> = _bookedSlotsForDate

    private var currentUserId: String = ""

    fun setSelectedFacilityType(type: FacilityType?) {
        _selectedFacilityType.value = type
    }

    /** Fetches non-cancelled bookings for [facilityId] on [date] and stores their time slot strings. */
    fun fetchBookedSlotsForDate(facilityId: String, date: Date) {
        viewModelScope.launch {
            try {
                _bookedSlotsForDate.value = emptySet()
                val cal = Calendar.getInstance().apply { time = date }
                val year  = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                val day   = cal.get(Calendar.DAY_OF_MONTH)

                val snapshot = firestore.collection("facility_bookings")
                    .whereEqualTo("facilityId", facilityId)
                    .get().await()

                val booked = snapshot.documents.mapNotNull { doc ->
                    if (doc.getString("status") == BookingStatus.CANCELLED.name) return@mapNotNull null
                    val existing = doc.getTimestamp("bookingDate")?.toDate() ?: return@mapNotNull null
                    val existingCal = Calendar.getInstance().apply { time = existing }
                    val sameDay = existingCal.get(Calendar.YEAR)  == year &&
                                  existingCal.get(Calendar.MONTH) == month &&
                                  existingCal.get(Calendar.DAY_OF_MONTH) == day
                    if (sameDay) doc.getString("timeSlot") else null
                }.toSet()

                _bookedSlotsForDate.value = booked
            } catch (e: Exception) {
                _bookedSlotsForDate.value = emptySet()
            }
        }
    }

    fun clearBookedSlotsForDate() {
        _bookedSlotsForDate.value = emptySet()
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
                    // Check for existing bookings with same facility and time slot
                    // We'll fetch all non-cancelled bookings for this facility and check date match
                    val existingBookingsSnapshot = firestore.collection("facility_bookings")
                        .whereEqualTo("facilityId", facility.id)
                        .whereEqualTo("timeSlot", timeSlot)
                        .get()
                        .await()

                    // Normalize dates for comparison (compare only year, month, day)
                    val calendar = Calendar.getInstance()
                    calendar.time = bookingDate
                    val bookingYear = calendar.get(Calendar.YEAR)
                    val bookingMonth = calendar.get(Calendar.MONTH)
                    val bookingDay = calendar.get(Calendar.DAY_OF_MONTH)

                    // Check if any of the existing bookings conflict
                    val hasConflict = existingBookingsSnapshot.documents.any { doc ->
                        val status = doc.getString("status")
                        if (status == BookingStatus.CANCELLED.name) {
                            return@any false
                        }

                        val existingDate = doc.getTimestamp("bookingDate")?.toDate()
                        if (existingDate != null) {
                            val existingCal = Calendar.getInstance()
                            existingCal.time = existingDate
                            val sameDate = existingCal.get(Calendar.YEAR) == bookingYear &&
                                    existingCal.get(Calendar.MONTH) == bookingMonth &&
                                    existingCal.get(Calendar.DAY_OF_MONTH) == bookingDay
                            sameDate
                        } else {
                            false
                        }
                    }

                    if (hasConflict) {
                        _bookingFormState.value = BookingFormState.Error(
                            "This room is already booked for the selected date and time slot. Please choose a different time or facility."
                        )
                        return@withTimeout
                    }

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

