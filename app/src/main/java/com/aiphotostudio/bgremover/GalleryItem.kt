package com.aiphotostudio.bgremover

/**
 * Data class representing an item in the gallery.
 */
data class GalleryItem(
    val id: String,
    val url: String,
    val title: String,
    val createdAt: Long
)
