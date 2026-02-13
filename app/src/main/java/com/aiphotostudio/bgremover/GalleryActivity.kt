package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        findViewById<Button>(R.id.btn_back_to_studio).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        loadImages()
    }

    private fun loadImages() {
        val imageList = mutableListOf<Uri>()
        
        val directory = File(filesDir, "saved_images")
        if (directory.exists()) {
            val files = directory.listFiles()
            files?.sortByDescending { it.lastModified() }
            files?.forEach { file ->
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                imageList.add(uri)
            }
        }

        if (imageList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = GalleryAdapter(
                imageList,
                onDeleteClick = { uri -> deleteImage(uri) },
                onDownloadClick = { uri -> downloadImage(uri) }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun deleteImage(uri: Uri) {
        try {
            // Since we're using FileProvider URIs, we need to handle deletion carefully
            // If the URI is a file URI or content URI from our provider
            val directory = File(filesDir, "saved_images")
            val files = directory.listFiles()
            
            // Extract filename from URI if possible, or match by other means
            // For simplicity in this internal app, we might need a better way if URIs are complex
            // But usually, we can find the file in our private directory
            
            val fileName = uri.lastPathSegment
            if (fileName != null) {
                val file = File(directory, fileName)
                if (file.exists()) {
                    if (file.delete()) {
                        loadImages()
                        Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error deleting image", e)
        }
    }

    private fun downloadImage(uri: Uri) {
        try {
            val directory = File(filesDir, "saved_images")
            val fileNameFromUri = uri.lastPathSegment ?: return
            val file = File(directory, fileNameFromUri)
            if (!file.exists()) return

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Background Remover")
                }

                val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val itemUri = contentResolver.insert(contentUri, values)

                itemUri?.let {
                    contentResolver.openOutputStream(it).use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val studioDir = File(publicDir, "AI Background Remover")
                if (!studioDir.exists()) studioDir.mkdirs()

                val destFile = File(studioDir, fileName)
                FileInputStream(file).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Refresh MediaStore
                @Suppress("DEPRECATION")
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(destFile)
                sendBroadcast(mediaScanIntent)
                Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error downloading image", e)
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
