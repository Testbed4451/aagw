package com.meilluer.glanceables.api

import com.meilluer.glanceables.data.FootballMatchResponse
import com.meilluer.glanceables.data.FootballTeamListResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the football-data.org v4 API.
 *
 * Auth is provided per-request via the `X-Auth-Token` header.
 *
 * Free-tier available competition codes:
 * WC, CL, BL1, DED, BSA, PD, FL1, PL, PPL, EC, SA, ELC
 */
interface FootballApiService {

    /**
     * List teams in a competition.
     *
     * @param authToken  API key passed as `X-Auth-Token`.
     * @param code       Competition code (e.g. "PL", "CL").
     * @param season     Optional season year (e.g. 2024). Defaults to the current season.
     */
    @GET("competitions/{code}/teams")
    suspend fun getTeamsForCompetition(
        @Header("X-Auth-Token") authToken: String,
        @Path("code") code: String,
        @Query("season") season: Int? = null
    ): FootballTeamListResponse

    /**
     * Get matches for a specific team.
     *
     * @param authToken  API key.
     * @param teamId     Team ID.
     * @param status     Match status filter (SCHEDULED, LIVE, IN_PLAY, PAUSED, FINISHED, etc.).
     * @param dateFrom   Start date filter (yyyy-MM-dd).
     * @param dateTo     End date filter (yyyy-MM-dd).
     * @param limit      Maximum number of matches to return.
     */
    @GET("teams/{id}/matches")
    suspend fun getMatchesForTeam(
        @Header("X-Auth-Token") authToken: String,
        @Path("id") teamId: Int,
        @Query("status") status: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("limit") limit: Int? = null
    ): FootballMatchResponse

    /**
     * Get matches across competitions for a given date.
     *
     * @param authToken  API key.
     * @param date       Date filter (yyyy-MM-dd). Returns today's matches when provided.
     */
    @GET("matches")
    suspend fun getMatches(
        @Header("X-Auth-Token") authToken: String,
        @Query("date") date: String? = null
    ): FootballMatchResponse
}
