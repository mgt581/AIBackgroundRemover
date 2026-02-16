package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
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
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    private lateinit var backgroundWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // UI Elements (Headers/Buttons)
    private lateinit var btnAuthAction: MaterialButton
    private lateinit var btnHeaderSettings: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnSignUp: MaterialButton
    private lateinit var btnSaveToGallery: MaterialButton
    private lateinit var tvAuthStatus: TextView

    // Footer Social Links
    private lateinit var btnWhatsApp: TextView
    private lateinit var btnTikTok: TextView
    private lateinit var btnFacebook: TextView

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val results = if (uri != null) arrayOf(uri) else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        initViews()
        setupClickListeners()
        setupWebView()
        updateHeaderUi()
        setupBackNavigation()
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
    }

    private fun initViews() {
        // Header/Auth Elements
        btnAuthAction = findViewById(R.id.ybtn_auth_action)
        btnHeaderSettings = findViewById(R.id.btn_header_settings)
        btnGallery = findViewById(R.id.btn_gallery)
        btnSignUp = findViewById(R.id.btn_sign_up)
        btnSaveToGallery = findViewById(R.id.btn_save_to_gallery)
        tvAuthStatus = findViewById(R.id.tv_auth_status)

        // Social Links
        btnWhatsApp = findViewById(R.id.btn_whatsapp)
        btnTikTok = findViewById(R.id.btn_tiktok)
        btnFacebook = findViewById(R.id.btn_facebook)

        // Main Content WebView
        backgroundWebView = findViewById(R.id.backgroundWebView)
    }

    private fun setupClickListeners() {
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnAuthAction.setOnClickListener { handleAuthAction() }
        btnSignUp.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        btnHeaderSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        
        btnSaveToGallery.setOnClickListener {
            // Trigger JS to get the image data and send it to Android
            backgroundWebView.evaluateJavascript("if (typeof getResultImageData === 'function') { Android.saveToGallery(getResultImageData()); } else { Android.saveToGallery(document.querySelector('img')?.src); }", null)
        }

        // Social Links
        btnWhatsApp.setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        btnTikTok.setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        btnFacebook.setOnClickListener { openUrl(getString(R.string.facebook_url)) }

        // Footer Navigation Buttons
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
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            openUrl("https://ai-photo-studio-24354.web.app/privacy")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        backgroundWebView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
            }
            addJavascriptInterface(AndroidInterface(), "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return if (url.startsWith("http://") || url.startsWith("https://")) {
                        false // Let WebView load the URL
                    } else {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, request.url))
                            true
                        } catch (_: Exception) {
                            true
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectBackgroundPickerHook()
                    injectSaveToDeviceHook()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    filePickerLauncher.launch("image/*")
                    return true
                }
            }
            // Load the requested URL
            loadUrl("https://aiphotostudio.co")
        }
    }

    private fun injectBackgroundPickerHook() {
        val script = """
            (function() {
                const observer = new MutationObserver((mutations) => {
                    const buttons = document.querySelectorAll('button');
                    buttons.forEach(button => {
                        if (button.innerText.trim() === 'Choose Background' && !button.dataset.hooked) {
                            button.dataset.hooked = 'true';
                            button.onclick = (e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                Android.showBackgroundPicker();
                            };
                        }
                    });
                });
                observer.observe(document.body, { childList: true, subtree: true });
            })();
        """.trimIndent()
        backgroundWebView.evaluateJavascript(script, null)
    }

    private fun injectSaveToDeviceHook() {
        val script = """
            (function() {
                const observer = new MutationObserver((mutations) => {
                    const buttons = document.querySelectorAll('button');
                    buttons.forEach(button => {
                        if ((button.innerText.trim().toLowerCase().includes('save') || 
                             button.innerText.trim().toLowerCase().includes('download')) && 
                            !button.dataset.saveHooked) {
                            button.dataset.saveHooked = 'true';
                            button.onclick = (e) => {
                                // We don't necessarily want to prevent default if the web app handles it fine,
                                // but we can intercept the data if it provides it.
                                // For now, let's assume we want to intercept 'Download' to save to local storage.
                                if (button.innerText.trim().toLowerCase().includes('download')) {
                                     // Intercept and handle via Android
                                     // e.preventDefault();
                                     // Android.saveToDevice(...);
                                }
                            };
                        }
                    });
                });
                observer.observe(document.body, { childList: true, subtree: true });
            })();
        """.trimIndent()
        backgroundWebView.evaluateJavascript(script, null)
    }

    inner class AndroidInterface {
        @JavascriptInterface
        fun showBackgroundPicker() {
            runOnUiThread {
                val colors = arrayOf("Red", "Blue", "Green", "Yellow", "Pink", "Purple", "White", "Black")
                val hexColors = arrayOf("#FF0000", "#0000FF", "#00FF00", "#FFFF00", "#FFC0CB", "#800080", "#FFFFFF", "#000000")

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Choose Background Color")
                    .setItems(colors) { _, which ->
                        applyBackgroundColor(hexColors[which])
                    }
                    .show()
            }
        }

        @JavascriptInterface
        fun saveToGallery(base64Data: String?) {
            if (base64Data == null || !base64Data.startsWith("data:image")) {
                runOnUiThread { Toast.makeText(this@MainActivity, "No image found to save", Toast.LENGTH_SHORT).show() }
                return
            }
            
            runOnUiThread {
                try {
                    val pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1)
                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    
                    val userId = auth.currentUser?.uid ?: "guest"
                    val userDir = File(filesDir, "saved_images/$userId")
                    if (!userDir.exists()) userDir.mkdirs()
                    
                    val fileName = "img_${System.currentTimeMillis()}.png"
                    val file = File(userDir, fileName)
                    
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    Toast.makeText(this@MainActivity, "Saved to App Gallery", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error saving to gallery", e)
                    Toast.makeText(this@MainActivity, "Failed to save to gallery", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun saveToDevice(base64Data: String?) {
            if (base64Data == null || !base64Data.startsWith("data:image")) return
            
            runOnUiThread {
                saveBitmapToPublicGallery(base64Data)
            }
        }
    }

    private fun saveBitmapToPublicGallery(base64Data: String) {
        try {
            val pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1)
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Photo Studio")
                }
                val itemUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    outputStream = contentResolver.openOutputStream(itemUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val studioDir = File(imagesDir, "AI Photo Studio")
                if (!studioDir.exists()) studioDir.mkdirs()
                val file = File(studioDir, fileName)
                outputStream = FileOutputStream(file)
                
                @Suppress("DEPRECATION")
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(file)
                sendBroadcast(mediaScanIntent)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "Saved to device storage", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving to device", e)
            Toast.makeText(this, "Failed to save to device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyBackgroundColor(color: String) {
        val script = "if (typeof setBackgroundColor === 'function') { setBackgroundColor('$color'); } else { console.log('setBackgroundColor not found'); }"
        backgroundWebView.evaluateJavascript(script, null)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backgroundWebView.canGoBack()) {
                    backgroundWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
            tvAuthStatus.visibility = View.VISIBLE
            tvAuthStatus.text = "Account: ${user.email ?: "Guest"}"
            btnAuthAction.text = "Logout"
            btnSignUp.visibility = View.GONE
        } else {
            tvAuthStatus.visibility = View.GONE
            btnAuthAction.text = getString(R.string.sign_in)
            btnSignUp.visibility = View.VISIBLE
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}
