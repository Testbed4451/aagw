package com.meilluer.glanceables.data

import com.google.gson.annotations.SerializedName

/**
 * Data model representing travel information extracted from a ticket PDF or entered by the user.
 *
 * Variables:
 * - seat_number: Seat or berth number (e.g. "B5-10", "12F")
 * - birthtype: Berth or seat type (e.g. "Lower Berth", "Middle Berth", "Window Seat", "Aisle Seat")
 * - date_of_journey: Travel date (e.g. "17-Jun-2024")
 * - departure_time: Departure time (e.g. "07:35")
 * - arrival_time: Arrival time (e.g. "04:26")
 * - boarding_station: Boarding station code or airport code (e.g. "DLI", "DEL")
 * - deboarding_station: Deboarding station code or airport code (e.g. "BGS", "BOM")
 * - train_number: Train number, flight number, or vehicle number (e.g. "15484", "6E-204")
 * - train_name: Train name, flight carrier, or service name (e.g. "MAHANANDA EXP", "IndiGo")
 */
data class TravelInfo(
    @SerializedName("seat_number")
    val seatNumber: String = "",

    @SerializedName("birthtype")
    val birthtype: String = "",

    @SerializedName("date_of_journey")
    val dateOfJourney: String? = null,

    @SerializedName("departure_time")
    val departureTime: String? = null,

    @SerializedName("arrival_time")
    val arrivalTime: String = "",

    @SerializedName("boarding_station")
    val boardingStation: String = "",

    @SerializedName("deboarding_station")
    val deboardingStation: String = "",

    @SerializedName("train_number")
    val trainNumber: String = "",

    @SerializedName("train_name")
    val trainName: String = ""
) {
    fun isNotEmpty(): Boolean {
        return seatNumber.isNotBlank() ||
                birthtype.isNotBlank() ||
                !dateOfJourney.isNullOrBlank() ||
                !departureTime.isNullOrBlank() ||
                arrivalTime.isNotBlank() ||
                boardingStation.isNotBlank() ||
                deboardingStation.isNotBlank() ||
                trainNumber.isNotBlank() ||
                trainName.isNotBlank()
    }
}
