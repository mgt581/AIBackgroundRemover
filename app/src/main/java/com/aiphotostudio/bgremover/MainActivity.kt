@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
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
import androidx.core.net.toUri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import androidx.core.graphics.toColorInt
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
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            ivMainPreview = findViewById(R.id.iv_main_preview)
            pbProcessing = findViewById(R.id.pb_processing)
            btnChoosePhoto = findViewById(R.id.btn_choose_photo)
            btnRemoveBg = findViewById(R.id.btn_remove_bg)
            llImageActions = findViewById(R.id.ll_image_actions)
            btnSaveFixed = findViewById(R.id.btn_save_fixed)
            btnDownloadDevice = findViewById(R.id.btn_download_device)

            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            btnGallery = findViewById(R.id.btn_gallery)
            tvAuthStatus = findViewById(R.id.tv_auth_status)

            setupClickListeners()
            setupFooterClickListeners()
            updateHeaderUi()
            checkAndRequestPermissions()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Initialization error", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        btnChoosePhoto.setOnClickListener { showSourceDialog() }
        
        btnRemoveBg.setOnClickListener { removeBackground() }

        btnSaveFixed.setOnClickListener {
            processedBitmap?.let { saveToInternalGallery(it) } ?: run {
                Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownloadDevice.setOnClickListener {
            processedBitmap?.let { downloadToDevice(it) } ?: run {
                Toast.makeText(this, "No image to download", Toast.LENGTH_SHORT).show()
            }
        }

        btnAuthAction?.setOnClickListener {
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java))
        }

        btnHeaderSettings?.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnGallery?.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
    }

    private fun setupFooterClickListeners() {
        findViewById<ImageButton>(R.id.btn_whatsapp)?.setOnClickListener {
            openUrl(getString(R.string.whatsapp_url))
        }
        findViewById<ImageButton>(R.id.btn_tiktok)?.setOnClickListener {
            openUrl(getString(R.string.tiktok_url))
        }
        findViewById<ImageButton>(R.id.btn_facebook)?.setOnClickListener {
            openUrl(getString(R.string.facebook_url))
        }
        findViewById<ImageButton>(R.id.btn_share)?.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out AI Photo Studio!")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }

        findViewById<TextView>(R.id.footer_gallery)?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<TextView>(R.id.footer_contact)?.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:${getString(R.string.owner_email)}".toUri()
            }
            startActivity(Intent.createChooser(emailIntent, "Send Email"))
        }
        findViewById<TextView>(R.id.footer_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.footer_privacy)?.setOnClickListener {
            startActivity(Intent(this, WebPageActivity::class.java)
                .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.privacy_policy))
                .putExtra(WebPageActivity.EXTRA_URL, "https://aiphotostudio.co/privacy"))
        }
        findViewById<TextView>(R.id.footer_terms)?.setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening URL", e)
        }
    }

    private fun loadSelectedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                selectedBitmap = BitmapFactory.decodeStream(stream)
                ivMainPreview.setImageBitmap(selectedBitmap)
                btnRemoveBg.visibility = View.VISIBLE
                llImageActions.visibility = View.GONE
                processedBitmap = null
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeBackground() {
        val bitmap = selectedBitmap ?: return
        pbProcessing.visibility = View.VISIBLE
        btnRemoveBg.isEnabled = false

        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(image)
            .addOnSuccessListener { mask: SegmentationMask ->
                val maskBuffer = mask.buffer
                val maskWidth = mask.width
                val maskHeight = mask.height
                processedBitmap = createBitmapWithMask(bitmap, maskBuffer, maskWidth, maskHeight)
                ivMainPreview.setImageBitmap(processedBitmap)
                llImageActions.visibility = View.VISIBLE
                btnRemoveBg.visibility = View.GONE
                pbProcessing.visibility = View.GONE
                btnRemoveBg.isEnabled = true
                segmenter.close()
            }
            .addOnFailureListener { e: Exception ->
                pbProcessing.visibility = View.GONE
                btnRemoveBg.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                segmenter.close()
            }
    }

    private fun createBitmapWithMask(original: Bitmap, maskBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        val width = original.width
        val height = original.height
        val result = createBitmap(width, height)
        
        val pixels = IntArray(width * height)
        original.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskArray = FloatArray(maskWidth * maskHeight)
        maskBuffer.rewind()
        maskBuffer.asFloatBuffer().get(maskArray)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val maskX = (x * maskWidth / width)
                val maskY = (y * maskHeight / height)
                val confidence = maskArray[maskY * maskWidth + maskX]
                
                if (confidence < 0.5f) {
                    pixels[index] = Color.TRANSPARENT
                }
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun saveToInternalGallery(bitmap: Bitmap) {
        try {
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"
            val galleryDir = File(filesDir, "saved_images")
            if (!galleryDir.exists()) galleryDir.mkdirs()
            val file = File(galleryDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Saved to App Gallery", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadToDevice(bitmap: Bitmap) {
        try {
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Background Remover")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore insert failed")
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Background Remover")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }
            Toast.makeText(this, "Downloaded to Device", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_image_source))
            .setItems(options) { _, which -> if (which == 0) launchCamera() else launchGallery() }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            takePicture.launch(cameraImageUri!!)
        } else {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        btnAuthAction?.text = if (user != null) getString(R.string.sign_out) else getString(R.string.sign_in)
        if (user != null) {
            tvAuthStatus?.text = getString(R.string.signed_in_status)
            tvAuthStatus?.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            tvAuthStatus?.text = getString(R.string.sign_in_now)
            tvAuthStatus?.setTextColor(Color.parseColor("#FF4444"))
        }
    }

    private fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut().addOnCompleteListener {
            updateHeaderUi()
            Toast.makeText(this, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val toReq = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toReq.isNotEmpty()) requestPermissionsLauncher.launch(toReq.toTypedArray())
    }
}
