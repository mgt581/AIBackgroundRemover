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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        loadImages()
    }

    private fun loadImages() {
        val imageList = mutableListOf<Uri>()
        
        val directory = File(filesDir, "saved_images")
        if (directory.exists()) {
            val files = directory.listFiles()
            files?.sortByDescending { it.lastModified() }
            files?.forEach { file ->
                imageList.add(Uri.fromFile(file))
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
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    loadImages()
                    Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error deleting image", e)
        }
    }

    private fun downloadImage(uri: Uri) {
        try {
            val path = uri.path ?: return
            val file = File(path)
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIPhotoStudio")
                }

                val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val itemUri = contentResolver.insert(contentUri, values)

                itemUri?.let {
                    contentResolver.openOutputStream(it).use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val studioDir = File(publicDir, "AIPhotoStudio")
                if (!studioDir.exists()) studioDir.mkdirs()

                val destFile = File(studioDir, fileName)
                FileInputStream(file).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Refresh MediaStore
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
                Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error downloading image", e)
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
