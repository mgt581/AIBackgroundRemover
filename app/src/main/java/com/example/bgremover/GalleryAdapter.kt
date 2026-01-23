package com.example.bgremover

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class GalleryAdapter(
    private val images: MutableList<File>,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.iv_image)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        val btnDownload: ImageButton = view.findViewById(R.id.btn_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = images[position]
        
        Glide.with(holder.ivImage.context)
            .load(file)
            .centerCrop()
            .into(holder.ivImage)

        holder.btnDelete.setOnClickListener {
            onDeleteClick(file)
        }

        // For "download" in gallery, we could implement a share or open intent
        holder.btnDownload.visibility = View.GONE 
    }

    override fun getItemCount() = images.size
}
