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

    // --- PHOTO PICKER LOGIC ---
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ivPreview.setImageURI(uri)
            filePathCallback?.onReceiveValue(arrayOf(uri))
            triggerWebUpload()
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            ivPreview.setImageURI(cameraImageUri)
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
            triggerWebUpload()
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private fun triggerWebUpload() {
        webView.evaluateJavascript("javascript:if(window.handleAppPhotoUpload) window.handleAppPhotoUpload();", null)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for the studio.", Toast.LENGTH_SHORT).show()
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

        findViewById<Button>(R.id.btn_choose_photo).setOnClickListener {
            // We must trigger the web file input first to set the callback
            webView.evaluateJavascript("document.querySelector('input[type=\"file\"]')?.click();", null)
        }

        findViewById<Button>(R.id.btn_remove_bg).setOnClickListener {
            executeWebAction("remove background")
            Toast.makeText(this, "Removing background...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_change_bg).setOnClickListener {
            executeWebAction("change background")
        }

        findViewById<Button>(R.id.btn_choose_background).setOnClickListener {
            webView.evaluateJavascript("""
                (function(){
                    var inputs = document.querySelectorAll('input[type="file"]');
                    if(inputs.length > 0) inputs[inputs.length - 1].click(); 
                })();
            """.trimIndent(), null)
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

        // --- NAVIGATION & EXTERNAL ---
        btnHeaderGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnHeaderSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnAuthSignin.setOnClickListener {
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.btn_link_bds).setOnClickListener { openUrl("https://bryantdigitalsolutions.com") }
        findViewById<Button>(R.id.btn_footer_terms).setOnClickListener { openUrl("https://aiphotostudio.co.uk/terms") }

        viewsInitialized = true
    }

    private fun executeWebAction(keyword: String) {
        webView.evaluateJavascript("""
            (function(){
                var buttons = Array.from(document.querySelectorAll('button, a, span'));
                var target = buttons.find(el => el.innerText.toLowerCase().includes('$keyword'));
                if(target) target.click();
            })();
        """.trimIndent(), null)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { if (it.path?.contains("/auth") == true) webView.loadUrl(it.toString()) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.contains("aiphotostudio.co.uk") || url.contains("google.com")) false
                else { openUrl(url); true }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
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
                filePathCallback?.onReceiveValue(null) // Reset any existing
                filePathCallback = f
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
            Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Save Error: ${e.message}")
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which -> if (which == 0) launchCamera() else launchGallery() }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    private fun launchCamera() {
        try {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIPhotoStudio")
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, "temp_${System.currentTimeMillis()}.jpg")

            // Matches the authority in your Manifest exactly
            cameraImageUri = FileProvider.getUriForFile(this, "com.aiphotostudio.bgremover.fileprovider", file)
            takePicture.launch(cameraImageUri!!)
        } catch (e: Exception) {
            Log.e("MainActivity", "Camera Launch Error: ${e.message}")
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun updateHeaderUi() {
        val user = auth.currentUser
        btnAuthSignin.text = if (user != null) "Sign Out" else "Sign In"
        btnAuthSignup.visibility = if (user != null) View.GONE else View.VISIBLE
        tvSignedInStatus.visibility = if (user != null) View.VISIBLE else View.GONE
        user?.let { tvSignedInStatus.text = "Signed in as: ${it.email}" }
    }

    private fun signOut() {
        auth.signOut()
        updateHeaderUi()
        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}