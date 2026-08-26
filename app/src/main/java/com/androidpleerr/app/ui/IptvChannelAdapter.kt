package com.androidpleerr.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.androidpleerr.app.data.IptvChannel
import com.androidpleerr.app.databinding.ItemChannelBinding

class IptvChannelAdapter(
    private val items: MutableList<IptvChannel> = mutableListOf(),
    private val onClick: (IptvChannel) -> Unit,
    private val onToggleFavorite: (IptvChannel) -> Unit,
    private val isFavorite: (IptvChannel) -> Boolean
) : RecyclerView.Adapter<IptvChannelAdapter.VH>() {

    inner class VH(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<IptvChannel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.channelName.text = item.name
        holder.binding.channelGroup.text = item.group ?: ""
        holder.binding.root.setOnClickListener { onClick(item) }

        val favorite = isFavorite(item)
        holder.binding.favoriteButton.setImageResource(
            if (favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
        holder.binding.favoriteButton.setOnClickListener {
            onToggleFavorite(item)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = items.size
}
