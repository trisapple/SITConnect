package com.example.sitconnect2.features.admin.facilitymanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect2.features.facilitybooking.domain.model.BookingStatus
import com.example.sitconnect2.features.facilitybooking.domain.model.Facility
import com.example.sitconnect2.features.facilitybooking.domain.model.FacilityBooking
import com.example.sitconnect2.features.facilitybooking.domain.model.FacilityType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date

data class BookingReport(
    val totalBookings: Int,
    val pendingCount: Int,
    val confirmedCount: Int,
    val cancelledCount: Int,
    val completedCount: Int,
    val bookingsByFacilityType: Map<FacilityType, Int>
)

sealed class FacilityManagementState {
    object Idle : FacilityManagementState()
    object Loading : FacilityManagementState()
    data class Success(
        val facilities: List<Facility> = emptyList(),
        val bookings: List<FacilityBooking> = emptyList(),
        val report: BookingReport? = null,
        val message: String? = null
    ) : FacilityManagementState()
    data class Error(val message: String) : FacilityManagementState()
}

class FacilityManagementViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<FacilityManagementState>(FacilityManagementState.Idle)
    val state: StateFlow<FacilityManagementState> = _state

    fun fetchAllData() {
        viewModelScope.launch {
            try {
                _state.value = FacilityManagementState.Loading

                withTimeout(15000L) {
                    val facilitiesSnapshot = firestore.collection("facilities").get().await()
                    val bookingsSnapshot = firestore.collection("facility_bookings").get().await()

                    val facilities = facilitiesSnapshot.documents.mapNotNull { doc ->
                        try {
                            Facility(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                type = try {
                                    FacilityType.valueOf(doc.getString("type") ?: "DISCUSSION_ROOM")
                                } catch (e: Exception) { FacilityType.DISCUSSION_ROOM },
                                location = doc.getString("location") ?: "",
                                capacity = doc.getLong("capacity")?.toInt() ?: 0,
                                amenities = (doc.get("amenities") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                imageUrl = doc.getString("imageUrl") ?: ""
                            )
                        } catch (e: Exception) { null }
                    }.sortedBy { it.name }

                    val bookings = bookingsSnapshot.documents.mapNotNull { doc ->
                        try {
                            FacilityBooking(
                                id = doc.id,
                                facilityId = doc.getString("facilityId") ?: "",
                                facilityName = doc.getString("facilityName") ?: "",
                                facilityType = try {
                                    FacilityType.valueOf(doc.getString("facilityType") ?: "DISCUSSION_ROOM")
                                } catch (e: Exception) { FacilityType.DISCUSSION_ROOM },
                                userId = doc.getString("userId") ?: "",
                                userName = doc.getString("userName") ?: "",
                                bookingDate = doc.getTimestamp("bookingDate")?.toDate() ?: Date(),
                                timeSlot = doc.getString("timeSlot") ?: "",
                                purpose = doc.getString("purpose") ?: "",
                                status = try {
                                    BookingStatus.valueOf(doc.getString("status") ?: "PENDING")
                                } catch (e: Exception) { BookingStatus.PENDING },
                                createdAt = doc.getTimestamp("createdAt")?.toDate() ?: Date()
                            )
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.createdAt }

                    val report = generateReport(bookings)

                    _state.value = FacilityManagementState.Success(
                        facilities = facilities,
                        bookings = bookings,
                        report = report
                    )
                }
            } catch (e: Exception) {
                _state.value = FacilityManagementState.Error(e.message ?: "Failed to fetch data")
            }
        }
    }

    private fun generateReport(bookings: List<FacilityBooking>): BookingReport {
        return BookingReport(
            totalBookings = bookings.size,
            pendingCount = bookings.count { it.status == BookingStatus.PENDING },
            confirmedCount = bookings.count { it.status == BookingStatus.CONFIRMED },
            cancelledCount = bookings.count { it.status == BookingStatus.CANCELLED },
            completedCount = bookings.count { it.status == BookingStatus.COMPLETED },
            bookingsByFacilityType = bookings.groupBy { it.facilityType }
                .mapValues { it.value.size }
        )
    }

    fun updateBookingStatus(bookingId: String, newStatus: BookingStatus) {
        viewModelScope.launch {
            try {
                _state.value = FacilityManagementState.Loading

                withTimeout(10000L) {
                    firestore.collection("facility_bookings").document(bookingId)
                        .update("status", newStatus.name)
                        .await()

                    fetchAllData()
                }
            } catch (e: Exception) {
                _state.value = FacilityManagementState.Error(e.message ?: "Failed to update booking")
            }
        }
    }

    fun createFacility(
        name: String,
        type: FacilityType,
        location: String,
        capacity: Int,
        amenities: List<String>
    ) {
        viewModelScope.launch {
            try {
                _state.value = FacilityManagementState.Loading

                withTimeout(10000L) {
                    val facilityData = hashMapOf(
                        "name" to name,
                        "type" to type.name,
                        "location" to location,
                        "capacity" to capacity,
                        "amenities" to amenities,
                        "imageUrl" to ""
                    )
                    firestore.collection("facilities").add(facilityData).await()

                    fetchAllData()
                }
            } catch (e: Exception) {
                _state.value = FacilityManagementState.Error(e.message ?: "Failed to create facility")
            }
        }
    }

    fun updateFacility(
        facilityId: String,
        name: String,
        type: FacilityType,
        location: String,
        capacity: Int,
        amenities: List<String>
    ) {
        viewModelScope.launch {
            try {
                _state.value = FacilityManagementState.Loading

                withTimeout(10000L) {
                    val updates = mapOf(
                        "name" to name,
                        "type" to type.name,
                        "location" to location,
                        "capacity" to capacity,
                        "amenities" to amenities
                    )
                    firestore.collection("facilities").document(facilityId).update(updates).await()

                    fetchAllData()
                }
            } catch (e: Exception) {
                _state.value = FacilityManagementState.Error(e.message ?: "Failed to update facility")
            }
        }
    }

    fun deleteFacility(facilityId: String) {
        viewModelScope.launch {
            try {
                _state.value = FacilityManagementState.Loading

                withTimeout(10000L) {
                    // Cancel all pending bookings for this facility
                    val bookingsSnapshot = firestore.collection("facility_bookings")
                        .whereEqualTo("facilityId", facilityId)
                        .whereEqualTo("status", BookingStatus.PENDING.name)
                        .get()
                        .await()

                    bookingsSnapshot.documents.forEach { doc ->
                        firestore.collection("facility_bookings").document(doc.id)
                            .update("status", BookingStatus.CANCELLED.name)
                            .await()
                    }

                    // Delete the facility
                    firestore.collection("facilities").document(facilityId).delete().await()

                    fetchAllData()
                }
            } catch (e: Exception) {
                _state.value = FacilityManagementState.Error(e.message ?: "Failed to delete facility")
            }
        }
    }
}
