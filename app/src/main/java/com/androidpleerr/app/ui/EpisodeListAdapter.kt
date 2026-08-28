package com.androidpleerr.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.androidpleerr.app.R
import com.androidpleerr.app.data.TorrentFileStat
import com.androidpleerr.app.databinding.ItemEpisodeBinding
import com.androidpleerr.app.util.Formatting

/** Shows every playable file inside a multi-file torrent (season pack, album, etc). */
class EpisodeListAdapter(
    private val items: List<TorrentFileStat>,
    private var selectedId: Int,
    private val onSelect: (TorrentFileStat) -> Unit
) : RecyclerView.Adapter<EpisodeListAdapter.VH>() {

    inner class VH(val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val fileName = item.path.substringAfterLast('/')
        holder.binding.episodeName.text = displayName(fileName)
        holder.binding.episodeSize.text = Formatting.formatBytes(item.length)

        val isCurrent = item.id == selectedId
        holder.binding.root.isSelected = isCurrent

        val context = holder.binding.root.context
        val nameColor = if (isCurrent) R.color.bg_dark else R.color.text_primary
        val sizeColor = if (isCurrent) R.color.bg_dark else R.color.text_secondary
        holder.binding.episodeName.setTextColor(ContextCompat.getColor(context, nameColor))
        holder.binding.episodeSize.setTextColor(ContextCompat.getColor(context, sizeColor))

        holder.binding.root.setOnClickListener {
            val previous = selectedId
            selectedId = item.id
            notifyItemChanged(items.indexOfFirst { it.id == previous })
            notifyItemChanged(position)
            onSelect(item)
        }
    }

    override fun getItemCount() = items.size

    companion object {
        private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v")
        private val SEASON_EPISODE = Regex("(?i)s(\\d{1,2})[.\\s_-]?e(\\d{1,3})")
        private val EPISODE_ONLY = Regex("(?i)(?:episode|epi|ep|серия|s)[.\\s_-]?(\\d{1,3})\\b")

        /** Video files only, sorted naturally (Episode 1, 2, 3...). */
        fun videoFiles(files: List<TorrentFileStat>): List<TorrentFileStat> =
            files.filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXT }
                .sortedBy { it.path }

        /**
         * Turns a raw torrent file name into a short, readable episode label when a
         * recognisable pattern is found (e.g. "S01E05" or "Episode 12"); otherwise
         * falls back to the original file name so nothing is ever hidden.
         */
        fun displayName(rawFileName: String): String {
            SEASON_EPISODE.find(rawFileName)?.let { m ->
                val season = m.groupValues[1].padStart(2, '0')
                val episode = m.groupValues[2].padStart(2, '0')
                return "S${season}E$episode"
            }
            EPISODE_ONLY.find(rawFileName)?.let { m ->
                return "Серия ${m.groupValues[1].toIntOrNull() ?: m.groupValues[1]}"
            }
            return rawFileName
        }
    }
}
