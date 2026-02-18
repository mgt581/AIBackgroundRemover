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
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: GalleryAdapter
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        auth = FirebaseAuth.getInstance()

        findViewById<Button>(R.id.btn_back_to_studio).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        loadImages()
        setupFooter()
    }

    private fun setupFooter() {
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_sign_in).setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Toast.makeText(this, "Already signed in", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.footer_btn_sign_up).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            openUrl("https://ai-photo-studio-24354.web.app/privacy")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImages() {
        val imageFiles = mutableListOf<File>()

        val userId = auth.currentUser?.uid ?: "guest"
        val userDir = File(filesDir, "saved_images/$userId")

        if (userDir.exists()) {
            val files = userDir.listFiles()
            files?.sortByDescending { it.lastModified() }
            files?.forEach { file ->
                if (file.isFile) imageFiles.add(file)
            }
        }

        if (imageFiles.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            adapter = GalleryAdapter(
                imageFiles = imageFiles,
                onDeleteClick = { file -> deleteImage(file) },
                onDownloadClick = { file -> downloadImage(file) }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun deleteImage(file: File) {
        try {
            if (file.exists() && file.delete()) {
                loadImages()
                Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not delete image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error deleting image", e)
            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadImage(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AI Photo Studio"
                    )
                }

                val itemUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                itemUri?.let {
                    contentResolver.openOutputStream(it).use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(this, "Saved to device storage", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
                }

            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val studioDir = File(publicDir, "AI Photo Studio")
                if (!studioDir.exists()) studioDir.mkdirs()

                val destFile = File(studioDir, fileName)
                FileInputStream(file).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                @Suppress("DEPRECATION")
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(destFile)
                sendBroadcast(mediaScanIntent)

                Toast.makeText(this, "Saved to device storage", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("GalleryActivity", "Error downloading image", e)
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}