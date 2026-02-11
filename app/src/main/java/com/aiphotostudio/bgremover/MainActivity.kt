@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.set
import androidx.core.graphics.get
import androidx.core.graphics.createBitmap
import com.aiphotostudio.bgremover.R.id.btn_save_fixed
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var cameraImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null

    private lateinit var ivMainPreview: ImageView
    private lateinit var pbProcessing: ProgressBar
    private lateinit var btnChoosePhoto: Button
    private lateinit var btnRemoveBg: Button
    private lateinit var llImageActions: LinearLayout
    private lateinit var btnSaveFixed: Button
    private lateinit var btnDownloadDevice: Button
    private lateinit var btnChangeBackground: Button

    private var btnAuthAction: TextView? = null
    private var btnHeaderSettings: Button? = null
    private var btnGallery: Button? = null
    private var btnSignUp: TextView? = null
    private var tvAuthStatus: TextView? = null
    private var fabSave: ExtendedFloatingActionButton? = null
    
    private var btnWhatsApp: TextView? = null
    private var btnTikTok: TextView? = null
    private var btnFacebook: TextView? = null

    // Bridge for WebView if you are using one for the checkerboard UI
    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun openGallery() {
            startActivity(Intent(mContext, GalleryActivity::class.java))
        }

        @JavascriptInterface
        fun openAccount() {
            handleAuthAction()
        }

        @JavascriptInterface
        fun downloadImage() {
            processedBitmap?.let { saveImageToGallery(it) } ?: run {
                runOnUiThread { Toast.makeText(mContext, "No image to save", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            loadSelectedImage(uri)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { loadSelectedImage(it) }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] != true) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            // Initialize Views
            ivMainPreview = this.findViewById(R.id.iv_main_preview)
            pbProcessing = findViewById(R.id.pb_processing)
            btnChoosePhoto = findViewById(R.id.btn_choose_photo)
            btnRemoveBg = findViewById(R.id.btn_remove_bg)
            // llImageActions is no longer used in the simplified layout but kept for safety
            llImageActions = LinearLayout(this) 
            btnSaveFixed = findViewById(btn_save_fixed)
            btnDownloadDevice = findViewById(R.id.btn_download_device)
            btnChangeBackground = findViewById(R.id.btn_change_background)

            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            btnGallery = findViewById(R.id.btn_gallery)
            btnSignUp = findViewById(R.id.btn_sign_up)
            tvAuthStatus = findViewById(R.id.tv_auth_status)
            fabSave = findViewById(R.id.fab_save)
            btnWhatsApp = findViewById(R.id.btn_whatsapp)
            btnTikTok = findViewById(R.id.btn_tiktok)
            btnFacebook = findViewById(R.id.btn_facebook)

            setupClickListeners()
            updateHeaderUi()
            checkAndRequestPermissions()

        } catch (exception: Exception) {
            Log.e("MainActivity", "Error in onCreate", exception)
            Toast.makeText(this, "Initialization error", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        btnChoosePhoto.setOnClickListener { showImagePickerOptions() }
        btnRemoveBg.setOnClickListener { processImage() }
        btnDownloadDevice.setOnClickListener { processedBitmap?.let { saveImageToGallery(it) } }
        btnSaveFixed.setOnClickListener { processedBitmap?.let { saveToInternalGallery(it) } }
        fabSave?.setOnClickListener { processedBitmap?.let { saveImageToGallery(it) } }

        btnGallery?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        btnAuthAction?.setOnClickListener { handleAuthAction() }
        btnSignUp?.setOnClickListener { 
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnHeaderSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        btnChangeBackground.setOnClickListener {
            Toast.makeText(this, "Change Background coming soon", Toast.LENGTH_SHORT).show()
        }
        
        btnWhatsApp?.setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        btnTikTok?.setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        btnFacebook?.setOnClickListener { openUrl(getString(R.string.facebook_url)) }
        
        findViewById<View>(R.id.footer_gallery).setOnClickListener { 
            startActivity(Intent(this, GalleryActivity::class.java)) 
        }
        findViewById<View>(R.id.footer_settings).setOnClickListener { 
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }
        findViewById<View>(R.id.footer_privacy).setOnClickListener {
            openUrl("https://aiphotostudio.co/privacy")
        }
        findViewById<View>(R.id.footer_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAuthAction() {
        if (auth.currentUser != null) {
            auth.signOut()
            updateHeaderUi()
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            tvAuthStatus?.text = "Account: ${user.email ?: "Guest"}"
            btnAuthAction?.text = "Logout"
            btnSignUp?.visibility = View.GONE
        } else {
            tvAuthStatus?.text = "Not Signed In"
            btnAuthAction?.text = "Login"
            btnSignUp?.visibility = View.VISIBLE
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Gallery", "Camera")
        AlertDialog.Builder(this)
            .setTitle("Select Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> openCamera()
                }
            }
            .show()
    }

    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_image.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.file-provider", photoFile)
        takePicture.launch(cameraImageUri!!)
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            selectedBitmap = BitmapFactory.decodeStream(inputStream)
            ivMainPreview.setImageBitmap(selectedBitmap)
            ivMainPreview.background = null // Remove checkerboard for original
            btnRemoveBg.visibility = View.VISIBLE
            btnSaveFixed.visibility = View.GONE
            fabSave?.visibility = View.GONE
            btnChangeBackground.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading image", e)
        }
    }

    private fun processImage() {
        val bitmap = selectedBitmap ?: return
        pbProcessing.visibility = View.VISIBLE
        btnRemoveBg.isEnabled = false

        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { mask ->
                renderResult(mask, bitmap)
            }
            .addOnFailureListener { e ->
                pbProcessing.visibility = View.GONE
                btnRemoveBg.isEnabled = true
                Toast.makeText(this, "Processing failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderResult(mask: SegmentationMask, original: Bitmap) {
        val width = mask.width
        val height = mask.height
        val buffer = mask.buffer

        val resultBitmap = createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val confidence = buffer.float
                // Using a slightly higher threshold or different processing for better results
                if (confidence > 0.4) {
                    resultBitmap[x, y] = original[x, y]
                } else {
                    resultBitmap[x, y] = Color.TRANSPARENT
                }
            }
        }

        processedBitmap = resultBitmap
        ivMainPreview.setImageBitmap(processedBitmap)
        ivMainPreview.setBackgroundResource(R.drawable.checkerboard_background)
        
        pbProcessing.visibility = View.GONE
        btnRemoveBg.visibility = View.GONE
        btnSaveFixed.visibility = View.VISIBLE
        btnChangeBackground.visibility = View.VISIBLE
        fabSave?.visibility = View.VISIBLE
        btnRemoveBg.isEnabled = true
    }

    private fun saveToInternalGallery(bitmap: Bitmap) {
        val directory = File(filesDir, "saved_images")
        if (!directory.exists()) directory.mkdirs()
        
        val filename = "BG_Remover_${System.currentTimeMillis()}.png"
        val file = File(directory, filename)
        
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Toast.makeText(this, "Saved to App Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving internally", e)
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "BG_Remover_${System.currentTimeMillis()}.png"
        val outputStream: FileOutputStream?

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                outputStream = imageUri?.let { contentResolver.openOutputStream(it) } as FileOutputStream?
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, filename)
                outputStream = FileOutputStream(image)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "Saved to Device Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving to device", e)
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }
}