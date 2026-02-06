package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private lateinit var auth: FirebaseAuth

    private lateinit var btnHeaderGallery: Button
    private lateinit var btnHeaderSettings: Button
    private lateinit var btnAuthSignin: Button
    private lateinit var btnAuthSignup: Button
    private lateinit var tvSignedInStatus: TextView
    private lateinit var ivPreview: ImageView

    private var viewsInitialized = false

    // Photo Picker Logic
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ivPreview.setImageURI(uri)
            filePathCallback?.onReceiveValue(arrayOf(uri))
            webView.evaluateJavascript("javascript:if(window.handleAppPhotoUpload) window.handleAppPhotoUpload();", null)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            ivPreview.setImageURI(cameraImageUri)
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
            webView.evaluateJavascript("javascript:if(window.handleAppPhotoUpload) window.handleAppPhotoUpload();", null)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_main)

        initViews()
        setupWebView()
        updateHeaderUi()
        checkAndRequestPermissions()

        if (savedInstanceState == null) {
            webView.loadUrl("https://aiphotostudio.co.uk/")
            handleIntent(intent)
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        btnHeaderGallery = findViewById(R.id.btn_header_gallery)
        btnHeaderSettings = findViewById(R.id.btn_header_settings)
        btnAuthSignin = findViewById(R.id.btn_auth_signin)
        btnAuthSignup = findViewById(R.id.btn_auth_signup)
        tvSignedInStatus = findViewById(R.id.tv_signed_in_status)
        ivPreview = findViewById(R.id.iv_preview)

        // --- STUDIO BUTTONS ---
        findViewById<Button>(R.id.btn_choose_photo).setOnClickListener {
            showSourceDialog()
        }

        findViewById<Button>(R.id.btn_remove_bg).setOnClickListener {
            webView.evaluateJavascript("(function(){ var b=Array.from(document.querySelectorAll('button')).find(x=>x.innerText.includes('Remove Background')); if(b) b.click(); })();", null)
            Toast.makeText(this, "Removing background...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_change_bg).setOnClickListener {
            webView.evaluateJavascript("javascript:document.querySelector('button[aria-label=\"Change Background\"]')?.click();", null)
        }

        findViewById<Button>(R.id.btn_choose_background).setOnClickListener {
            webView.evaluateJavascript("(function(){ var i=document.querySelector('input[type=\"file\"]'); if(i) i.click(); })();", null)
        }

        findViewById<View>(R.id.btn_remove_person).setOnClickListener {
            webView.evaluateJavascript("javascript:if(window.removePerson) window.removePerson();", null)
        }

        findViewById<Button>(R.id.btn_save_to_gallery).setOnClickListener {
            webView.evaluateJavascript("""
                (function() {
                    var canvas = document.querySelector('canvas') || document.querySelector('img[src^="blob:"]');
                    if(canvas) {
                        var dataUrl = canvas.tagName === 'CANVAS' ? canvas.toDataURL("image/png") : canvas.src;
                        AndroidInterface.processBlob(dataUrl);
                    }
                })();
            """.trimIndent(), null)
            Toast.makeText(this, "Processing save...", Toast.LENGTH_SHORT).show()
        }

        // --- PRICING PLAN BUTTONS ---
        findViewById<Button>(R.id.btn_plan_day).setOnClickListener { openUrl("https://aiphotostudio.co.uk/pricing") }
        findViewById<Button>(R.id.btn_plan_monthly).setOnClickListener { openUrl("https://aiphotostudio.co.uk/pricing") }
        findViewById<Button>(R.id.btn_plan_yearly).setOnClickListener { openUrl("https://aiphotostudio.co.uk/pricing") }

        // --- EXTERNAL BUSINESS & FOOTER LINKS ---
        findViewById<Button>(R.id.btn_link_bds).setOnClickListener { openUrl("https://bryantdigitalsolutions.com") }
        findViewById<Button>(R.id.btn_link_bgh).setOnClickListener { openUrl("https://bryantgroupholdings.co.uk") }
        findViewById<Button>(R.id.btn_footer_terms).setOnClickListener { openUrl("https://aiphotostudio.co.uk/terms") }

        // --- NAVIGATION ---
        btnHeaderGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnHeaderSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnAuthSignin.setOnClickListener {
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java))
        }
        btnAuthSignup.setOnClickListener {
            if (auth.currentUser == null) startActivity(Intent(this, LoginActivity::class.java))
        }

        viewsInitialized = true
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.path?.contains("/auth") == true) {
            webView.loadUrl(data.toString())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (HTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun processBlob(base64Data: String) {
                runOnUiThread { saveImageToGallery(base64Data) }
            }
        }, "AndroidInterface")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                f: ValueCallback<Array<Uri>>?,
                p: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = f
                showSourceDialog()
                return true
            }
        }
    }

    private fun saveImageToGallery(base64Data: String) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Photo Studio")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { contentResolver.openOutputStream(it)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }

            runOnUiThread { Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            Log.e("MainActivity", "Save Error: ${e.message}")
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_image_source))
            .setItems(options) { _, which -> if (which == 0) launchCamera() else launchGallery() }
            .setOnCancelListener { filePathCallback?.onReceiveValue(null) }
            .show()
    }

    private fun launchCamera() {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIPhotoStudio")
        if (!directory.exists()) directory.mkdirs()
        val file = File(directory, "temp_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "com.aiphotostudio.bgremover.fileprovider", file)
        takePicture.launch(cameraImageUri!!)
    }

    private fun launchGallery() = pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun updateHeaderUi() {
        val user = auth.currentUser
        btnAuthSignin.text = if (user != null) getString(R.string.sign_out) else getString(R.string.sign_in)
        btnAuthSignup.visibility = if (user != null) View.GONE else View.VISIBLE
        tvSignedInStatus.visibility = if (user != null) View.VISIBLE else View.GONE
        user?.let {
            tvSignedInStatus.text = getString(R.string.signed_in_as, it.email)
        }
    }

    private fun signOut() {
        auth.signOut()
        updateHeaderUi()
        Toast.makeText(this, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}
