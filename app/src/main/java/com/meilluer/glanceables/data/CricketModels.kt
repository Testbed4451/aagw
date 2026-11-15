package com.meilluer.glanceables.data

import com.google.gson.annotations.SerializedName

/**
 * Data model for a cricket team available for selection.
 */
data class CricketTeam(
    val name: String,
    val shortName: String,
    val isLeague: Boolean = false,
    val leagueOrCategory: String = "National"
)

/**
 * Persisted model representing a cricket team the user follows.
 */
data class FollowedCricketTeam(
    @SerializedName("name") val name: String,
    @SerializedName("shortName") val shortName: String,
    @SerializedName("category") val category: String = "National"
)

/**
 * Data model representing a live or recent cricket match scraped from Cricbuzz.
 */
data class CricketMatch(
    val id: String = "",
    val title: String = "",
    val seriesName: String = "",
    val team1: String = "",
    val team1Short: String = "",
    val team2: String = "",
    val team2Short: String = "",
    val team1Score: String = "",
    val team2Score: String = "",
    val currentBatting: String = "",
    val liveScore: String = "",
    val overs: String = "",
    val crr: String = "",
    val rrr: String = "",
    val status: String = "",
    val matchUrl: String = "",
    val isLive: Boolean = false
) {
    /**
     * Formatted summary string for display or watch complication.
     * Example: "IND 164/2 (28.4) • CRR: 5.72 • RRR: 6.20"
     */
    fun getComplicationSummary(): String {
        val scorePart = when {
            liveScore.isNotBlank() -> "$currentBatting $liveScore"
            team1Score.isNotBlank() && team2Score.isNotBlank() -> "$team1Short $team1Score vs $team2Short $team2Score"
            team1Score.isNotBlank() -> "$team1Short $team1Score"
            else -> "$team1Short vs $team2Short"
        }

        val crrPart = if (crr.isNotBlank()) "CRR $crr" else ""
        val rrrPart = if (rrr.isNotBlank()) "RRR $rrr" else ""

        val rates = listOf(crrPart, rrrPart).filter { it.isNotBlank() }.joinToString(" • ")

        return when {
            rates.isNotBlank() -> "$scorePart ($rates)"
            status.isNotBlank() -> "$scorePart • $status"
            else -> scorePart
        }
    }
}

/**
 * Master catalog of national and popular league teams for selection.
 */
object CricketTeamCatalog {

    val NATIONAL_TEAMS = listOf(
        CricketTeam("India", "IND", false, "National"),
        CricketTeam("Australia", "AUS", false, "National"),
        CricketTeam("England", "ENG", false, "National"),
        CricketTeam("South Africa", "RSA", false, "National"),
        CricketTeam("New Zealand", "NZ", false, "National"),
        CricketTeam("Pakistan", "PAK", false, "National"),
        CricketTeam("West Indies", "WI", false, "National"),
        CricketTeam("Sri Lanka", "SL", false, "National"),
        CricketTeam("Bangladesh", "BAN", false, "National"),
        CricketTeam("Afghanistan", "AFG", false, "National"),
        CricketTeam("Ireland", "IRE", false, "National"),
        CricketTeam("Zimbabwe", "ZIM", false, "National"),
        CricketTeam("Netherlands", "NED", false, "National"),
        CricketTeam("Scotland", "SCO", false, "National"),
        CricketTeam("Nepal", "NEP", false, "National"),
        CricketTeam("United States", "USA", false, "National"),
        CricketTeam("Canada", "CAN", false, "National"),
        CricketTeam("Namibia", "NAM", false, "National"),
        CricketTeam("Oman", "OMA", false, "National"),
        CricketTeam("UAE", "UAE", false, "National")
    )

    val LEAGUE_TEAMS = listOf(
        // Indian Premier League (IPL)
        CricketTeam("Chennai Super Kings", "CSK", true, "IPL"),
        CricketTeam("Mumbai Indians", "MI", true, "IPL"),
        CricketTeam("Royal Challengers Bengaluru", "RCB", true, "IPL"),
        CricketTeam("Kolkata Knight Riders", "KKR", true, "IPL"),
        CricketTeam("Gujarat Titans", "GT", true, "IPL"),
        CricketTeam("Lucknow Super Giants", "LSG", true, "IPL"),
        CricketTeam("Delhi Capitals", "DC", true, "IPL"),
        CricketTeam("Punjab Kings", "PBKS", true, "IPL"),
        CricketTeam("Rajasthan Royals", "RR", true, "IPL"),
        CricketTeam("Sunrisers Hyderabad", "SRH", true, "IPL"),

        // Big Bash League (BBL)
        CricketTeam("Sydney Sixers", "SYS", true, "BBL"),
        CricketTeam("Perth Scorchers", "PRS", true, "BBL"),
        CricketTeam("Melbourne Stars", "MLS", true, "BBL"),
        CricketTeam("Melbourne Renegades", "MLR", true, "BBL"),
        CricketTeam("Brisbane Heat", "BRH", true, "BBL"),
        CricketTeam("Adelaide Strikers", "ADS", true, "BBL"),
        CricketTeam("Sydney Thunder", "SYT", true, "BBL"),
        CricketTeam("Hobart Hurricanes", "HBH", true, "BBL"),

        // Pakistan Super League (PSL)
        CricketTeam("Lahore Qalandars", "LQ", true, "PSL"),
        CricketTeam("Karachi Kings", "KK", true, "PSL"),
        CricketTeam("Islamabad United", "IU", true, "PSL"),
        CricketTeam("Peshawar Zalmi", "PZ", true, "PSL"),
        CricketTeam("Quetta Gladiators", "QG", true, "PSL"),
        CricketTeam("Multan Sultans", "MS", true, "PSL"),

        // Caribbean Premier League (CPL)
        CricketTeam("Trinbago Knight Riders", "TKR", true, "CPL"),
        CricketTeam("Guyana Amazon Warriors", "GAW", true, "CPL"),
        CricketTeam("Barbados Royals", "BR", true, "CPL"),
        CricketTeam("St Lucia Kings", "SLK", true, "CPL"),

        // SA20
        CricketTeam("Sunrisers Eastern Cape", "SEC", true, "SA20"),
        CricketTeam("Durban's Super Giants", "DSG", true, "SA20"),
        CricketTeam("MI Cape Town", "MICT", true, "SA20"),
        CricketTeam("Paarl Royals", "PR", true, "SA20"),
        CricketTeam("Pretoria Capitals", "PC", true, "SA20"),
        CricketTeam("Joburg Super Kings", "JSK", true, "SA20")
    )

    val ALL_TEAMS = NATIONAL_TEAMS + LEAGUE_TEAMS
}
