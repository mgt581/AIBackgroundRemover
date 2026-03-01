package com.aiphotostudio.bgremover

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapter class for displaying a gallery of images in a RecyclerView.
 */
class GalleryAdapter(
    private val galleryItems: List<GalleryItem>,
    private val onDeleteClick: (GalleryItem) -> Unit,
    private val onDownloadClick: (GalleryItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.iv_image)
        val deleteBtn: Button = itemView.findViewById(R.id.btn_delete)
        val downloadBtn: Button = itemView.findViewById(R.id.btn_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
        return ImageViewHolder(view)
    }

    override fun getItemCount(): Int = galleryItems.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = galleryItems[position]
        val context: Context = holder.itemView.context

        Glide.with(context)
            .load(item.url)
            .placeholder(android.R.drawable.progress_indeterminate_horizontal)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.imageView)

        holder.deleteBtn.setOnClickListener { onDeleteClick(item) }
        holder.downloadBtn.setOnClickListener { onDownloadClick(item) }
    }
}
