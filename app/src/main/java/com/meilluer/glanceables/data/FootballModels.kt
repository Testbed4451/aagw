package com.meilluer.glanceables.data

import com.google.gson.annotations.SerializedName

// ── football-data.org API v4 Response Models ─────────────────────────────────

/**
 * Top-level response for match-related endpoints.
 */
data class FootballMatchResponse(
    @SerializedName("matches") val matches: List<FootballMatch> = emptyList(),
    @SerializedName("resultSet") val resultSet: FootballResultSet? = null
)

data class FootballResultSet(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("first") val first: String = "",
    @SerializedName("last") val last: String = "",
    @SerializedName("played") val played: Int = 0
)

/**
 * A single match from the football-data.org API.
 */
data class FootballMatch(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("competition") val competition: FootballCompetition = FootballCompetition(),
    @SerializedName("homeTeam") val homeTeam: FootballTeam = FootballTeam(),
    @SerializedName("awayTeam") val awayTeam: FootballTeam = FootballTeam(),
    @SerializedName("score") val score: FootballScore = FootballScore(),
    @SerializedName("status") val status: String = "",
    @SerializedName("utcDate") val utcDate: String = "",
    @SerializedName("matchday") val matchday: Int? = null
)

/**
 * Team information returned by the API.
 */
data class FootballTeam(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("shortName") val shortName: String = "",
    @SerializedName("tla") val tla: String = "",
    @SerializedName("crest") val crest: String = ""
)

/**
 * Competition (league / tournament) information.
 */
data class FootballCompetition(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("code") val code: String = "",
    @SerializedName("emblem") val emblem: String = ""
)

/**
 * Score wrapper containing full-time and half-time details.
 */
data class FootballScore(
    @SerializedName("winner") val winner: String? = null,
    @SerializedName("fullTime") val fullTime: FootballScoreDetail = FootballScoreDetail(),
    @SerializedName("halfTime") val halfTime: FootballScoreDetail = FootballScoreDetail()
)

/**
 * Individual score detail (home / away goals).
 */
data class FootballScoreDetail(
    @SerializedName("home") val home: Int? = null,
    @SerializedName("away") val away: Int? = null
)

// ── Competition Teams Endpoint Response ──────────────────────────────────────

/**
 * Response from `GET competitions/{code}/teams`.
 */
data class FootballTeamListResponse(
    @SerializedName("teams") val teams: List<FootballTeam> = emptyList()
)

// ── Local / Processed Models ─────────────────────────────────────────────────

/**
 * Lightweight model stored locally to track which teams the user follows.
 */
data class FollowedTeamInfo(
    val teamId: Int = 0,
    val teamName: String = "",
    val tla: String = "",
    val competitionCode: String = ""
)

/**
 * Processed model for presenting a matchday in the UI.
 */
data class FootballMatchDay(
    val matches: List<FootballMatch> = emptyList(),
    val matchday: Int = 0,
    val competitionName: String = ""
)
