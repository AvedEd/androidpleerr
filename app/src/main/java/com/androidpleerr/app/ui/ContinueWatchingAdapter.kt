package com.androidpleerr.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.androidpleerr.app.data.ContinueWatchingItem
import com.androidpleerr.app.databinding.ItemContinueWatchingBinding

class ContinueWatchingAdapter(
    private val items: MutableList<ContinueWatchingItem> = mutableListOf(),
    private val onClick: (ContinueWatchingItem) -> Unit,
    private val onDismiss: (ContinueWatchingItem) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.VH>() {

    inner class VH(val binding: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<ContinueWatchingItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.cwTitle.text = item.title
        val percent = if (item.durationMs > 0) (item.positionMs * 100 / item.durationMs).toInt() else 0
        holder.binding.cwProgress.progress = percent
        holder.binding.cwPercent.text = "$percent%"
        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.cwDismiss.setOnClickListener { onDismiss(item) }
    }

    override fun getItemCount() = items.size
}
