package com.tharunbirla.librecuts

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FrameAdapter(private val frameNames: List<String>) : RecyclerView.Adapter<FrameAdapter.FrameViewHolder>() {

    class FrameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.fileNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_name, parent, false)
        Log.d("FrameAdapter", "ViewHolder created for item type: $viewType")
        return FrameViewHolder(view)
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.textView.text = frameNames[position]
        Log.d("FrameAdapter", "Binding data to ViewHolder at position: $position with name: ${frameNames[position]}")

        holder.textView.setBackgroundColor(holder.itemView.context.getColor(R.color.colorOnPrimary))
    }

    override fun getItemCount(): Int {
        val count = frameNames.size
        Log.d("FrameAdapter", "Total items in adapter: $count")
        return count
    }
}