package com.meilluer.glanceables.repository

import com.meilluer.glanceables.api.FootballRetrofitClient
import com.meilluer.glanceables.data.FootballMatch
import com.meilluer.glanceables.data.FootballTeam
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sealed class representing the result of a football data fetch.
 */
sealed class FootballResult {
    /** Matches happening today for followed teams. */
    data class TodayMatches(val matches: List<FootballMatch>) : FootballResult()

    /** No matches today — the next scheduled match for a followed team. */
    data class UpcomingMatch(val match: FootballMatch, val date: String) : FootballResult()

    /** No matches found at all. */
    data object NoMatches : FootballResult()
}

/**
 * Repository that fetches football data from football-data.org v4 API.
 *
 * All methods accept the API key as a parameter to keep the repository
 * stateless with respect to credentials.
 */
class FootballRepository {

    private val service = FootballRetrofitClient.service

    /**
     * Returns the list of teams for the given competition code
     * (e.g. "PL" for Premier League).
     *
     * Useful for letting the user pick which teams to follow.
     */
    suspend fun getTeamsForCompetition(
        apiKey: String,
        competitionCode: String
    ): List<FootballTeam> {
        return service.getTeamsForCompetition(apiKey, competitionCode).teams
    }

    /**
     * Fetches today's matches across all competitions and filters them
     * to only include matches involving the given [teamIds].
     *
     * Uses a single API call (`GET /matches?date=<today>`) to stay
     * rate-limit friendly.
     */
    suspend fun getTodayMatchesForTeams(
        apiKey: String,
        teamIds: List<Int>
    ): List<FootballMatch> {
        val today = todayDateString()
        val response = service.getMatches(apiKey, date = today)
        val teamIdSet = teamIds.toSet()
        return response.matches.filter { match ->
            match.homeTeam.id in teamIdSet || match.awayTeam.id in teamIdSet
        }
    }

    /**
     * Returns the next scheduled match for a specific team.
     *
     * Uses `GET /teams/{id}/matches?status=SCHEDULED&limit=1`.
     */
    suspend fun getNextMatchForTeam(
        apiKey: String,
        teamId: Int
    ): FootballMatch? {
        val response = service.getMatchesForTeam(
            authToken = apiKey,
            teamId = teamId,
            status = "SCHEDULED",
            limit = 1
        )
        return response.matches.firstOrNull()
    }

    /**
     * Smart fetch that first checks today's matches for all followed teams.
     *
     * If there are matches today, returns [FootballResult.TodayMatches].
     * Otherwise, fetches the next scheduled match for the first followed
     * team and returns [FootballResult.UpcomingMatch].
     *
     * If nothing is found, returns [FootballResult.NoMatches].
     */
    suspend fun getMatchesOrNextDay(
        apiKey: String,
        teamIds: List<Int>
    ): FootballResult {
        if (teamIds.isEmpty()) return FootballResult.NoMatches

        // 1. Check today's matches (single API call)
        val todayMatches = getTodayMatchesForTeams(apiKey, teamIds)

        if (todayMatches.isNotEmpty()) {
            return FootballResult.TodayMatches(todayMatches)
        }

        // 2. No matches today — find the earliest next scheduled match among followed teams
        var earliestMatch: FootballMatch? = null
        // Limit to up to 5 teams to avoid burning rate limits on free tier (10 calls/min)
        val teamsToCheck = teamIds.take(5)
        for (teamId in teamsToCheck) {
            try {
                val match = getNextMatchForTeam(apiKey, teamId)
                if (match != null) {
                    if (earliestMatch == null || match.utcDate < earliestMatch.utcDate) {
                        earliestMatch = match
                    }
                }
            } catch (e: Exception) {
                // Ignore failure for one team and continue
            }
        }

        return if (earliestMatch != null) {
            FootballResult.UpcomingMatch(
                match = earliestMatch,
                date = earliestMatch.utcDate
            )
        } else {
            FootballResult.NoMatches
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
