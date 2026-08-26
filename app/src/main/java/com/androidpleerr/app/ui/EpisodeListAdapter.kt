package com.androidpleerr.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
        holder.binding.episodeName.text = fileName
        holder.binding.episodeSize.text = Formatting.formatBytes(item.length)
        holder.binding.root.isSelected = item.id == selectedId
        holder.binding.root.setOnClickListener {
            val previous = selectedId
            selectedId = item.id
            notifyItemChanged(items.indexOfFirst { it.id == previous })
            notifyItemChanged(position)
            onSelect(item)
        }
    }

    override fun getItemCount() = items.size

    /** Video files only, sorted naturally (Episode 1, 2, 3...). */
    companion object {
        private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v")
        fun videoFiles(files: List<TorrentFileStat>): List<TorrentFileStat> =
            files.filter { it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXT }
                .sortedBy { it.path }
    }
}
