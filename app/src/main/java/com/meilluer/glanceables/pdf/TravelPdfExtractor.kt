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
 * Boarding/Deboarding Station Codes, Train Number and Train Name) from PDF documents (train tickets, flight boarding passes,
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
                stripper.sortByPosition = true
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
        val dateOfJourney = extractDateOfJourney(normalized).ifBlank { null }
        val departureTime = extractDepartureTime(normalized).ifBlank { null }
        val arrivalTime = extractArrivalTime(normalized)
        val (boardingStation, deboardingStation) = extractStations(normalized)
        val (trainNumber, trainName) = extractTrainInfo(normalized)

        return TravelInfo(
            seatNumber = seatNumber,
            birthtype = birthtype,
            dateOfJourney = dateOfJourney,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            boardingStation = boardingStation,
            deboardingStation = deboardingStation,
            trainNumber = trainNumber,
            trainName = trainName
        )
    }

    // ── 1. Seat Number ────────────────────────────────────────────────────────

    private fun extractSeatNumber(text: String): String {
        // IRCTC pattern: Coach / Berth / Status, e.g. "CNF/B5/10/MIDDLE", "B4 / 45", "B4-45", "B4, 45"
        val irctcPatterns = listOf(
            // "CNF / B5 / 10" or "CNF/B5/10/MIDDLE" or "CNF/B4/45/LB" or "B4/45/LB"
            Pattern.compile("""(?:CNF|RAC|WL)\s*[/,]\s*([A-Z0-9]+)\s*[/,]\s*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
            // "B5/10/MIDDLE" or "B4/45/LB" without CNF
            Pattern.compile("""\b([A-Z][0-9]{1,2})\s*[/,]\s*([0-9]{1,3})\s*[/,]\s*(?:MIDDLE|LOWER|UPPER|SIDE|LB|MB|UB|SL|SU|SM)\b""", Pattern.CASE_INSENSITIVE),
            // "Coach : B4 Berth: 45"
            Pattern.compile("""Coach\s*[:\s-]*([A-Z0-9]+)[\s,/|]+(?:Berth|Seat)\s*(?:No\.?|Number)?\s*[:\s#-]*([0-9]{1,3})""", Pattern.CASE_INSENSITIVE),
            // "Berth/Seat : 45 Coach: B4"
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

        // Look for IRCTC codes in booking status: e.g. "CNF/B5/10/MIDDLE" or "CNF/B4/45/LB"
        val statusPattern = Pattern.compile(
            """(?:CNF|RAC|WL)[/\s,]+[A-Z0-9]+[/\s,]+[0-9]+[/\s,]+(LB|MB|UB|SL|SU|SM|WS|AS|LOWER|MIDDLE|UPPER|SIDE\s*LOWER|SIDE\s*UPPER|SIDE\s*MIDDLE)\b""",
            Pattern.CASE_INSENSITIVE
        )
        val statusMatcher = statusPattern.matcher(text)
        if (statusMatcher.find()) {
            val code = statusMatcher.group(1)?.uppercase().orEmpty()
            return when (code) {
                "LB", "LOWER" -> "Lower Berth"
                "MB", "MIDDLE" -> "Middle Berth"
                "UB", "UPPER" -> "Upper Berth"
                "SL", "SIDE LOWER" -> "Side Lower Berth"
                "SU", "SIDE UPPER" -> "Side Upper Berth"
                "SM", "SIDE MIDDLE" -> "Side Middle Berth"
                "WS", "WINDOW" -> "Window Seat"
                "AS", "AISLE" -> "Aisle Seat"
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
            // "Start Date* 17-Jun-2024" or "Date of Journey: 25-Oct-2026" or "Departure* 17-Jun-2024"
            Pattern.compile("""(?:Start\s*Date|Departure|Date\s*of\s*Journey|Journey\s*Date|Travel\s*Date|Boarding\s*Date|Departure\s*Date|Flight\s*Date|Date)\*?\s*[:\s-]*$dateRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\b(?:DOJ|Travel\s*On|On)\*?\s*[:\s-]*$dateRegex""", Pattern.CASE_INSENSITIVE)
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
            // "Departure* 17-Jun-2024 07:35" or "Departure* 07:35" or "Scheduled Departure: 16:55"
            Pattern.compile("""(?:Sched(?:uled)?\s*Departure|Departure\s*Time|Departure|Dep\.?\s*Time|Departs?|Dep\.?|STD)\*?\s*(?:\d{1,2}[-/.](?:[A-Za-z]{3}|\d{1,2})[-/.]\d{2,4}\s*)?[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Boarding\s*Time|Gate\s*Closes)\*?\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE)
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
            // "Arrival*18-Jun-2024 04:26" or "Arrival* 18-Jun-2024 04:26" or "Arrival: 04:26"
            Pattern.compile("""(?:Sched(?:uled)?\s*Arrival|Arrival\s*Time|Arrival|Arr\.?\s*Time|Arrives?|Arr\.?|STA)\*?\s*(?:\d{1,2}[-/.](?:[A-Za-z]{3}|\d{1,2})[-/.]\d{2,4}\s*)?[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:Destination\s*Time|Drop\s*Time)\*?\s*[:\s-]*$timeRegex""", Pattern.CASE_INSENSITIVE)
        )

        for (p in patterns) {
            val matcher = p.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim().orEmpty()
            }
        }

        return ""
    }

    // ── 6 & 7. Boarding & Deboarding Station Codes ───────────────────────────

    private fun extractStations(text: String): Pair<String, String> {
        var boardingCode = ""
        var deboardingCode = ""

        // 1. Check for IRCTC Table Layout: "Booked From [Boarding At] To" followed by the stations line
        val irctcHeader = Pattern.compile(
            """Booked\s*From[^\r\n]*?Boarding\s*(?:At)?[^\r\n]*?\bTo\b[^\r\n]*[\r\n]+([^\r\n]+)""",
            Pattern.CASE_INSENSITIVE
        )
        val irctcHeaderMatcher = irctcHeader.matcher(text)
        if (irctcHeaderMatcher.find()) {
            val stationsLine = irctcHeaderMatcher.group(1)?.trim().orEmpty()
            val tokens = parseIrctcStationLine(stationsLine)
            if (tokens.size >= 3) {
                // tokens[0] is Booked From, tokens[1] is Boarding At, tokens[2] is To
                boardingCode = extractStationCode(tokens[1])
                deboardingCode = extractStationCode(tokens[2])
                if (boardingCode.isNotEmpty() && deboardingCode.isNotEmpty()) {
                    return Pair(boardingCode, deboardingCode)
                }
            } else if (tokens.size == 2) {
                boardingCode = extractStationCode(tokens[0])
                deboardingCode = extractStationCode(tokens[1])
                if (boardingCode.isNotEmpty() && deboardingCode.isNotEmpty()) {
                    return Pair(boardingCode, deboardingCode)
                }
            }
        }

        // 2. Direct Boarding search (handling arrows like ->, ➔, colons, newlines):
        // "Boarding At -> DELHI(DLI)" or "Boarding At \n DELHI(DLI)" or "Boarding At: DELHI(DLI)"
        if (boardingCode.isEmpty()) {
            val boardingPattern = Pattern.compile(
                """(?:Boarding\s*(?:At|Station|Point)?|From\s*(?:Station|City)?|Pickup\s*(?:Point|Location)?)\b[^\w\n]*\n*([A-Za-z0-9\s/().-]+?)(?=\n|Departure|Start Date|Dropping|Drop|$)""",
                Pattern.CASE_INSENSITIVE
            )
            val bm = boardingPattern.matcher(text)
            if (bm.find()) {
                val candidate = bm.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    boardingCode = extractStationCode(candidate)
                }
            }
        }

        // Direct Deboarding search:
        // "To \n BEGU SARAI - BGS()" or "To Station: MMCT" or "Dropping Point: ..."
        if (deboardingCode.isEmpty()) {
            val toPattern = Pattern.compile(
                """(?:\bTo\s*Station|\bDeboarding\s*(?:Station|At|Point)|\bDestination\s*(?:Station)?|\bDropping\s*(?:Station|Point|Location)|\bDrop\s*Point|\bTo)\b[^\w\n]*\n*([A-Za-z0-9\s/().-]+?)(?=\n|Arrival|Class|Distance|Seat|$)""",
                Pattern.CASE_INSENSITIVE
            )
            val tm = toPattern.matcher(text)
            if (tm.find()) {
                val candidate = tm.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    deboardingCode = extractStationCode(candidate)
                }
            }
        }

        // 3. Airport route codes e.g. "DEL -> BOM" or "DEL - BOM" or "DEL to BOM"
        if (boardingCode.isEmpty() || deboardingCode.isEmpty()) {
            val airportRoutePattern = Pattern.compile("""\b([A-Z]{3})\s*(?:->|-|to)\s*([A-Z]{3})\b""")
            val am = airportRoutePattern.matcher(text)
            if (am.find()) {
                if (boardingCode.isEmpty()) boardingCode = am.group(1)?.trim().orEmpty()
                if (deboardingCode.isEmpty()) deboardingCode = am.group(2)?.trim().orEmpty()
            }
        }

        // 4. Fallback search for Station Codes in IRCTC format across lines following "Boarding"
        if (boardingCode.isEmpty()) {
            val irctcBoardingCodePattern = Pattern.compile(
                """(?:Boarding[^\n]*)\n+.*?([A-Za-z\s]+)\(([A-Z0-9]{2,5})\)""",
                Pattern.CASE_INSENSITIVE
            )
            val ibm = irctcBoardingCodePattern.matcher(text)
            if (ibm.find()) {
                boardingCode = ibm.group(2)?.trim().orEmpty()
            }
        }

        // 5. Fallback: Standard "From: DEL" / "To: BOM"
        if (boardingCode.isEmpty()) {
            val fromPattern = Pattern.compile("""(?:From|Boarding\s*Point|Pickup\s*Point)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:To|Dropping|Destination|\n|$))""", Pattern.CASE_INSENSITIVE)
            val fm = fromPattern.matcher(text)
            if (fm.find()) {
                val candidate = fm.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    boardingCode = extractStationCode(candidate)
                }
            }
        }

        if (deboardingCode.isEmpty()) {
            val toSimplePattern = Pattern.compile("""(?:To|Dropping\s*Point|Drop\s*Point)\s*[:\s-]*([A-Za-z0-9\s()/-]{2,40}?)(?=(?:\n|$|[;,]))""", Pattern.CASE_INSENSITIVE)
            val tsm = toSimplePattern.matcher(text)
            if (tsm.find()) {
                val candidate = tsm.group(1)?.trim().orEmpty()
                if (isValidStationOrCode(candidate)) {
                    deboardingCode = extractStationCode(candidate)
                }
            }
        }

        return Pair(boardingCode, deboardingCode)
    }

    private fun parseIrctcStationLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        // Each station in IRCTC ends with parentheses like (NEW DELHI), (DLI), or ()
        val p = Pattern.compile("""[A-Za-z0-9\s/.-]+?\([A-Za-z0-9\s/.-]*\)""")
        val m = p.matcher(line)
        while (m.find()) {
            val token = m.group()?.trim().orEmpty()
            if (token.isNotEmpty()) {
                tokens.add(token)
            }
        }
        return tokens
    }

    /**
     * Extracts ONLY the station code or airport code (e.g. "DLI", "BGS", "NDLS", "DEL", "BOM").
     */
    private fun extractStationCode(raw: String): String {
        val trimmed = raw.trim().trim('-', ':', '>', '*', '➔')

        // 1. Pattern: "BEGU SARAI - BGS()" or "- BGS()" or "- DLI(NEW DELHI)"
        val dashCodeParenPattern = Pattern.compile("""-\s*([A-Z0-9]{2,5})\s*\(""")
        val dm = dashCodeParenPattern.matcher(trimmed)
        if (dm.find()) {
            return dm.group(1)?.trim().orEmpty()
        }

        // 2. Pattern: "DELHI(DLI)" or "NEW DELHI (NDLS)" -> extracts "DLI" or "NDLS"
        val parenCodePattern = Pattern.compile("""\(([A-Z0-9]{2,5})\)""")
        val pm = parenCodePattern.matcher(trimmed)
        if (pm.find()) {
            return pm.group(1)?.trim().orEmpty()
        }

        // 3. Pattern: "NDLS / NEW DELHI" or "NEW DELHI / NDLS"
        val slashCodePattern = Pattern.compile("""\b([A-Z0-9]{2,5})\s*/|/\s*([A-Z0-9]{2,5})\b""")
        val sm = slashCodePattern.matcher(trimmed)
        if (sm.find()) {
            val c1 = sm.group(1)?.trim().orEmpty()
            val c2 = sm.group(2)?.trim().orEmpty()
            return c1.ifEmpty { c2 }
        }

        // 4. Pattern: "NAME - CODE"
        val dashCodePattern = Pattern.compile("""-\s*([A-Z0-9]{2,5})\b""")
        val dcm = dashCodePattern.matcher(trimmed)
        if (dcm.find()) {
            return dcm.group(1)?.trim().orEmpty()
        }

        // 5. If string is already 2 to 5 letters (e.g. "DLI", "BGS", "DEL", "BOM")
        val directCodePattern = Pattern.compile("""^[A-Z0-9]{2,5}$""")
        if (directCodePattern.matcher(trimmed).matches()) {
            return trimmed
        }

        // 6. First uppercase word of 2 to 5 letters if uppercase code exists
        val wordCodePattern = Pattern.compile("""\b([A-Z]{3,4})\b""")
        val wm = wordCodePattern.matcher(trimmed)
        if (wm.find()) {
            val word = wm.group(1)?.trim().orEmpty()
            if (word != "EXP" && word != "NEW" && word != "THE" && word != "FOR") {
                return word
            }
        }

        // Fallback: remove empty parentheses and return clean text
        return trimmed.replace(Regex("""\(\s*\)"""), "").trim()
    }

    // ── 8 & 9. Train Number & Train Name ─────────────────────────────────────

    private fun extractTrainInfo(text: String): Pair<String, String> {
        var number = ""
        var name = ""

        // 1. "Train No./Name: 15484 / MAHANANDA EXP" or "Train No. & Name: 12952 / NEW DELHI RAJDHANI"
        val labeled = Pattern.compile(
            """(?:Train\s*(?:No\.?|Number)?\s*(?:[/&]\s*Name)?)\s*[:\s-]*([0-9]{4,5})\s*[/,-]\s*([A-Za-z0-9\s.-]+?)(?=\n|Class|Distance|Quota|Booking|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val lm = labeled.matcher(text)
        if (lm.find()) {
            number = lm.group(1)?.trim().orEmpty()
            name = lm.group(2)?.trim().orEmpty()
            return Pair(number, name)
        }

        // 2. Vertical layout:
        // Train No./Name
        // 15484 / MAHANANDA EXP
        val vertical = Pattern.compile(
            """Train\s*(?:No\.?|Number)?\s*(?:[/&]\s*Name)?[^\w\n]*\n+\s*([0-9]{4,5})\s*[/,-]\s*([A-Za-z0-9\s.-]+?)(?=\n|Class|Distance|Quota|Booking|$)""",
            Pattern.CASE_INSENSITIVE
        )
        val vm = vertical.matcher(text)
        if (vm.find()) {
            number = vm.group(1)?.trim().orEmpty()
            name = vm.group(2)?.trim().orEmpty()
            return Pair(number, name)
        }

        // 3. Horizontal table row after 10-digit PNR: "2116530134 15484 / MAHANANDA EXP THIRD AC"
        val tableRow = Pattern.compile(
            """\b[0-9]{10}\s+([0-9]{4,5})\s*[/,-]\s*([A-Za-z0-9\s.-]+?)\s+(?:FIRST|SECOND|THIRD|SL|1A|2A|3A|CC|EC|2S|CHAIR|EXECUTIVE)\b""",
            Pattern.CASE_INSENSITIVE
        )
        val tm = tableRow.matcher(text)
        if (tm.find()) {
            number = tm.group(1)?.trim().orEmpty()
            name = tm.group(2)?.trim().orEmpty()
            return Pair(number, name)
        }

        // 4. Standalone pattern: "15484 / MAHANANDA EXP"
        val standalone = Pattern.compile(
            """\b([0-9]{5})\s*[/,-]\s*([A-Za-z\s]{3,30}?(?:EXP(?:RESS)?|SF|SUPERFAST|SPL|SPECIAL|PASSENGER|MAIL|RAJDHANI|SHATABDI|VANDE\s*BHARAT|DURONTO|GARIB\s*RATH))\b""",
            Pattern.CASE_INSENSITIVE
        )
        val sm = standalone.matcher(text)
        if (sm.find()) {
            number = sm.group(1)?.trim().orEmpty()
            name = sm.group(2)?.trim().orEmpty()
            return Pair(number, name)
        }

        // 5. Separate Train No: "Train No: 15484"
        val trainNoOnly = Pattern.compile("""(?:Train\s*(?:No\.?|Number))\s*[:\s-]*([0-9]{4,5})\b""", Pattern.CASE_INSENSITIVE)
        val tnm = trainNoOnly.matcher(text)
        if (tnm.find()) {
            number = tnm.group(1)?.trim().orEmpty()
        }

        val trainNameOnly = Pattern.compile("""(?:Train\s*Name)\s*[:\s-]*([A-Za-z0-9\s.-]+?)(?=\n|Class|Distance|Quota|Booking|$)""", Pattern.CASE_INSENSITIVE)
        val tnam = trainNameOnly.matcher(text)
        if (tnam.find()) {
            name = tnam.group(1)?.trim().orEmpty()
        }

        // 6. Flight: "Flight: 6E-204"
        if (number.isEmpty()) {
            val flight = Pattern.compile(
                """(?:Flight\s*(?:No\.?)?|Flight)\s*[:\s-]*([A-Z0-9]{2,3}[- ]?[0-9]{3,4})\b""",
                Pattern.CASE_INSENSITIVE
            )
            val fm = flight.matcher(text)
            if (fm.find()) {
                number = fm.group(1)?.trim().orEmpty()
            }
        }

        return Pair(number, name)
    }

    private fun isValidStationOrCode(station: String): Boolean {
        if (station.isBlank()) return false
        val lower = station.lowercase().trim()
        if (lower.startsWith("date") || lower.startsWith("time") || lower.startsWith("pnr") ||
            lower.startsWith("seat") || lower.startsWith("class") || lower.startsWith("distance") ||
            lower.startsWith("start date") || lower.startsWith("departure") || lower.startsWith("arrival")
        ) {
            return false
        }
        return true
    }
}
