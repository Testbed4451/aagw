package com.meilluer.glanceables.debug

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meilluer.glanceables.R
import com.meilluer.glanceables.databinding.DialogDebugLogsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugLogsDialog : DialogFragment() {

    private var _binding: DialogDebugLogsBinding? = null
    private val binding get() = _binding!!

    private lateinit var logAdapter: LogAdapter
    private var allLogs: MutableList<LogEntry> = mutableListOf()
    private var filteredLogs: MutableList<LogEntry> = mutableListOf()

    private var selectedLevel: LogLevel? = null
    private var searchQuery: String = ""
    private var isLive: Boolean = true

    private val liveListener: (LogEntry) -> Unit = { newEntry ->
        allLogs.add(newEntry)
        if (isLive && matchesFilter(newEntry)) {
            filteredLogs.add(newEntry)
            val pos = filteredLogs.size - 1
            logAdapter.notifyItemInserted(pos)
            binding.recyclerLogs.scrollToPosition(pos)
            updateStatsText()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogDebugLogsBinding.inflate(layoutInflater)

        setupRecyclerView()
        setupListeners()
        loadInitialLogs()

        AppLogger.addListener(liveListener)

        val dialog = MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setView(binding.root)
            .create()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        return dialog
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(filteredLogs) { entry ->
            // On click, show detail dialog if log is long or has stack trace
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("[${entry.level.name}] ${entry.tag}")
                .setMessage(entry.toPlainText())
                .setPositiveButton("Copy") { _, _ ->
                    copyTextToClipboard("Log Entry", entry.toPlainText())
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.recyclerLogs.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = logAdapter
        }
    }

    private fun setupListeners() {
        updateLiveIndicator()

        binding.btnTogglePause.setOnClickListener {
            isLive = !isLive
            updateLiveIndicator()
            if (isLive) {
                applyFilters()
            }
        }

        binding.btnCopyLogs.setOnClickListener {
            val text = AppLogger.formatAllLogs(filteredLogs)
            copyTextToClipboard("Glanceables Logs", text)
            Toast.makeText(requireContext(), "Copied ${filteredLogs.size} logs to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clear()
            allLogs.clear()
            filteredLogs.clear()
            logAdapter.notifyDataSetChanged()
            updateStatsText()
            Toast.makeText(requireContext(), "Logs cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareLogs.setOnClickListener {
            val text = AppLogger.formatAllLogs(filteredLogs)
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Share App Logs"))
        }

        binding.btnCloseLogs.setOnClickListener {
            dismiss()
        }

        binding.btnLogcatDump.setOnClickListener {
            dumpSystemLogcat()
        }

        // Search text watcher
        binding.editSearchLogs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Level chips listener
        binding.chipGroupLogLevel.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedLevel = when {
                checkedIds.contains(R.id.chipDebug) -> LogLevel.DEBUG
                checkedIds.contains(R.id.chipInfo) -> LogLevel.INFO
                checkedIds.contains(R.id.chipWarn) -> LogLevel.WARN
                checkedIds.contains(R.id.chipError) -> LogLevel.ERROR
                else -> null
            }
            applyFilters()
        }
    }

    private fun updateLiveIndicator() {
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(if (isLive) Color.parseColor("#4CAF50") else Color.parseColor("#757575"))
        }
        binding.textLiveStatus.background = pillBg
        binding.textLiveStatus.text = if (isLive) "● LIVE" else "❚❚ PAUSED"
        binding.btnTogglePause.text = if (isLive) "Pause" else "Resume"
    }

    private fun loadInitialLogs() {
        allLogs = AppLogger.getAllLogs().toMutableList()
        applyFilters()
    }

    private fun matchesFilter(entry: LogEntry): Boolean {
        if (selectedLevel != null && entry.level != selectedLevel) {
            return false
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            val matchesTag = entry.tag.lowercase().contains(q)
            val matchesMsg = entry.message.lowercase().contains(q)
            if (!matchesTag && !matchesMsg) return false
        }
        return true
    }

    private fun applyFilters() {
        filteredLogs.clear()
        for (log in allLogs) {
            if (matchesFilter(log)) {
                filteredLogs.add(log)
            }
        }
        logAdapter.notifyDataSetChanged()
        if (filteredLogs.isNotEmpty()) {
            binding.recyclerLogs.scrollToPosition(filteredLogs.size - 1)
        }
        updateStatsText()
    }

    private fun updateStatsText() {
        val autoScrollStr = if (isLive) "Auto-scroll ON" else "Paused"
        binding.textLogStats.text = "Showing ${filteredLogs.size} / ${allLogs.size} logs • $autoScrollStr"
    }

    private fun dumpLogcat() {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Reading process logcat…", Toast.LENGTH_SHORT).show()
            val lines = withContext(Dispatchers.IO) {
                AppLogger.readProcessLogcat(200)
            }
            for (line in lines) {
                AppLogger.d("Logcat", line)
            }
        }
    }

    private fun dumpSystemLogcat() {
        dumpLogcat()
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        AppLogger.removeListener(liveListener)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DebugLogsDialog"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            val dialog = DebugLogsDialog()
            dialog.show(fragmentManager, TAG)
        }
    }
}
