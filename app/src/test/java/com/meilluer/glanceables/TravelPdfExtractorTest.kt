package com.meilluer.glanceables

import com.meilluer.glanceables.pdf.TravelPdfExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelPdfExtractorTest {

    private val extractor = TravelPdfExtractor(null)

    @Test
    fun testIrctcTrainTicketExtraction() {
        val irctcText = """
            ELECTRONIC RESERVATION SLIP (ERS)
            PNR: 2456789123 Train No. & Name: 12952 / NEW DELHI RAJDHANI
            Date of Journey: 25-Oct-2026
            Scheduled Departure: 16:55
            Scheduled Arrival: 08:35
            Boarding Station: NDLS / NEW DELHI
            To Station: MMCT / MUMBAI CENTRAL
            Passenger Details:
            1. John Doe   Male   32   CNF / B4 / 45 / LB (Lower Berth)
            Berth Type: Lower Berth
        """.trimIndent()

        val info = extractor.parse(irctcText)

        assertEquals("B4-45", info.seatNumber)
        assertEquals("Lower Berth", info.birthtype)
        assertEquals("25-Oct-2026", info.dateOfJourney)
        assertEquals("16:55", info.departureTime)
        assertEquals("08:35", info.arrivalTime)
        assertEquals("NDLS / NEW DELHI", info.boardingStation)
        assertEquals("MMCT / MUMBAI CENTRAL", info.deboardingStation)
        assertTrue(info.isNotEmpty())
    }

    @Test
    fun testFlightBoardingPassExtraction() {
        val flightText = """
            BOARDING PASS - INDIGO 6E-204
            Passenger: SMITH/ALICE
            Flight Date: 15-Nov-2026
            Seat: 12F
            Seat Type: Window Seat
            Boarding Time: 09:15 AM
            Departure Time: 10:00 AM
            Arrival Time: 12:15 PM
            From: DEL
            To: BOM
            Gate: 4B
        """.trimIndent()

        val info = extractor.parse(flightText)

        assertEquals("12F", info.seatNumber)
        assertEquals("Window Seat", info.birthtype)
        assertEquals("15-Nov-2026", info.dateOfJourney)
        assertEquals("10:00 AM", info.departureTime)
        assertEquals("12:15 PM", info.arrivalTime)
        assertEquals("DEL", info.boardingStation)
        assertEquals("BOM", info.deboardingStation)
    }

    @Test
    fun testFlightInferredSeatType() {
        val flightText = """
            Airline: Air India AI-101
            Travel Date: 2026-12-05
            Seat: 24A
            STD: 14:30
            STA: 18:45
            From: JFK
            To: LHR
        """.trimIndent()

        val info = extractor.parse(flightText)

        assertEquals("24A", info.seatNumber)
        assertEquals("Window Seat", info.birthtype) // 'A' maps to Window Seat
        assertEquals("2026-12-05", info.dateOfJourney)
        assertEquals("14:30", info.departureTime)
        assertEquals("18:45", info.arrivalTime)
        assertEquals("JFK", info.boardingStation)
        assertEquals("LHR", info.deboardingStation)
    }

    @Test
    fun testBusTicketExtraction() {
        val busText = """
            Booking Confirmation
            Operator: InterCity SmartBus
            Date of Journey: 18/11/2026
            Departure Time: 21:30
            Arrival Time: 06:00
            Boarding Point: Anand Vihar ISBT
            Dropping Point: Kashmiri Gate Metro
            Seat No: 14
            Berth Type: Upper Berth
        """.trimIndent()

        val info = extractor.parse(busText)

        assertEquals("14", info.seatNumber)
        assertEquals("Upper Berth", info.birthtype)
        assertEquals("18/11/2026", info.dateOfJourney)
        assertEquals("21:30", info.departureTime)
        assertEquals("06:00", info.arrivalTime)
        assertEquals("Anand Vihar ISBT", info.boardingStation)
        assertEquals("Kashmiri Gate Metro", info.deboardingStation)
    }

    @Test
    fun testUserSampleIrctcTicketHorizontalLayout() {
        // Horizontal text layout from PDFTextStripper
        val userTicketText = """
            Electronic Reservation Slip (ERS)-Normal User
            Booked From Boarding At To
            DELHI - DLI(NEW DELHI) DELHI(DLI) BEGU SARAI - BGS()
            Start Date* 17-Jun-2024 Departure* 17-Jun-2024 07:35 Arrival*18-Jun-2024 04:26
            PNR Train No./Name Class
            2116530134 15484 / MAHANANDA EXP THIRD AC (3A)
            Quota Distance Booking Date
            PREMIUM TATKAL (PTQ) 1118 KM 16-Jun-2024 10:02:11 HRS
            Passenger Details
            # Name Age Gender Booking Status Current Status
            1. RAGHVENDRA KUMAR 24 Male CNF/B5/10/MIDDLE CNF/B5/10/MIDDLE
        """.trimIndent()

        val info = extractor.parse(userTicketText)

        assertEquals("B5-10", info.seatNumber)
        assertEquals("Middle Berth", info.birthtype)
        assertEquals("17-Jun-2024", info.dateOfJourney)
        assertEquals("07:35", info.departureTime)
        assertEquals("04:26", info.arrivalTime)
        assertEquals("DELHI (DLI)", info.boardingStation)
        assertEquals("BEGU SARAI (BGS)", info.deboardingStation)
    }

    @Test
    fun testUserSampleIrctcTicketVerticalLayout() {
        // Vertical text layout
        val userTicketText = """
            Electronic Reservation Slip (ERS)-Normal User
            Booked From
            DELHI - DLI(NEW DELHI)
            Start Date* 17-Jun-2024
            Boarding At ->
            DELHI(DLI)
            Departure* 17-Jun-2024 07:35
            To
            BEGU SARAI - BGS()
            Arrival*18-Jun-2024 04:26
            PNR
            2116530134
            Train No./Name
            15484 / MAHANANDA EXP
            Class
            THIRD AC (3A)
            Passenger Details
            # Name Age Gender Booking Status Current Status
            1. RAGHVENDRA KUMAR 24 Male CNF/B5/10/MIDDLE CNF/B5/10/MIDDLE
        """.trimIndent()

        val info = extractor.parse(userTicketText)

        assertEquals("B5-10", info.seatNumber)
        assertEquals("Middle Berth", info.birthtype)
        assertEquals("17-Jun-2024", info.dateOfJourney)
        assertEquals("07:35", info.departureTime)
        assertEquals("04:26", info.arrivalTime)
        assertEquals("DELHI (DLI)", info.boardingStation)
        assertEquals("BEGU SARAI (BGS)", info.deboardingStation)
    }
}
