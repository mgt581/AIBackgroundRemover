@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
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

    private var btnAuthAction: Button? = null
    private var btnHeaderSettings: Button? = null
    private var btnGallery: Button? = null
    private var tvAuthStatus: TextView? = null

    // Bridge for WebView if you are using one for the checkerboard UI
    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun openGallery() {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            // Initialize Views
            ivMainPreview = this.findViewById(R.id.iv_main_preview)
            val also =
                findViewById<ProgressBar>(/* id = */ R.id.pb_processing).also { pbProcessing = it }
            btnChoosePhoto = findViewById(R.id.btn_choose_photo)
            btnRemoveBg = findViewById(R.id.btn_remove_bg)
            llImageActions = findViewById<LinearLayout>(/* id = */ R.id.ll_image_actions)
            btnSaveFixed = findViewById(R.id.btn_save_fixed)
            btnDownloadDevice = findViewById<Button>(/* id = */ R.id.btn_download_device)

            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            btnGallery = findViewById(R.id.btn_gallery)
            tvAuthStatus = findViewById(R.id.tv_auth_status)

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

        btnGallery?.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnAuthAction?.setOnClickListener { handleAuthAction() }

        btnHeaderSettings?.setOnClickListener {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAuthAction() {
        if (auth.currentUser != null) {
            auth.signOut()
            updateHeaderUi()
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
        } else {
            // Trigger your login flow here
            Toast.makeText(this, "Opening Login...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            tvAuthStatus?.text = "Account: ${user.email}"
            btnAuthAction?.text = "Logout"
        } else {
            tvAuthStatus?.text = "Not Signed In"
            btnAuthAction?.text = "Login"
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
            btnRemoveBg.visibility = View.VISIBLE
            llImageActions.visibility = View.GONE
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

        val resultBitmap = createBitmap(original.width, original.height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val confidence = buffer.float
                if (confidence > 0.5) {
                    resultBitmap[x, y] = original[x, y]
                } else {
                    resultBitmap[x, y] = Color.TRANSPARENT
                }
            }
        }

        processedBitmap = resultBitmap
        ivMainPreview.setImageBitmap(processedBitmap)
        pbProcessing.visibility = View.GONE
        btnRemoveBg.visibility = View.GONE
        llImageActions.visibility = View.VISIBLE
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
                Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
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