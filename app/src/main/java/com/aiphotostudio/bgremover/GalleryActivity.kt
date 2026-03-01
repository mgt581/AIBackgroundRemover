package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.net.URL
import java.util.concurrent.Executors

/**
 * Activity to display and manage saved images from Firebase.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val galleryItems = mutableListOf<GalleryItem>()
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        findViewById<Button>(R.id.btn_back_to_studio).setOnClickListener {
            // Explicitly navigate to the .co homepage to ensure the correct environment
            launchBrowser("https://aiphotostudio.co/index.html")
            finish()
        }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = GalleryAdapter(
            galleryItems = galleryItems,
            onDeleteClick = { item -> deleteImage(item) },
            onDownloadClick = { item -> downloadImage(item) }
        )
        recyclerView.adapter = adapter

        setupFooter()
        loadImagesFromFirestore()
    }

    private fun setupFooter() {
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.footer_btn_sign_in).setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Toast.makeText(this, getString(R.string.already_signed_in), Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            launchBrowser("https://aiphotostudio.co/privacy.html")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun launchBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImagesFromFirestore() {
        val user = auth.currentUser
        if (user == null) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = getString(R.string.please_sign_in_gallery)
            progressBar.visibility = View.GONE
            return
        }

        progressBar.visibility = View.VISIBLE
        db.collection("users")
            .document(user.uid)
            .collection("gallery")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                galleryItems.clear()
                for (document in documents) {
                    val url = document.getString("url") ?: ""
                    val title = document.getString("title") ?: "Untitled"
                    val createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    galleryItems.add(GalleryItem(document.id, url, title, createdAt))
                }

                if (galleryItems.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading gallery", e)
                Toast.makeText(this, "Failed to load gallery", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteImage(item: GalleryItem) {
        val user = auth.currentUser ?: return
        
        // 1. Delete from Firestore
        db.collection("users")
            .document(user.uid)
            .collection("gallery")
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                // 2. Delete from Storage
                try {
                    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(item.url)
                    storageRef.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting from storage", e)
                }
                
                galleryItems.remove(item)
                adapter.notifyDataSetChanged()
                if (galleryItems.isEmpty()) tvEmpty.visibility = View.VISIBLE
                Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun downloadImage(item: GalleryItem) {
        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()
        
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val url = URL(item.url)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val bytes = inputStream.readBytes()
                
                runOnUiThread {
                    saveToMediaStore(bytes, "AI_${System.currentTimeMillis()}.png")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveToMediaStore(bytes: ByteArray, name: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIPhotoStudio")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { itemUri ->
            contentResolver.openOutputStream(itemUri)?.use { it.write(bytes) }
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "GalleryActivity"
    }
}
