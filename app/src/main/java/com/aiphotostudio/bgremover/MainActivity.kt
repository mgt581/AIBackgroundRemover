package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.graphics.set
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var cameraImageUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null
    private var currentBackgroundUri: Uri? = null
    private var currentBackgroundColor: Int? = null

    // UI Elements
    private lateinit var ivMainPreview: ImageView
    private lateinit var pbProcessing: ProgressBar
    private lateinit var btnChoosePhoto: MaterialButton
    private lateinit var btnRemoveBg: MaterialButton
    private lateinit var btnSaveFixed: MaterialButton
    private lateinit var btnDownloadDevice: MaterialButton
    private lateinit var btnChangeBackground: MaterialButton
    private lateinit var btnChooseBackground: MaterialButton

    private lateinit var btnAuthAction: MaterialButton
    private lateinit var btnHeaderSettings: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnSignUp: MaterialButton
    private lateinit var tvAuthStatus: TextView
    private var fabSave: ExtendedFloatingActionButton? = null

    private lateinit var btnWhatsApp: TextView
    private lateinit var btnTikTok: TextView
    private lateinit var btnFacebook: TextView

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadSelectedImage(uri)
    }

    private val pickBackground = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            currentBackgroundUri = uri
            currentBackgroundColor = null
            applyImageBackground(uri)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraImageUri?.let { loadSelectedImage(it) }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] != true) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        initViews()
        setupClickListeners()
        updateHeaderUi()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
    }

    private fun initViews() {
        ivMainPreview = findViewById(R.id.iv_main_preview)
        pbProcessing = findViewById(R.id.pb_processing)
        btnChoosePhoto = findViewById(R.id.btn_choose_photo)
        btnRemoveBg = findViewById(R.id.btn_remove_bg)
        btnSaveFixed = findViewById(R.id.btn_save_fixed)
        btnDownloadDevice = findViewById(R.id.btn_download_device)
        btnChangeBackground = findViewById(R.id.btn_change_background)
        btnChooseBackground = findViewById(R.id.btn_choose_background)

        btnAuthAction = findViewById(R.id.btn_auth_action)
        btnHeaderSettings = findViewById(R.id.btn_header_settings)
        btnGallery = findViewById(R.id.btn_gallery)
        btnSignUp = findViewById(R.id.btn_sign_up)
        tvAuthStatus = findViewById(R.id.tv_auth_status)
        fabSave = findViewById(R.id.fab_save)

        btnWhatsApp = findViewById(R.id.btn_whatsapp)
        btnTikTok = findViewById(R.id.btn_tiktok)
        btnFacebook = findViewById(R.id.btn_facebook)

        applyCheckerboardBackground(findViewById(R.id.iv_main_preview_container))
    }

    private fun applyCheckerboardBackground(view: View) {
        val size = 20
        val bitmap = createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        bitmap.applyCanvas {
            val paint = Paint()
            paint.color = Color.WHITE
            drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            drawRect(size.toFloat(), size.toFloat(), (size * 2).toFloat(), (size * 2).toFloat(), paint)
            paint.color = "#E0E0E0".toColorInt()
            drawRect(size.toFloat(), 0f, (size * 2).toFloat(), size.toFloat(), paint)
            drawRect(0f, size.toFloat(), size.toFloat(), (size * 2).toFloat(), paint)
        }

        val drawable = bitmap.toDrawable(resources)
        drawable.tileModeX = Shader.TileMode.REPEAT
        drawable.tileModeY = Shader.TileMode.REPEAT
        view.background = drawable
    }

    private fun setupClickListeners() {
        btnChoosePhoto.setOnClickListener { showImagePickerOptions() }
        btnRemoveBg.setOnClickListener { processImage() }
        btnDownloadDevice.setOnClickListener { getFinalBitmap()?.let { saveImageToGallery(it) } }
        btnSaveFixed.setOnClickListener { getFinalBitmap()?.let { saveToInternalGallery(it) } }
        fabSave?.setOnClickListener { getFinalBitmap()?.let { saveImageToGallery(it) } }

        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnAuthAction.setOnClickListener { handleAuthAction() }
        btnSignUp.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        btnHeaderSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnChangeBackground.setOnClickListener { showBackgroundOptions() }
        btnChooseBackground.setOnClickListener {
            pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnWhatsApp.setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        btnTikTok.setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        btnFacebook.setOnClickListener { openUrl(getString(R.string.facebook_url)) }

        findViewById<View>(R.id.footer_privacy).setOnClickListener {
            openUrl("https://aiphotostudio.co/privacy")
        }
        findViewById<View>(R.id.footer_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun showBackgroundOptions() {
        val options = arrayOf("Solid White", "Solid Black", "Solid Blue", "Solid Red", "Solid Green", "Checkerboard")
        AlertDialog.Builder(this)
            .setTitle("Change Background")
            .setItems(options) { _, which ->
                currentBackgroundUri = null
                when (which) {
                    0 -> updateBackgroundColor(Color.WHITE)
                    1 -> updateBackgroundColor(Color.BLACK)
                    2 -> updateBackgroundColor(Color.BLUE)
                    3 -> updateBackgroundColor(Color.RED)
                    4 -> updateBackgroundColor(Color.GREEN)
                    5 -> {
                        currentBackgroundColor = null
                        applyCheckerboardBackground(findViewById(R.id.iv_main_preview_container))
                    }
                }
            }
            .show()
    }

    private fun updateBackgroundColor(color: Int) {
        currentBackgroundColor = color
        findViewById<View>(R.id.iv_main_preview_container).setBackgroundColor(color)
    }

    private fun applyImageBackground(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                val bgBitmap = BitmapFactory.decodeStream(inputStream) ?: return
                ivMainPreview.post {
                    findViewById<View>(R.id.iv_main_preview_container).background = bgBitmap.toDrawable(resources)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error applying image background", e)
        }
    }

    private fun getFinalBitmap(): Bitmap? {
        val subject = processedBitmap ?: return null

        val finalBitmap = createBitmap(subject.width, subject.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Draw background
        currentBackgroundColor?.let { canvas.drawColor(it) }
        currentBackgroundUri?.let { uri ->
            try {
                contentResolver.openInputStream(uri).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bg ->
                        val scaledBg = bg.scale(subject.width, subject.height, true)
                        canvas.drawBitmap(scaledBg, 0f, 0f, null)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error decoding background image", e)
            }
        }

        // Draw subject
        canvas.drawBitmap(subject, 0f, 0f, null)
        return finalBitmap
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
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        takePicture.launch(cameraImageUri!!)
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                selectedBitmap = BitmapFactory.decodeStream(inputStream)
                ivMainPreview.setImageBitmap(selectedBitmap)
                applyCheckerboardBackground(findViewById(R.id.iv_main_preview_container))

                btnRemoveBg.visibility = View.VISIBLE
                btnChangeBackground.visibility = View.GONE
                btnChooseBackground.visibility = View.GONE
                btnSaveFixed.visibility = View.GONE
                btnDownloadDevice.visibility = View.GONE
                fabSave?.visibility = View.GONE
                processedBitmap = null
                currentBackgroundUri = null
                currentBackgroundColor = null
            }
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
        val maskWidth = mask.width
        val maskHeight = mask.height
        val buffer = mask.buffer
        
        // Reset buffer position and set byte order for proper reading
        buffer.rewind()
        buffer.order(ByteOrder.nativeOrder())

        // Scale original to mask dimensions for processing
        val scaledOriginal = original.scale(maskWidth, maskHeight)
        val resultBitmap = createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val confidence = buffer.float
                if (confidence > 0.5) {
                    resultBitmap[x, y] = scaledOriginal[x, y]
                } else {
                    resultBitmap[x, y] = Color.TRANSPARENT
                }
            }
        }

        // Scale result back to original size for high quality
        processedBitmap = resultBitmap.scale(original.width, original.height, true)
        ivMainPreview.setImageBitmap(processedBitmap)

        pbProcessing.visibility = View.GONE
        btnRemoveBg.visibility = View.GONE
        btnChangeBackground.visibility = View.VISIBLE
        btnChooseBackground.visibility = View.VISIBLE
        btnSaveFixed.visibility = View.VISIBLE
        btnDownloadDevice.visibility = View.VISIBLE
        fabSave?.visibility = View.VISIBLE
        btnRemoveBg.isEnabled = true
    }

    private fun saveToInternalGallery(bitmap: Bitmap) {
        val directory = File(filesDir, "saved_images")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "BG_Remover_${System.currentTimeMillis()}.png")
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
        try {
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                @Suppress("DEPRECATION")
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                FileOutputStream(File(imagesDir, filename))
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

    private fun handleAuthAction() {
        if (auth.currentUser != null) {
            auth.signOut()
            updateHeaderUi()
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            tvAuthStatus.text = "Account: ${user.email ?: "Guest"}"
            btnAuthAction.text = "Logout"
            btnSignUp.visibility = View.GONE
        } else {
            tvAuthStatus.text = getString(R.string.not_signed_in)
            btnAuthAction.text = getString(R.string.sign_in)
            btnSignUp.visibility = View.VISIBLE
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }
}
