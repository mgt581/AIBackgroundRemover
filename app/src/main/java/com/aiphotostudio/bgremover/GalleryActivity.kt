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

/**
 * Activity to display and manage saved images.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: GalleryAdapter
    private lateinit var auth: FirebaseAuth

    /**
     * Initializes the activity, setting up UI components and loading images.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most recently supplied.
     */
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

    /**
     * Configures click listeners for the footer navigation buttons.
     */
    private fun setupFooter() {
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_sign_in).setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.already_signed_in),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        findViewById<View>(R.id.footer_btn_sign_up).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            launchBrowser(PRIVACY_URL)
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    /**
     * Launches a browser with the specified URL.
     * @param url The URL to open in the browser.
     */
    private fun launchBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.could_not_open_link),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Loads saved images from internal storage for the current user.
     */
    private fun loadImages() {
        val imageFiles = mutableListOf<File>()

        val userId = auth.currentUser?.uid ?: getString(R.string.guest)
        val userDir = File(filesDir, "$INTERNAL_DIR_PREFIX$userId")

        if (userDir.exists()) {
            val files = userDir.listFiles()
            files?.sortByDescending { it.lastModified() }
            files?.forEach { file: File ->
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
                onDeleteClick = { file: File -> deleteImage(file) },
                onDownloadClick = { file: File -> downloadImage(file) }
            )
            recyclerView.adapter = adapter
        }
    }

    /**
     * Deletes the specified image file from internal storage.
     * @param file The file to be deleted.
     */
    private fun deleteImage(file: File) {
        try {
            if (file.exists() && file.delete()) {
                loadImages()
                Toast.makeText(
                    this,
                    getString(R.string.image_deleted),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.could_not_delete_image),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image", e)
            Toast.makeText(
                this,
                getString(R.string.delete_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Downloads the specified image file to the device's public storage.
     * @param file The file to be downloaded.
     */
    private fun downloadImage(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(
                    this,
                    getString(R.string.file_not_found),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + ALBUM_NAME
                    )
                }

                val itemUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                itemUri?.let { uri: Uri ->
                    contentResolver.openOutputStream(uri).use { outputStream ->
                        FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.saved_to_device_storage),
                        Toast.LENGTH_SHORT
                    ).show()
                } ?: run {
                    Toast.makeText(
                        this,
                        getString(R.string.save_failed, getString(R.string.error_mediastore)),
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val studioDir = File(publicDir, ALBUM_NAME)
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

                Toast.makeText(
                    this,
                    getString(R.string.saved_to_device_storage),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image", e)
            Toast.makeText(
                this,
                getString(R.string.download_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val TAG = "GalleryActivity"
        private const val ALBUM_NAME = "AI Photo Studio"
        private const val INTERNAL_DIR_PREFIX = "saved_images/"
        private const val PRIVACY_URL = "https://mgt581.github.io/photo-static-main-3/privacy"
    }
}
