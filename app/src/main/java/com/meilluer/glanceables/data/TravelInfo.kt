package com.meilluer.glanceables.data

import com.google.gson.annotations.SerializedName

/**
 * Data model representing travel information extracted from a ticket PDF or entered by the user.
 *
 * Variables:
 * - seat_number: Seat or berth number (e.g. "B4-45", "12F")
 * - birthtype: Berth or seat type (e.g. "Lower Berth", "Upper Berth", "Window Seat", "Aisle Seat")
 * - date_of_journey: Travel date (e.g. "25-Oct-2026")
 * - departure_time: Departure time (e.g. "14:30")
 * - arrival_time: Arrival time (e.g. "20:15")
 * - boarding_station: Boarding station name or airport code (e.g. "NDLS", "DEL")
 * - deboarding_station: Deboarding/destination station name or airport code (e.g. "BOM", "MMCT")
 */
data class TravelInfo(
    @SerializedName("seat_number")
    val seatNumber: String = "",

    @SerializedName("birthtype")
    val birthtype: String = "",

    @SerializedName("date_of_journey")
    val dateOfJourney: String = "",

    @SerializedName("departure_time")
    val departureTime: String = "",

    @SerializedName("arrival_time")
    val arrivalTime: String = "",

    @SerializedName("boarding_station")
    val boardingStation: String = "",

    @SerializedName("deboarding_station")
    val deboardingStation: String = ""
) {
    fun isNotEmpty(): Boolean {
        return seatNumber.isNotBlank() ||
                birthtype.isNotBlank() ||
                dateOfJourney.isNotBlank() ||
                departureTime.isNotBlank() ||
                arrivalTime.isNotBlank() ||
                boardingStation.isNotBlank() ||
                deboardingStation.isNotBlank()
    }
}
