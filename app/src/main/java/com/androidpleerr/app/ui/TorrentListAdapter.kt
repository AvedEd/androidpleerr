package com.androidpleerr.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.androidpleerr.app.data.TorrentInfo
import com.androidpleerr.app.databinding.ItemTorrentBinding
import com.androidpleerr.app.util.Formatting

class TorrentListAdapter(
    private val items: MutableList<TorrentInfo> = mutableListOf(),
    private val onClick: (TorrentInfo) -> Unit,
    private val onRemove: (TorrentInfo) -> Unit
) : RecyclerView.Adapter<TorrentListAdapter.VH>() {

    inner class VH(val binding: ItemTorrentBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<TorrentInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTorrentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.title.text = item.title ?: item.name ?: item.hash ?: "?"
        val speed = Formatting.formatSpeed(item.stat?.downloadSpeed)
        val peers = item.stat?.peers ?: 0
        holder.binding.subtitle.text = "$speed · $peers peers"
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.removeButton.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount() = items.size
}
