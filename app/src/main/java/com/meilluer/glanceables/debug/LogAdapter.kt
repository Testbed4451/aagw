package com.meilluer.glanceables.debug

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.meilluer.glanceables.databinding.ItemDebugLogBinding

class LogAdapter(
    private var items: List<LogEntry> = emptyList(),
    private val onItemClick: ((LogEntry) -> Unit)? = null
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    fun updateList(newItems: List<LogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemDebugLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class LogViewHolder(private val binding: ItemDebugLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            binding.textLogLevel.text = entry.level.letter

            val badgeColor = when (entry.level) {
                LogLevel.VERBOSE -> Color.parseColor("#9E9E9E")
                LogLevel.DEBUG -> Color.parseColor("#2196F3")
                LogLevel.INFO -> Color.parseColor("#4CAF50")
                LogLevel.WARN -> Color.parseColor("#FF9800")
                LogLevel.ERROR -> Color.parseColor("#F44336")
            }

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f
                setColor(badgeColor)
            }
            binding.textLogLevel.background = bg

            val err = entry.throwable?.let { "\n" + android.util.Log.getStackTraceString(it) } ?: ""
            binding.textLogContent.text = "${entry.formattedTime()} [${entry.tag}] ${entry.message}$err"

            val textColor = when (entry.level) {
                LogLevel.ERROR -> Color.parseColor("#D32F2F")
                LogLevel.WARN -> Color.parseColor("#E65100")
                else -> Color.parseColor("#212121")
            }
            binding.textLogContent.setTextColor(textColor)

            binding.root.setOnClickListener {
                onItemClick?.invoke(entry)
            }
        }
    }
}
