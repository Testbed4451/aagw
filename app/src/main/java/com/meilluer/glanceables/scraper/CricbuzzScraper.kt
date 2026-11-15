package com.meilluer.glanceables.scraper

import com.meilluer.glanceables.data.CricketMatch
import com.meilluer.glanceables.data.FollowedCricketTeam
import com.meilluer.glanceables.debug.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Web scraper for Cricbuzz live cricket scores, current run rate (CRR),
 * and required run rate (RRR).
 */
class CricbuzzScraper {

    companion object {
        private const val TAG = "CricbuzzScraper"
        private const val BASE_URL = "https://www.cricbuzz.com"
        private const val LIVE_SCORES_URL = "https://www.cricbuzz.com/cricket-match/live-scores"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches live matches and filters for the user's followed teams.
     * Enriches active live matches with CRR and RRR.
     */
    suspend fun getMatchesForFollowedTeams(
        followedTeams: List<FollowedCricketTeam>
    ): List<CricketMatch> = withContext(Dispatchers.IO) {
        if (followedTeams.isEmpty()) return@withContext emptyList()

        AppLogger.i(TAG, "Fetching live matches for ${followedTeams.size} followed cricket teams")
        val allMatches = fetchAllMatches()

        val matching = allMatches.filter { match ->
            isMatchForFollowedTeams(match, followedTeams)
        }

        AppLogger.i(TAG, "Found ${matching.size} matches matching followed teams")

        // Enrich live matches with CRR & RRR from their detail pages
        matching.map { match ->
            if (match.matchUrl.isNotBlank() && (match.isLive || isMatchLikelyActive(match.status))) {
                enrichWithLiveStats(match)
            } else {
                match
            }
        }
    }

    /**
     * Fetches all matches available on the Cricbuzz live scores page.
     */
    suspend fun fetchAllMatches(): List<CricketMatch> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LIVE_SCORES_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.w(TAG, "HTTP error fetching Cricbuzz live scores: ${response.code}")
                return@withContext emptyList()
            }

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                AppLogger.w(TAG, "Empty response body from Cricbuzz")
                return@withContext emptyList()
            }

            parseMatches(html)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching Cricbuzz live scores: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Parses matches from the Cricbuzz HTML page.
     * Combines both DOM attributes and embedded React Server Component state.
     */
    private fun parseMatches(html: String): List<CricketMatch> {
        val matches = mutableListOf<CricketMatch>()
        val seenMatchIds = mutableSetOf<String>()

        // 1. Parse JSON state embedded in script tags (Cricbuzz Next.js data)
        parseEmbeddedJsonMatches(html, matches, seenMatchIds)

        // 2. Parse HTML cards as fallback or augmentation
        parseHtmlCards(html, matches, seenMatchIds)

        AppLogger.i(TAG, "Parsed a total of ${matches.size} unique matches from Cricbuzz")
        return matches
    }

    /**
     * Extracts match info from Next.js embedded JSON chunks in Cricbuzz HTML.
     */
    private fun parseEmbeddedJsonMatches(
        html: String,
        outList: MutableList<CricketMatch>,
        seenIds: MutableSet<String>
    ) {
        try {
            val matchInfoPattern = Pattern.compile(
                """"matchInfo":\{"matchId":(\d+).*?"seriesName":"(.*?)".*?"matchDesc":"(.*?)".*?"state":"(.*?)".*?"status":"(.*?)".*?"team1":\{.*?"teamName":"(.*?)".*?"teamSName":"(.*?)".*?\}.*?"team2":\{.*?"teamName":"(.*?)".*?"teamSName":"(.*?)".*?\}""",
                Pattern.DOTALL
            )
            val matcher = matchInfoPattern.matcher(html)

            while (matcher.find()) {
                val matchId = matcher.group(1)?.trim().orEmpty()
                if (matchId.isBlank() || seenIds.contains(matchId)) continue

                val seriesName = matcher.group(2)?.trim().orEmpty().replace("\\\"", "\"")
                val matchDesc = matcher.group(3)?.trim().orEmpty().replace("\\\"", "\"")
                val state = matcher.group(4)?.trim().orEmpty().replace("\\\"", "\"")
                val status = matcher.group(5)?.trim().orEmpty().replace("\\\"", "\"")
                val team1 = matcher.group(6)?.trim().orEmpty().replace("\\\"", "\"")
                val team1Short = matcher.group(7)?.trim().orEmpty().replace("\\\"", "\"")
                val team2 = matcher.group(8)?.trim().orEmpty().replace("\\\"", "\"")
                val team2Short = matcher.group(9)?.trim().orEmpty().replace("\\\"", "\"")

                // Try to find match scores near this match
                val (t1Score, t2Score) = extractScoresForMatch(html, matchId)

                val isLive = state.equals("In Progress", ignoreCase = true) ||
                        state.equals("Live", ignoreCase = true) ||
                        state.equals("Innings Break", ignoreCase = true)

                val slug = "${team1Short.lowercase()}-vs-${team2Short.lowercase()}"
                val matchUrl = "/live-cricket-scores/$matchId/$slug"

                val match = CricketMatch(
                    id = matchId,
                    title = if (matchDesc.isNotBlank()) "$team1Short vs $team2Short, $matchDesc" else "$team1Short vs $team2Short",
                    seriesName = seriesName,
                    team1 = team1,
                    team1Short = team1Short,
                    team2 = team2,
                    team2Short = team2Short,
                    team1Score = t1Score,
                    team2Score = t2Score,
                    status = status.ifBlank { state },
                    matchUrl = matchUrl,
                    isLive = isLive
                )

                seenIds.add(matchId)
                outList.add(match)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error in parseEmbeddedJsonMatches: ${e.message}")
        }
    }

    /**
     * Extracts scores near a match ID in embedded JSON.
     */
    private fun extractScoresForMatch(html: String, matchId: String): Pair<String, String> {
        var t1Score = ""
        var t2Score = ""
        try {
            val pos = html.indexOf(matchId)
            if (pos != -1) {
                val searchWindow = html.substring(pos, (pos + 3500).coerceAtMost(html.length))

                // Check team1Score runs/wickets/overs
                val t1Pattern = Pattern.compile(
                    """"team1Score":\{"inngs1":\{"inningsId":\d+,"runs":(\d+)(?:,"wickets":(\d+))?,"overs":([0-9.]+)\}"""
                )
                val m1 = t1Pattern.matcher(searchWindow)
                if (m1.find()) {
                    val runs = m1.group(1) ?: "0"
                    val wkts = m1.group(2) ?: "10"
                    val overs = m1.group(3) ?: ""
                    t1Score = if (overs.isNotBlank()) "$runs/$wkts ($overs)" else "$runs/$wkts"
                }

                // Check team2Score runs/wickets/overs
                val t2Pattern = Pattern.compile(
                    """"team2Score":\{"inngs1":\{"inningsId":\d+,"runs":(\d+)(?:,"wickets":(\d+))?,"overs":([0-9.]+)\}"""
                )
                val m2 = t2Pattern.matcher(searchWindow)
                if (m2.find()) {
                    val runs = m2.group(1) ?: "0"
                    val wkts = m2.group(2) ?: "10"
                    val overs = m2.group(3) ?: ""
                    t2Score = if (overs.isNotBlank()) "$runs/$wkts ($overs)" else "$runs/$wkts"
                }
            }
        } catch (ignored: Exception) {
        }
        return Pair(t1Score, t2Score)
    }

    /**
     * Parses match cards directly from HTML tags.
     */
    private fun parseHtmlCards(
        html: String,
        outList: MutableList<CricketMatch>,
        seenIds: MutableSet<String>
    ) {
        try {
            // Pattern to find match card links:
            // <a title="India vs New Zealand, 5th ODI - Innings Break" href="/live-cricket-scores/161282/ind-vs-nz-..."
            val linkPattern = Pattern.compile(
                """<a[^>]*title="([^"]*vs[^"]*)"[^>]*href="(/live-cricket-scores/(\d+)/[^"]+)"""",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = linkPattern.matcher(html)

            while (matcher.find()) {
                val rawTitle = matcher.group(1)?.trim().orEmpty()
                val matchUrl = matcher.group(2)?.trim().orEmpty()
                val matchId = matcher.group(3)?.trim().orEmpty()

                if (matchId.isBlank() || seenIds.contains(matchId)) continue

                // Title usually: "India vs New Zealand, 5th ODI - Innings Break"
                val titleParts = rawTitle.split("-")
                val matchTitle = titleParts.getOrNull(0)?.trim() ?: rawTitle
                val status = titleParts.drop(1).joinToString("-").trim()

                // Extract team names
                val vsPattern = Pattern.compile("""(.*?)\s+vs\s+(.*?),?\s*(.*)?""", Pattern.CASE_INSENSITIVE)
                val vm = vsPattern.matcher(matchTitle)
                val t1 = if (vm.find()) vm.group(1)?.trim().orEmpty() else ""
                val t2 = if (vm.matches()) vm.group(2)?.trim().orEmpty() else ""

                val isLive = isMatchLikelyActive(status)

                val match = CricketMatch(
                    id = matchId,
                    title = matchTitle,
                    team1 = t1,
                    team1Short = t1.take(4).uppercase(),
                    team2 = t2,
                    team2Short = t2.take(4).uppercase(),
                    status = status,
                    matchUrl = matchUrl,
                    isLive = isLive
                )

                seenIds.add(matchId)
                outList.add(match)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error in parseHtmlCards: ${e.message}")
        }
    }

    /**
     * Enriches a live match with Current Run Rate (CRR), Required Run Rate (RRR),
     * and live batting score from its detail commentary page.
     */
    suspend fun enrichWithLiveStats(match: CricketMatch): CricketMatch = withContext(Dispatchers.IO) {
        val detailUrl = if (match.matchUrl.startsWith("http")) match.matchUrl else "$BASE_URL${match.matchUrl}"
        try {
            AppLogger.d(TAG, "Enriching match ${match.title} from $detailUrl")
            val request = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext match

            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return@withContext match

            // 1. Extract CRR: <span[^>]*>CRR:?</span>\s*<span[^>]*>5.72</span>
            var crr = ""
            val crrPattern = Pattern.compile(
                """(?:CRR:?|Current Run Rate)[:\s]*</span>\s*<span[^>]*>([0-9.]+)</span>""",
                Pattern.CASE_INSENSITIVE
            )
            val crrMatcher = crrPattern.matcher(html)
            if (crrMatcher.find()) {
                crr = crrMatcher.group(1)?.trim().orEmpty()
            } else {
                // Secondary check: "CRR:</span><span...>5.72"
                val crrAltPattern = Pattern.compile("""CRR:?</span>.*?<span[^>]*>([0-9.]+)</span>""", Pattern.DOTALL)
                val m = crrAltPattern.matcher(html)
                if (m.find()) {
                    crr = m.group(1)?.trim().orEmpty()
                }
            }

            // 2. Extract RRR: <span[^>]*>RRR:?</span>\s*<span[^>]*>6.20</span>
            var rrr = ""
            val rrrPattern = Pattern.compile(
                """(?:RRR:?|Req(?:uired)?\s*RR)[:\s]*</span>\s*<span[^>]*>([0-9.]+)</span>""",
                Pattern.CASE_INSENSITIVE
            )
            val rrrMatcher = rrrPattern.matcher(html)
            if (rrrMatcher.find()) {
                rrr = rrrMatcher.group(1)?.trim().orEmpty()
            } else {
                val rrrAltPattern = Pattern.compile("""(?:RRR|Req\s*RR):?</span>.*?<span[^>]*>([0-9.]+)</span>""", Pattern.DOTALL)
                val m = rrrAltPattern.matcher(html)
                if (m.find()) {
                    rrr = m.group(1)?.trim().orEmpty()
                }
            }

            // 3. Extract Live Batting Team & Score
            // Pattern: <div class="mr-2">ENG</div><div><span class="mr-2"><span>164</span>...-2</span></span><span class="mr-2">(28.4)</span>
            var liveScore = match.liveScore
            var battingTeam = match.currentBatting
            val scoreBlockPattern = Pattern.compile(
                """<div[^>]*class="[^"]*font-bold[^"]*"[^>]*>\s*<div[^>]*class="mr-2">([A-Za-z0-9]+)</div>.*?<span>(\d+)</span>(?:.*?-\s*(\d+))?</span>.*?\(<!-- -->([0-9.]+)<!-- -->\)""",
                Pattern.DOTALL
            )
            val sm = scoreBlockPattern.matcher(html)
            if (sm.find()) {
                battingTeam = sm.group(1)?.trim().orEmpty()
                val runs = sm.group(2)?.trim().orEmpty()
                val wkts = sm.group(3)?.trim() ?: ""
                val overs = sm.group(4)?.trim().orEmpty()
                val wktStr = if (wkts.isNotBlank()) "/$wkts" else ""
                val overStr = if (overs.isNotBlank()) " ($overs)" else ""
                liveScore = "$runs$wktStr$overStr"
            }

            // 4. Extract detailed live status line
            var liveStatus = match.status
            val statusPattern = Pattern.compile(
                """<div[^>]*class="[^"]*text-cbTxtLive[^"]*"[^>]*>(.*?)</div>""",
                Pattern.CASE_INSENSITIVE
            )
            val stm = statusPattern.matcher(html)
            if (stm.find()) {
                val extracted = stm.group(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
                if (extracted.isNotBlank()) {
                    liveStatus = extracted
                }
            }

            AppLogger.d(TAG, "Enriched ${match.title} -> Score: $liveScore, CRR: $crr, RRR: $rrr, Status: $liveStatus")

            match.copy(
                currentBatting = battingTeam,
                liveScore = liveScore.ifBlank { match.team1Score.ifBlank { match.team2Score } },
                crr = crr,
                rrr = rrr,
                status = liveStatus,
                isLive = true
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to enrich match detail: ${e.message}")
            match
        }
    }

    private fun isMatchForFollowedTeams(
        match: CricketMatch,
        followedTeams: List<FollowedCricketTeam>
    ): Boolean {
        for (f in followedTeams) {
            val name = f.name.lowercase().trim()
            val short = f.shortName.lowercase().trim()

            val t1 = match.team1.lowercase()
            val t1s = match.team1Short.lowercase()
            val t2 = match.team2.lowercase()
            val t2s = match.team2Short.lowercase()
            val title = match.title.lowercase()

            if (t1.contains(name) || t1s == short ||
                t2.contains(name) || t2s == short ||
                title.contains(name) || title.contains(short)
            ) {
                return true
            }
        }
        return false
    }

    private fun isMatchLikelyActive(status: String): Boolean {
        val s = status.lowercase()
        return s.contains("live") ||
                s.contains("opt to") ||
                s.contains("need ") ||
                s.contains("trail by") ||
                s.contains("lead by") ||
                s.contains("innings break") ||
                s.contains("ings break") ||
                s.contains("tea") ||
                s.contains("lunch") ||
                s.contains("stump")
    }
}
