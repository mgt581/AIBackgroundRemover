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
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    private lateinit var backgroundWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // UI Elements (Headers/Buttons)
    private lateinit var btnAuthAction: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var btnSignUp: MaterialButton
    private lateinit var btnSaveToGallery: MaterialButton
    private lateinit var tvAuthStatus: TextView

    // Footer Social Links
    private lateinit var btnWhatsApp: TextView
    private lateinit var btnTikTok: TextView
    private lateinit var btnFacebook: TextView

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val results = if (uri != null) arrayOf(uri) else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
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
        
        btnSaveToGallery.setOnClickListener {
            // Trigger manual save
            extractAndSaveImage()
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

    private fun extractAndSaveImage() {
        val js = """
            (async function() {
                try {
                    let dataUrl = "";
                    if (typeof getResultImageData === 'function') {
                        dataUrl = getResultImageData();
                    } 
                    
                    if (!dataUrl) {
                        const imgs = Array.from(document.querySelectorAll('img'));
                        const resultImg = imgs.find(img => (img.src.startsWith('data:') || img.src.startsWith('blob:')) && img.width > 100) || imgs[0];
                        
                        if (resultImg) {
                            if (resultImg.src.startsWith('blob:')) {
                                const response = await fetch(resultImg.src);
                                const blob = await response.blob();
                                dataUrl = await new Promise(resolve => {
                                    const reader = new FileReader();
                                    reader.onloadend = () => resolve(reader.result);
                                    reader.readAsDataURL(blob);
                                });
                            } else {
                                dataUrl = resultImg.src;
                            }
                        }
                    }

                    if (dataUrl && dataUrl.startsWith('data:image')) {
                        Android.saveToDevice(dataUrl);
                    } else {
                        Android.showToast("No image found to save");
                    }
                } catch (e) {
                    Android.showToast("Save error: " + e.message);
                }
            })();
        """.trimIndent()
        backgroundWebView.evaluateJavascript(js, null)
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
            addJavascriptInterface(AndroidInterface(), "Studio")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return if (url.startsWith("http://") || url.startsWith("https://")) {
                        false
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
                    injectAutoSaveHook()
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
                    
                    showImageSourceDialog()
                    return true
                }
            }
            
            setDownloadListener { url, _, _, _, _ ->
                if (url.startsWith("data:image") || url.startsWith("blob:")) {
                    evaluateJavascript("Android.saveToDevice('$url')", null)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            
            loadUrl("https://aiphotostudio.co")
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Take Photo
                        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), 
                            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
                        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                        cameraImageUri?.let { cameraLauncher.launch(it) } ?: run {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                        }
                    }
                    1 -> { // Choose from Gallery
                        galleryLauncher.launch("image/*")
                    }
                    2 -> { // Cancel
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                        dialog.dismiss()
                    }
                }
            }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
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

    private fun injectAutoSaveHook() {
        val script = """
            (function() {
                const observer = new MutationObserver((mutations) => {
                    const imgs = document.querySelectorAll('img');
                    imgs.forEach(async img => {
                        if ((img.src.startsWith('data:image') || img.src.startsWith('blob:')) && 
                            img.width > 200 && !img.dataset.autoSaved) {
                            
                            img.dataset.autoSaved = 'true';
                            let dataUrl = img.src;
                            
                            if (dataUrl.startsWith('blob:')) {
                                try {
                                    const response = await fetch(dataUrl);
                                    const blob = await response.blob();
                                    dataUrl = await new Promise(resolve => {
                                        const reader = new FileReader();
                                        reader.onloadend = () => resolve(reader.result);
                                        reader.readAsDataURL(blob);
                                    });
                                } catch (e) { return; }
                            }
                            
                            if (dataUrl.startsWith('data:image')) {
                                Android.saveToGallery(dataUrl);
                            }
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
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun downloadImage(base64Data: String?) {
            saveToDevice(base64Data)
        }

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
            if (base64Data == null) return
            if (base64Data.startsWith("blob:")) {
                runOnUiThread {
                    backgroundWebView.evaluateJavascript("""
                        (async function() {
                            const response = await fetch('$base64Data');
                            const blob = await response.blob();
                            const reader = new FileReader();
                            reader.onloadend = () => Android.saveToGallery(reader.result);
                            reader.readAsDataURL(blob);
                        })()
                    """.trimIndent(), null)
                }
                return
            }

            if (!base64Data.startsWith("data:image")) return

            runOnUiThread {
                try {
                    val pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1)
                    val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return@runOnUiThread

                    val userId = auth.currentUser?.uid ?: "guest"
                    val userDir = File(filesDir, "saved_images/${userId}")
                    if (!userDir.exists()) userDir.mkdirs()
                    
                    val file = File(userDir, "img_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    
                    Toast.makeText(this@MainActivity, "Auto-saved to App Gallery", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Gallery save failed", e)
                }
            }
        }

        @JavascriptInterface
        fun saveToDevice(base64Data: String?) {
            if (base64Data == null) return
            if (base64Data.startsWith("blob:")) {
                runOnUiThread {
                    backgroundWebView.evaluateJavascript("""
                        (async function() {
                            const response = await fetch('$base64Data');
                            const blob = await response.blob();
                            const reader = new FileReader();
                            reader.onloadend = () => Android.saveToDevice(reader.result);
                            reader.readAsDataURL(blob);
                        })()
                    """.trimIndent(), null)
                }
                return
            }

            if (!base64Data.startsWith("data:image")) return
            runOnUiThread {
                saveBitmapToPublicGallery(base64Data)
            }
        }
    }

    private fun saveBitmapToPublicGallery(base64Data: String) {
        try {
            val pureBase64 = base64Data.substring(base64Data.indexOf(",") + 1)
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Photo Studio")
                }
                val itemUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) outputStream = contentResolver.openOutputStream(itemUri)
            } else {
                val studioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Photo Studio")
                if (!studioDir.exists()) studioDir.mkdirs()
                val file = File(studioDir, fileName)
                outputStream = FileOutputStream(file)
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "Saved to device storage", Toast.LENGTH_SHORT).show()
                // Also save to app gallery
                AndroidInterface().saveToGallery(base64Data)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Device save failed", e)
        }
    }

    private fun applyBackgroundColor(color: String) {
        backgroundWebView.evaluateJavascript("if (typeof setBackgroundColor === 'function') { setBackgroundColor('$color'); }", null)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backgroundWebView.canGoBack()) backgroundWebView.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun handleAuthAction() {
        if (auth.currentUser != null) {
            auth.signOut()
            updateHeaderUi()
            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
        } else startActivity(Intent(this, LoginActivity::class.java))
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
        try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        catch (_: Exception) { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }
}
