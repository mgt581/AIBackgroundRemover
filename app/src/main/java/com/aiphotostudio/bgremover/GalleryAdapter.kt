package com.aiphotostudio.bgremover

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

/**
 * Adapter class for displaying a gallery of images in a RecyclerView.
 *
 * @property imageFiles The list of image files to be displayed.
 * @property onDeleteClick Callback function triggered when the delete button is clicked.
 * @property onDownloadClick Callback function triggered when the download button is clicked.
 */
class GalleryAdapter(
    private val imageFiles: List<File>,
    private val onDeleteClick: (File) -> Unit,
    private val onDownloadClick: (File) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ImageViewHolder>() {

    /**
     * ViewHolder class for holding the views of a single gallery item.
     *
     * @param itemView The view of the gallery item.
     */
    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        /** The ImageView for displaying the image. */
        val imageView: ImageView = itemView.findViewById(R.id.iv_image)
        /** The button for deleting the image. */
        val deleteBtn: ImageButton = itemView.findViewById(R.id.btn_delete)
        /** The button for downloading the image. */
        val downloadBtn: ImageButton = itemView.findViewById(R.id.btn_download)
    }

    /**
     * Called when the RecyclerView needs a new ViewHolder of the given type to represent an item.
     *
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ImageViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
        return ImageViewHolder(view)
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     * @return The total number of items in the imageFiles list.
     */
    override fun getItemCount(): Int = imageFiles.size

    /**
     * Called by RecyclerView to display the data at the specified position.
     *
     * @param holder The ImageViewHolder, which should be updated to represent the contents of the item at the given position.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val file = imageFiles[position]
        val context: Context = holder.itemView.context

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.file provider",
            file
        )

        Glide.with(context)
            .load(uri)
            .into(holder.imageView)

        holder.deleteBtn.setOnClickListener { onDeleteClick(file) }
        holder.downloadBtn.setOnClickListener { onDownloadClick(file) }
    }
}
