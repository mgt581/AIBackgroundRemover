package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.io.File
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
    private lateinit var btnHeaderSettings: MaterialButton
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
            Toast.makeText(this, "Requesting image save from web...", Toast.LENGTH_SHORT).show()
            // Trigger the web-side save function which uses the AndroidBridge
            backgroundWebView.evaluateJavascript("(function() { " +
                "if(window.AndroidBridge && window.AndroidBridge.saveToGallery) { " +
                "  window.AndroidBridge.saveToGallery(); " +
                "} else if(window.saveToGallery) { " +
                "  window.saveToGallery(); " +
                "} else { " +
                "  return 'saveToGallery not found'; " +
                "} " +
                "return 'success'; " +
                "})()", { result ->
                    if (result?.contains("not found") == true) {
                        Toast.makeText(this, "Save function not found on page", Toast.LENGTH_SHORT).show()
                    }
                })
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
                allowFileAccess = true
                allowContentAccess = true
            }

            addJavascriptInterface(
                WebAppInterface(
                    context = this@MainActivity,
                    onBackgroundPickerRequested = {
                        runOnUiThread {
                            showImageSourceDialog()
                        }
                    },
                    callback = { success, uriOrMessage ->
                        this@MainActivity.runOnUiThread {
                            if (success) {
                                Toast.makeText(this@MainActivity, "Saved to Device Gallery", Toast.LENGTH_SHORT).show()
                                backgroundWebView.evaluateJavascript("window.onNativeSaveSuccess('$uriOrMessage');", null)
                            } else {
                                val message = uriOrMessage ?: "Save Failed"
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                backgroundWebView.evaluateJavascript("window.onNativeSaveFailed('$message');", null)
                            }
                        }
                    }
                ),
                "AndroidBridge"
            )

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

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url ?: return null
                    if (url.scheme == "content") {
                        return try {
                            val mime = contentResolver.getType(url) ?: "image/*"
                            val input = contentResolver.openInputStream(url) ?: return null
                            WebResourceResponse(mime, "UTF-8", input)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
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
            
            loadUrl("https://aiphotostudio.co")
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        val photoFile = File(getExternalFilesDir(null), 
                            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
                        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                        cameraImageUri?.let { cameraLauncher.launch(it) } ?: run {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                        }
                    }
                    1 -> galleryLauncher.launch("image/*")
                    2 -> {
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
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }
}
