package com.meilluer.glanceables

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meilluer.glanceables.alarm.TravelAlarmScheduler
import com.meilluer.glanceables.data.FollowedTeamInfo
import com.meilluer.glanceables.data.FootballMatch
import com.meilluer.glanceables.data.FootballTeam
import com.meilluer.glanceables.data.TravelInfo
import com.meilluer.glanceables.data.WeatherData
import com.meilluer.glanceables.databinding.ActivityMainBinding
import com.meilluer.glanceables.databinding.DialogTeamSelectorBinding
import com.meilluer.glanceables.databinding.ItemFootballMatchBinding
import com.meilluer.glanceables.databinding.ItemTeamCheckboxBinding
import com.meilluer.glanceables.pdf.TravelPdfExtractor
import com.meilluer.glanceables.repository.FootballRepository
import com.meilluer.glanceables.repository.FootballResult
import com.meilluer.glanceables.repository.WeatherRepository
import com.meilluer.glanceables.storage.PreferencesManager
import com.meilluer.glanceables.sync.WearableDataSender
import com.meilluer.glanceables.sync.WeatherSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private val repository = WeatherRepository()
    private lateinit var wearableSender: WearableDataSender
    private lateinit var pdfExtractor: TravelPdfExtractor
    private lateinit var alarmScheduler: TravelAlarmScheduler
    private val footballRepository = FootballRepository()
    private var currentFootballMatches: List<FootballMatch> = emptyList()
    private var currentIsUpcoming: Boolean = false

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            processPdfUri(uri)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        wearableSender = WearableDataSender(this)
        pdfExtractor = TravelPdfExtractor(this)
        alarmScheduler = TravelAlarmScheduler(this)

        setupUi()
        setupTravelUi()
        setupFootballUi()
        loadCachedData()
        loadStoredTravelInfo()
        loadStoredFootballInfo()
    }

    data class IntervalOption(val label: String, val minutes: Long)

    private val intervalOptions = listOf(
        IntervalOption("15 minutes (minimum)", 15L),
        IntervalOption("30 minutes (recommended)", 30L),
        IntervalOption("1 hour", 60L),
        IntervalOption("2 hours", 120L),
        IntervalOption("6 hours", 360L),
        IntervalOption("12 hours", 720L),
        IntervalOption("24 hours", 1440L)
    )

    private fun setupUi() {
        // Restore saved API key
        val savedKey = prefs.getApiKey()
        if (savedKey.isNotBlank()) {
            binding.editApiKey.setText(savedKey)
        }

        // Setup interval selector
        setupIntervalDropdown()

        // Save API key button
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.editApiKey.text.toString().trim()
            if (key.isBlank()) {
                binding.apiKeyInputLayout.error = "API key is required"
                return@setOnClickListener
            }
            binding.apiKeyInputLayout.error = null
            prefs.setApiKey(key)
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            schedulePeriodicSync()
        }

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            fetchWeatherWithLocation()
        }

        // Swipe-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            fetchWeatherWithLocation()
        }
    }

    private fun setupIntervalDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            intervalOptions.map { it.label }
        )
        binding.dropdownInterval.setAdapter(adapter)

        val currentMinutes = prefs.getRefreshIntervalMinutes()
        val currentOption = intervalOptions.find { it.minutes == currentMinutes }
            ?: intervalOptions[1] // Default to 30 min

        binding.dropdownInterval.setText(currentOption.label, false)

        binding.dropdownInterval.setOnItemClickListener { _, _, position, _ ->
            val selected = intervalOptions[position]
            prefs.setRefreshIntervalMinutes(selected.minutes)
            Toast.makeText(
                this,
                getString(R.string.refresh_interval_updated, selected.label),
                Toast.LENGTH_SHORT
            ).show()
            if (prefs.hasApiKey() && prefs.hasLocation()) {
                schedulePeriodicSync()
            }
        }
    }

    private fun loadCachedData() {
        val cached = prefs.getCachedWeatherData()
        if (cached != null) {
            displayWeatherData(cached)
        }
    }

    private fun fetchWeatherWithLocation() {
        if (!prefs.hasApiKey()) {
            Toast.makeText(this, "Please enter and save your API key first", Toast.LENGTH_LONG).show()
            binding.swipeRefresh.isRefreshing = false
            return
        }

        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
            binding.swipeRefresh.isRefreshing = false
            return
        }

        setLoading(true)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                prefs.setLocation(location.latitude, location.longitude)
                fetchWeatherData(location.latitude, location.longitude)
            } else {
                // Fall back to last known location
                val lat = prefs.getLatitude()
                val lon = prefs.getLongitude()
                if (lat != 0.0 || lon != 0.0) {
                    fetchWeatherData(lat, lon)
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Unable to get location", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { e ->
            setLoading(false)
            Toast.makeText(this, "Location error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchWeatherData(lat: Double, lon: Double) {
        val apiKey = prefs.getApiKey()

        lifecycleScope.launch {
            try {
                val weatherData = repository.fetchWeatherData(lat, lon, apiKey)
                prefs.setCachedWeatherData(weatherData)
                displayWeatherData(weatherData)

                // Push to watch
                wearableSender.sendToWatch(weatherData)

                // Ensure periodic sync is running
                schedulePeriodicSync()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun displayWeatherData(data: WeatherData) {
        binding.apply {
            cardWeather.visibility = View.VISIBLE

            // City
            textCity.text = data.cityName.ifBlank { "Unknown" }

            // Temperature
            textTemperature.text = String.format(Locale.getDefault(), "%.1f°C", data.temperature)
            textFeelsLike.text = String.format(
                Locale.getDefault(), "Feels like %.1f°C  •  Humidity %d%%",
                data.feelsLike, data.humidity
            )

            // UV Index
            textUvIndex.text = String.format(Locale.getDefault(), "%.1f", data.uvIndex)
            textUvLabel.text = uvIndexLabel(data.uvIndex)

            // AQI
            textAqi.text = data.aqi.toString()
            textAqiLabel.text = data.aqiLabel
            textPm25.text = String.format(Locale.getDefault(), "PM2.5: %.1f µg/m³", data.pm25)

            // Last updated
            if (data.lastUpdated > 0) {
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                textLastUpdated.text = "Updated: ${sdf.format(Date(data.lastUpdated))}"
                textLastUpdated.visibility = View.VISIBLE
            }
        }
    }

    private fun uvIndexLabel(uv: Double): String = when {
        uv <= 2 -> "Low"
        uv <= 5 -> "Moderate"
        uv <= 7 -> "High"
        uv <= 10 -> "Very High"
        else -> "Extreme"
    }

    private fun setLoading(loading: Boolean) {
        binding.swipeRefresh.isRefreshing = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
    }

    private fun schedulePeriodicSync() {
        val intervalMinutes = prefs.getRefreshIntervalMinutes()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchWeatherWithLocation()
            } else {
                Toast.makeText(this, "Location permission required for weather data", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Travel / PDF Extraction UI Handlers ───────────────────────────────────

    private fun setupTravelUi() {
        // Pick and extract PDF button
        binding.btnExtractPdf.setOnClickListener {
            pdfPickerLauncher.launch(arrayOf("application/pdf"))
        }

        // Save button
        binding.btnSaveTravelInfo.setOnClickListener {
            saveCurrentTravelInfo()
        }

        // Clear button
        binding.btnClearTravelInfo.setOnClickListener {
            clearTravelInfo()
        }

        // Push to watch button
        binding.btnSendTravelToWatch.setOnClickListener {
            pushTravelInfoToWatch()
        }
    }

    private fun loadStoredTravelInfo() {
        val travelInfo = prefs.getTravelInfo()
        if (travelInfo.isNotEmpty()) {
            displayTravelInfo(travelInfo)
        }
    }

    private fun displayTravelInfo(info: TravelInfo) {
        binding.editSeatNumber.setText(info.seatNumber)
        binding.editBirthtype.setText(info.birthtype)
        binding.editDateOfJourney.setText(info.dateOfJourney ?: "")
        binding.editDepartureTime.setText(info.departureTime ?: "")
        binding.editArrivalTime.setText(info.arrivalTime)
        binding.editBoardingStation.setText(info.boardingStation)
        binding.editDeboardingStation.setText(info.deboardingStation)
        binding.editTrainNumber.setText(info.trainNumber)
        binding.editTrainName.setText(info.trainName)
    }

    private fun processPdfUri(uri: Uri) {
        binding.pdfProgressBar.visibility = View.VISIBLE
        binding.btnExtractPdf.isEnabled = false

        lifecycleScope.launch {
            try {
                val travelInfo = withContext(Dispatchers.IO) {
                    val text = pdfExtractor.extractTextFromUri(uri)
                    pdfExtractor.parse(text)
                }

                if (travelInfo.isNotEmpty()) {
                    displayTravelInfo(travelInfo)
                    prefs.saveTravelInfo(travelInfo)
                    alarmScheduler.scheduleAlarms(travelInfo)
                    wearableSender.sendTravelInfoToWatch(travelInfo)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.travel_extracted_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not detect travel fields automatically. You can fill them in and click Save.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.pdf_error, e.localizedMessage ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.pdfProgressBar.visibility = View.GONE
                binding.btnExtractPdf.isEnabled = true
            }
        }
    }

    private fun getTravelInfoFromInputs(): TravelInfo {
        val dateOfJourney = binding.editDateOfJourney.text?.toString()?.trim()
            .let { if (it.isNullOrBlank()) null else it }
        val departureTime = binding.editDepartureTime.text?.toString()?.trim()
            .let { if (it.isNullOrBlank()) null else it }

        return TravelInfo(
            seatNumber = binding.editSeatNumber.text?.toString()?.trim().orEmpty(),
            birthtype = binding.editBirthtype.text?.toString()?.trim().orEmpty(),
            dateOfJourney = dateOfJourney,
            departureTime = departureTime,
            arrivalTime = binding.editArrivalTime.text?.toString()?.trim().orEmpty(),
            boardingStation = binding.editBoardingStation.text?.toString()?.trim().orEmpty(),
            deboardingStation = binding.editDeboardingStation.text?.toString()?.trim().orEmpty(),
            trainNumber = binding.editTrainNumber.text?.toString()?.trim().orEmpty(),
            trainName = binding.editTrainName.text?.toString()?.trim().orEmpty()
        )
    }

    private fun saveCurrentTravelInfo() {
        val info = getTravelInfoFromInputs()
        prefs.saveTravelInfo(info)
        alarmScheduler.scheduleAlarms(info)
        Toast.makeText(this, getString(R.string.travel_saved_success), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            wearableSender.sendTravelInfoToWatch(info)
        }
    }

    private fun clearTravelInfo() {
        prefs.clearTravelInfo()
        alarmScheduler.cancelAlarms()
        binding.editSeatNumber.setText("")
        binding.editBirthtype.setText("")
        binding.editDateOfJourney.setText("")
        binding.editDepartureTime.setText("")
        binding.editArrivalTime.setText("")
        binding.editBoardingStation.setText("")
        binding.editDeboardingStation.setText("")
        binding.editTrainNumber.setText("")
        binding.editTrainName.setText("")
        Toast.makeText(this, getString(R.string.travel_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun pushTravelInfoToWatch() {
        val info = getTravelInfoFromInputs()
        lifecycleScope.launch {
            wearableSender.sendTravelInfoToWatch(info)
            Toast.makeText(this@MainActivity, "Travel details sent to watch", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Football Data (football-data.org) ─────────────────────────────────────

    data class CompetitionOption(val name: String, val code: String)

    private val freeTierCompetitions = listOf(
        CompetitionOption("Premier League (England)", "PL"),
        CompetitionOption("UEFA Champions League", "CL"),
        CompetitionOption("La Liga (Spain)", "PD"),
        CompetitionOption("Serie A (Italy)", "SA"),
        CompetitionOption("Bundesliga (Germany)", "BL1"),
        CompetitionOption("Ligue 1 (France)", "FL1"),
        CompetitionOption("Eredivisie (Netherlands)", "DED"),
        CompetitionOption("Championship (England)", "ELC"),
        CompetitionOption("Primeira Liga (Portugal)", "PPL"),
        CompetitionOption("Série A (Brazil)", "BSA")
    )

    private fun setupFootballUi() {
        val savedKey = prefs.getFootballApiKey()
        if (savedKey.isNotBlank()) {
            binding.editFootballApiKey.setText(savedKey)
        }

        binding.btnSaveFootballApiKey.setOnClickListener {
            val key = binding.editFootballApiKey.text?.toString()?.trim().orEmpty()
            if (key.isBlank()) {
                binding.footballApiKeyInputLayout.error = "API key is required"
                return@setOnClickListener
            }
            binding.footballApiKeyInputLayout.error = null
            prefs.setFootballApiKey(key)
            Toast.makeText(this, getString(R.string.football_api_key_saved), Toast.LENGTH_SHORT).show()
            if (prefs.getFollowedTeams().isNotEmpty()) {
                fetchFootballData()
            }
        }

        binding.btnSelectTeams.setOnClickListener {
            showTeamSelectorDialog()
        }

        binding.btnFetchFootball.setOnClickListener {
            fetchFootballData()
        }

        binding.btnSendFootballToWatch.setOnClickListener {
            pushFootballToWatch()
        }
    }

    private fun loadStoredFootballInfo() {
        updateFollowedTeamsChips()
        if (prefs.hasFootballApiKey() && prefs.getFollowedTeams().isNotEmpty()) {
            fetchFootballData()
        }
    }

    private fun updateFollowedTeamsChips() {
        binding.chipGroupFollowedTeams.removeAllViews()
        val followed = prefs.getFollowedTeams()
        if (followed.isEmpty()) {
            binding.textNoTeamsFollowed.visibility = View.VISIBLE
        } else {
            binding.textNoTeamsFollowed.visibility = View.GONE
            for (team in followed) {
                val chip = Chip(this).apply {
                    text = if (team.tla.isNotBlank()) "${team.teamName} (${team.tla})" else team.teamName
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        prefs.removeFollowedTeam(team.teamId)
                        updateFollowedTeamsChips()
                        if (prefs.getFollowedTeams().isNotEmpty()) {
                            fetchFootballData()
                        } else {
                            currentFootballMatches = emptyList()
                            binding.layoutFootballMatches.removeAllViews()
                            binding.textNoMatches.visibility = View.GONE
                        }
                    }
                }
                binding.chipGroupFollowedTeams.addView(chip)
            }
        }
    }

    private fun showTeamSelectorDialog() {
        val apiKey = prefs.getFootballApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.football_api_key_hint), Toast.LENGTH_LONG).show()
            return
        }

        val dialogBinding = DialogTeamSelectorBinding.inflate(layoutInflater)
        val tempFollowed = prefs.getFollowedTeams().toMutableList()

        val compNames = freeTierCompetitions.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, compNames)
        dialogBinding.dropdownCompetition.setAdapter(adapter)

        var selectedCompetition = freeTierCompetitions.first()
        dialogBinding.dropdownCompetition.setText(selectedCompetition.name, false)

        fun loadTeams(compCode: String) {
            dialogBinding.teamLoadingProgress.visibility = View.VISIBLE
            dialogBinding.layoutTeamsList.removeAllViews()

            lifecycleScope.launch {
                try {
                    val teams = withContext(Dispatchers.IO) {
                        footballRepository.getTeamsForCompetition(apiKey, compCode)
                    }
                    dialogBinding.teamLoadingProgress.visibility = View.GONE
                    dialogBinding.layoutTeamsList.removeAllViews()

                    for (team in teams) {
                        val rowBinding = ItemTeamCheckboxBinding.inflate(layoutInflater, dialogBinding.layoutTeamsList, false)
                        val displayName = if (team.tla.isNotBlank()) "${team.name} (${team.tla})" else team.name
                        rowBinding.checkboxTeam.text = displayName
                        rowBinding.checkboxTeam.isChecked = tempFollowed.any { it.teamId == team.id }

                        rowBinding.checkboxTeam.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                if (tempFollowed.none { it.teamId == team.id }) {
                                    tempFollowed.add(
                                        FollowedTeamInfo(
                                            teamId = team.id,
                                            teamName = team.name,
                                            tla = team.tla,
                                            competitionCode = compCode
                                        )
                                    )
                                }
                            } else {
                                tempFollowed.removeAll { it.teamId == team.id }
                            }
                        }
                        dialogBinding.layoutTeamsList.addView(rowBinding.root)
                    }
                } catch (e: Exception) {
                    dialogBinding.teamLoadingProgress.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Failed to load teams: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Load initial competition teams
        loadTeams(selectedCompetition.code)

        dialogBinding.dropdownCompetition.setOnItemClickListener { _, _, position, _ ->
            selectedCompetition = freeTierCompetitions[position]
            loadTeams(selectedCompetition.code)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_teams_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_teams) { _, _ ->
                prefs.setFollowedTeams(tempFollowed)
                updateFollowedTeamsChips()
                if (tempFollowed.isNotEmpty()) {
                    fetchFootballData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchFootballData() {
        val apiKey = prefs.getFootballApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.football_api_key_hint), Toast.LENGTH_SHORT).show()
            return
        }

        val followed = prefs.getFollowedTeams()
        if (followed.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_teams_followed), Toast.LENGTH_SHORT).show()
            return
        }

        binding.footballProgressBar.visibility = View.VISIBLE
        binding.btnFetchFootball.isEnabled = false

        lifecycleScope.launch {
            try {
                val teamIds = followed.map { it.teamId }
                val result = withContext(Dispatchers.IO) {
                    footballRepository.getMatchesOrNextDay(apiKey, teamIds)
                }

                when (result) {
                    is FootballResult.TodayMatches -> {
                        currentFootballMatches = result.matches
                        currentIsUpcoming = false
                        binding.textMatchSectionHeader.text = getString(R.string.today_matches_label)
                        binding.textNoMatches.visibility = View.GONE
                        displayFootballMatches(result.matches, isUpcoming = false)
                        // Push first match to watch for complication provider
                        if (result.matches.isNotEmpty()) {
                            wearableSender.sendFootballDataToWatch(result.matches.first(), isUpcoming = false)
                        }
                    }
                    is FootballResult.UpcomingMatch -> {
                        currentFootballMatches = listOf(result.match)
                        currentIsUpcoming = true
                        binding.textMatchSectionHeader.text = getString(R.string.next_match_label)
                        binding.textNoMatches.visibility = View.GONE
                        displayFootballMatches(listOf(result.match), isUpcoming = true)
                        // Push next match day info to watch
                        wearableSender.sendFootballDataToWatch(result.match, isUpcoming = true)
                    }
                    is FootballResult.NoMatches -> {
                        currentFootballMatches = emptyList()
                        binding.layoutFootballMatches.removeAllViews()
                        binding.textNoMatches.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Football error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.footballProgressBar.visibility = View.GONE
                binding.btnFetchFootball.isEnabled = true
            }
        }
    }

    private fun displayFootballMatches(matches: List<FootballMatch>, isUpcoming: Boolean) {
        binding.layoutFootballMatches.removeAllViews()
        for (match in matches) {
            val itemBinding = ItemFootballMatchBinding.inflate(layoutInflater, binding.layoutFootballMatches, false)

            itemBinding.textMatchCompetition.text = match.competition.name.ifBlank { "Football" }

            val statusText = when (match.status) {
                "IN_PLAY" -> "LIVE"
                "PAUSED" -> "HT"
                "FINISHED" -> "FT"
                "SUSPENDED" -> "SUSP"
                "POSTPONED" -> "POSTP"
                "CANCELLED" -> "CANC"
                else -> if (isUpcoming) "UPCOMING" else "SCHEDULED"
            }
            itemBinding.textMatchStatus.text = statusText

            itemBinding.textHomeTeam.text = match.homeTeam.shortName.ifBlank { match.homeTeam.name }
            itemBinding.textAwayTeam.text = match.awayTeam.shortName.ifBlank { match.awayTeam.name }

            val homeScore = match.score.fullTime.home
            val awayScore = match.score.fullTime.away
            if (homeScore != null && awayScore != null) {
                itemBinding.textScore.text = "$homeScore - $awayScore"
            } else {
                itemBinding.textScore.text = "vs"
            }

            itemBinding.textMatchTime.text = formatMatchDate(match.utcDate)

            binding.layoutFootballMatches.addView(itemBinding.root)
        }
    }

    private fun formatMatchDate(utcDateStr: String): String {
        if (utcDateStr.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(utcDateStr) ?: return utcDateStr

            val todayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val matchDateFormat = todayFormat.format(date)
            val todayStr = todayFormat.format(Date())

            if (matchDateFormat == todayStr) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                "Today, ${timeFormat.format(date)}"
            } else {
                val fullFormat = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault())
                fullFormat.format(date)
            }
        } catch (e: Exception) {
            utcDateStr
        }
    }

    private fun pushFootballToWatch() {
        if (currentFootballMatches.isEmpty()) {
            Toast.makeText(this, "No football match data to send", Toast.LENGTH_SHORT).show()
            return
        }
        val match = currentFootballMatches.first()
        lifecycleScope.launch {
            wearableSender.sendFootballDataToWatch(match, currentIsUpcoming)
            Toast.makeText(this@MainActivity, getString(R.string.football_sent_to_watch), Toast.LENGTH_SHORT).show()
        }
    }
}
