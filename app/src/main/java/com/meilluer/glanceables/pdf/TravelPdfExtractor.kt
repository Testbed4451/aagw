package com.meilluer.glanceables.pdf

import android.content.Context
import android.net.Uri
import com.meilluer.glanceables.data.TravelInfo
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.regex.Pattern

/**
 * Extracts travel information (Seat Number, Berth Type, Date of Journey, Departure/Arrival Times,
 * Boarding/Deboarding Stations or Airport Codes) from PDF documents (train tickets, flight boarding passes,
 * bus bookings).
 */
class TravelPdfExtractor(private val context: Context? = null) {

    init {
        context?.let {
            try {
                PDFBoxResourceLoader.init(it)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Reads text from a PDF file Uri.
     */
    fun extractTextFromUri(uri: Uri): String {
        val ctx = context ?: throw IllegalStateException("Context is required to open Uri")
        return ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            try {
                val stripper = PDFTextStripper()
                stripper.getText(document)
            } finally {
                document.close()
            }
        } ?: ""
    }

    /**
     * Parses extracted PDF text to retrieve the travel information.
     */
    fun parse(text: String): TravelInfo {
        if (text.isBlank()) return TravelInfo()

        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")

        val seatNumber = extractSeatNumber(normalized)
        val birthtype = extractBirthtype(normalized, seatNumber)
        val dateOfJourney = extractDateOfJourney(normalized)
        val departureTime = extractDepartureTime(normalized)
        val arrivalTime = extractArrivalTime(normalized)
        val boardingStation = extractBoardingStation(normalized)
        val deboardingStation = extractDeboardingStation(normalized)

        return TravelInfo(
            seatNumber = seatNumber,
            birthtype = birthtype,
            dateOfJourney = dateOfJourney,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            boardingStation = boardingStation,
            deboardingStation = deboardingStation
        )
    }

    // ── 1. Seat Number ────────────────────────────────────────────────────────

    private fun extractSeatNumber(text: String): String {
        // IRCTC pattern: Coach / Berth / Status, e.g. "B4 / 45", "B4-45", "B4, 45", "Coach: B4 Berth: 45"
        val irctcPatterns = listOf(
            // "CNF / B4 / 45" or "CNF/B4/45/LB" or "B4/45/LB"
            Pattern.compile("""(?:CNF|RAC|WL)\s*[/,]\s*([A-Z0-9]+)\s*[/,]\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
            // "Coach\s*[:\s]*([A-Z0-9]+)[,\s]+(?:Berth|Seat)\s*[:\s#]*([0-9]{1,3})"
            Pattern.compile("""Coach\s*[:\s-]*([A-Z0-9]+)[\s,/|]+(?:Berth|Seat)\s*(?:No\.?|Number)?\s*[:\s#-]*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
            // "Berth/Seat\s*[:\s]*([0-9]{1,3})\s*(?:Coach\s*[:\s]*([A-Z0-9]+))"
            Pattern.compile("""(?:Berth|Seat)\s*(?:No\.?|Number)?\s*[:\s#-]*([0-9]{1,3})[\s,/|]+Coach\s*[:\s-]*([A-Z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )

        for (p in irctcPatterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val coach = matcher.group(1)?.trim().orEmpty()
                val seat = matcher.group(2)?.trim().orEmpty()
                return if (coach.isNotEmpty() && seat.isNotEmpty()) "$coach-$seat" else coach.ifEmpty { seat }
            }
        }

        // Direct Seat / Berth Patterns: "Seat No: 24A", "Seat: 12F", "Seat 4B", "Berth No: 45"
        val directPatterns = listOf(
            // Flight seat format e.g. "Seat: 12F" or "Seat No. 23A"
            Pattern.compile("""(?:Seat|Berth)\s*(?:No\.?|Number)?\s*[:\s#-]*([0-9]{1,2}[A-K])\b""", Pattern.CASE_INSENSITIVE),
            // Generic seat number e.g. "Seat No: 45", "Seat : 12"
            Pattern.compile("""(?:Seat|Berth)\s*(?:No\.?|Number)?\s*[:\s#-]*([A-Z0-9-]+)\b""", Pattern.CASE_INSENSITIVE),
            // IRCTC table row: "B4 45 LB" or "S2 34 MB"
            Pattern.compile("""\b([A-Z][0-9]{1,2})\s+([0-9]{1,3})\s+(?:LB|MB|UB|SL|SU|SM|WS|AS)\b""", Pattern.CASE_INSENSITIVE)
        )

        for (p in directPatterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                return if (matcher.groupCount() >= 2) {
                    val g1 = matcher.group(1)?.trim().orEmpty()
                    val g2 = matcher.group(2)?.trim().orEmpty()
                    if (g1.isNotEmpty() && g2.isNotEmpty()) "$g1-$g2" else g1.ifEmpty { g2 }
                } else {
                    matcher.group(1)?.trim().orEmpty()
                }
            }
        }

        return ""
    }

    // ── 2. Berth / Seat Type (birthtype) ──────────────────────────────────────

    private fun extractBirthtype(text: String, seatNumber: String): String {
        // Explicit berth / seat type mentions
        val explicitMap = mapOf(
            "(?i)\\b(?:LB|Lower\\s*Berth|Lower\\s*Birth|Lower)\\b" to "Lower Berth",
            "(?i)\\b(?:UB|Upper\\s*Berth|Upper\\s*Birth|Upper)\\b" to "Upper Berth",
            "(?i)\\b(?:MB|Middle\\s*Berth|Middle\\s*Birth|Middle)\\b" to "Middle Berth",
            "(?i)\\b(?:SL|Side\\s*Lower\\s*Berth|Side\\s*Lower)\\b" to "Side Lower Berth",
            "(?i)\\b(?:SU|Side\\s*Upper\\s*Berth|Side\\s*Upper)\\b" to "Side Upper Berth",
            "(?i)\\b(?:SM|Side\\s*Middle\\s*Berth|Side\\s*Middle)\\b" to "Side Middle Berth",
            "(?i)\\b(?:Window\\s*Seat|Window)\\b" to "Window Seat",
            "(?i)\\b(?:Aisle\\s*Seat|Aisle)\\b" to "Aisle Seat",
            "(?i)\\b(?:Cabin)\\b" to "Cabin",
            "(?i)\\b(?:Coupe)\\b" to "Coupe"
        )

        // Check if there is a "Berth Type" or "Seat Type" key
        val keyPattern = Pattern.compile(
            """(?:Berth\s*(?:Type|Preference)|Birth\s*type|Seat\s*Type|Berth|Birth)\s*[:\s-]*([A-Za-z\s]+)""",
            Pattern.CASE_INSENSITIVE
        )
        val keyMatcher = keyPattern.matcher(text)
        if (keyMatcher.find()) {
            val captured = keyMatcher.group(1)?.trim().orEmpty()
            for ((regex, standardName) in explicitMap) {
                if (Pattern.compile(regex).matcher(captured).find()) {
                    return standardName
                }
            }
        }

        // Look for IRCTC codes in proximity to booking status: e.g. "B4 / 45 / LB"
        val statusPattern = Pattern.compile(
            """(?:CNF|RAC|WL)[/\s,]+[A-Z0-9]+[/\s,]+[0-9]+[/\s,]+(LB|MB|UB|SL|SU|SM|WS|AS)\b""",
            Pattern.CASE_INSENSITIVE
        )
        val statusMatcher = statusPattern.matcher(text)
        if (statusMatcher.find()) {
            val code = statusMatcher.group(1)?.uppercase().orEmpty()
            return when (code) {
                "LB" -> "Lower Berth"
                "MB" -> "Middle Berth"
                "UB" -> "Upper Berth"
                "SL" -> "Side Lower Berth"
                "SU" -> "Side Upper Berth"
                "SM" -> "Side Middle Berth"
                "WS" -> "Window Seat"
                "AS" -> "Aisle Seat"
                else -> code
            }
        }

        // Check if seat number indicates flight window or aisle (e.g. 12A, 14F -> Window, 12C, 12D -> Aisle)
        if (seatNumber.matches(Regex("""^[0-9]{1,2}[A-K]$""", RegexOption.IGNORE_CASE))) {
            val letter = seatNumber.last().uppercaseChar()
            if (letter == 'A' || letter == 'F') {
                return "Window Seat"
            } else if (letter == 'C' || letter == 'D') {
                return "Aisle Seat"
            } else if (letter == 'B' || letter == 'E') {
                return "Middle Seat"
            }
        }

        // Global fallback search for explicit terms
        for ((regex, standardName) in explicitMap) {
            if (Pattern.compile(regex).matcher(text).find()) {
                return standardName
            }
        }

        return ""
    }

    // ── 3. Date of Journey ───────────────────────────────────────────────────

    private fun extractDateOfJourney(text: String): String {
        val dateRegex = """(\d{1,2}[-/.](?:[A-Za-z]{3}|\d{1,2})[-/.]\d{2,4}|\d{4}[-/.](?:[A-Za-z]{3}|\d{1,2})[-/.]\d{1,2}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4})"""

        val patterns = listOf(
            Pattern.compile("""(?:Date\s*of\s*Journey|Journey\s*Date|Travel\s*Date|Boarding\s*Date|Departure\s*Date|Flight\s*Date|Date)\s*[:\s-]*$dateRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(?:DOJ|Travel\s*On|On)\s*[:\s-]*$dateRegex""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim().orEmpty()
            }
        }

        // Search for any date pattern that looks like a date of travel
        val standaloneDate = Pattern.compile("""\b(\d{1,2}[-/](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[-/]\d{2,4})\b""", Pattern.CASE_INSENSITIVE)
        val standaloneMatcher = standaloneDate.matcher(text)
        if (standaloneMatcher.find()) {
            return standaloneMatcher.group(1)?.trim().orEmpty()
        }

        return ""
    }

    // ── 4. Departure Time ────────────────────────────────────────────────────

    private fun extractDepartureTime(text: String): String {
        val timeRegex = """(\d{1,2}[:.]\d{2}(?:\s*(?:AM|PM|hrs|hours))?)"""

        val patterns = listOf(
            Pattern.compile("""(?:Departure\s*Time|Sched(?:uled)?\s*Departure|Departure|Dep\.?\s*Time|Departs?|Dep\.?|STD)\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Boarding\s*Time|Gate\s*Closes)\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim().orEmpty()
            }
        }

        return ""
    }

    // ── 5. Arrival Time ──────────────────────────────────────────────────────

    private fun extractArrivalTime(text: String): String {
        val timeRegex = """(\d{1,2}[:.]\d{2}(?:\s*(?:AM|PM|hrs|hours))?)"""

        val patterns = listOf(
            Pattern.compile("""(?:Arrival\s*Time|Sched(?:uled)?\s*Arrival|Arrival|Arr\.?\s*Time|Arrives?|Arr\.?|STA)\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Destination\s*Time|Drop\s*Time)\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim().orEmpty()
            }
        }

        return ""
    }

    // ── 6. Boarding Station / Airport Code ────────────────────────────────────

    private fun extractBoardingStation(text: String): String {
        val patterns = listOf(
            // "Boarding Station: NDLS / NEW DELHI" or "Boarding At: NDLS"
            Pattern.compile("""(?:Boarding\s*Station|Boarding\s*At|Boarding\s*Point|From\s*Station|Origin\s*Station|Origin)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:To\s*Station|Deboarding|Destination|\n|$))""", Pattern.CASE_INSENSITIVE),
            // "From: DEL" or "From: Indira Gandhi Intl (DEL)"
            Pattern.compile("""(?:From)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:To|Destination|\n|$))""", Pattern.CASE_INSENSITIVE),
            // 3-letter airport code after Origin/From e.g. "DEL -> BOM" or "DEL - BOM"
            Pattern.compile("""\b([A-Z]{3})\s*(?:->|-|to)\s*([A-Z]{3})\b""")
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    return candidate
                }
            }
        }

        return ""
    }

    // ── 7. Deboarding Station / Airport Code ──────────────────────────────────

    private fun extractDeboardingStation(text: String): String {
        val patterns = listOf(
            // "Deboarding Station: MMCT / MUMBAI CENTRAL" or "To Station: MMCT"
            Pattern.compile("""(?:Deboarding\s*Station|Deboarding\s*At|Deboarding\s*Point|To\s*Station|Destination\s*Station|Destination|Dropping\s*Point)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:\n|$|[;,]))""", Pattern.CASE_INSENSITIVE),
            // "To: BOM" or "To: Chhatrapati Shivaji (BOM)"
            Pattern.compile("""(?:To)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:\n|$|[;,]))""", Pattern.CASE_INSENSITIVE),
            // 3-letter airport code after Origin/From e.g. "DEL -> BOM" or "DEL - BOM"
            Pattern.compile("""\b[A-Z]{3}\s*(?:->|-|to)\s*([A-Z]{3})\b""")
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    return candidate
                }
            }
        }

        return ""
    }

    private fun isValidStationOrCode(station: String): Boolean {
        if (station.isBlank()) return false
        val lower = station.lowercase()
        // Ignore keywords
        if (lower.startsWith("date") || lower.startsWith("time") || lower.startsWith("pnr") || lower.startsWith("seat")) {
            return false
        }
        return true
    }
}
