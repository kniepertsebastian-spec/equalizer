package com.soundcloud.equalizer.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.soundcloud.equalizer.player.R
import com.soundcloud.equalizer.player.model.TrackItem

class TrackAdapter(
    private val onItemClick: (TrackItem) -> Unit
) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

    private val items = mutableListOf<TrackItem>()

    fun submitList(newList: List<TrackItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val btnPlay: Button = itemView.findViewById(R.id.btnPlay)

        fun bind(item: TrackItem) {
            tvTitle.text = item.title
            tvSubtitle.text = item.artist
            btnPlay.setOnClickListener { onItemClick(item) }
        }
    }
}
