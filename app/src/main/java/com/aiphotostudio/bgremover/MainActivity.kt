package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.File

/**
 * Main Activity for the AI Background Remover application.
 */
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var backgroundWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private lateinit var tvAuthStatus: TextView
    private lateinit var btnHeaderLogin: Button

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
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
        setupBackNavigation()
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
        injectNativeConfig()
    }

    private fun initViews() {
        tvAuthStatus = findViewById(R.id.tv_auth_status)
        btnHeaderLogin = findViewById(R.id.btn_header_login)
        backgroundWebView = findViewById(R.id.backgroundWebView)
    }

    private fun setupClickListeners() {
        btnHeaderLogin.setOnClickListener {
            if (auth.currentUser != null) {
                auth.signOut()
                updateHeaderUi()
                backgroundWebView.reload()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/gallery.html")
        }
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/settings")
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/privacy.html")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/terms.html")
        }

        findViewById<View>(R.id.btn_whatsapp).setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        findViewById<View>(R.id.btn_tiktok).setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        findViewById<View>(R.id.btn_facebook).setOnClickListener { openUrl(getString(R.string.facebook_url)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        backgroundWebView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
            }

            val webInterface = WebAppInterface(
                context = this@MainActivity,
                onBackgroundPickerRequested = { runOnUiThread { showImageSourceDialog() } },
                onGoogleSignInRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginSuccess = { runOnUiThread { backgroundWebView.reload() } },
                callback = { success, message ->
                    runOnUiThread {
                        if (success) {
                            if (auth.currentUser != null) {
                                showAutoSaveToast()
                            } else {
                                Toast.makeText(this@MainActivity, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, message ?: getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            addJavascriptInterface(webInterface, "AndroidBridge")
            addJavascriptInterface(webInterface, "Studio")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectNativeConfig()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.contains("signin.html") || url.contains("login")) {
                        if (auth.currentUser == null) {
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        }
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                    filePathCallback = callback
                    showImageSourceDialog()
                    return true
                }
            }
            loadUrl("https://mgt581.github.io/photo-static-main-3/")
        }
    }

    /**
     * Shows a custom toast message when an image is auto-saved to the cloud gallery.
     */
    private fun showAutoSaveToast() {
        val inflater = LayoutInflater.from(this)
        val layout = inflater.inflate(R.layout.layout_custom_toast, findViewById(R.id.backgroundWebView), false)
        
        with(Toast(applicationContext)) {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 100)
            duration = Toast.LENGTH_LONG
            view = layout
            show()
        }
    }

    private fun injectNativeConfig() {
        val user = auth.currentUser
        val userId = user?.uid ?: ""
        val userEmail = user?.email ?: ""

        val script = """
            (function() {
                window.NATIVE_AUTH_USER_ID = '$userId';
                window.NATIVE_AUTH_EMAIL = '$userEmail';
                if ('$userId' !== '') {
                    localStorage.setItem('userId', '$userId');
                    sessionStorage.setItem('userId', '$userId');
                    if (window.onNativeAuthResolved) window.onNativeAuthResolved('$userId', '$userEmail');
                }

                var style = document.createElement('style');
                style.innerHTML = `
                    .auth-container, .login-btn, .signup-btn, #auth-section, [href*="signin.html"],
                    .gallery-btn, .settings-btn, #nav-gallery, #nav-settings,
                    .watermark, #watermark, [class*="watermark"], [id*="watermark"] { 
                        display: none !important; 
                    }
                `;
                document.head.appendChild(style);
                
                if (window.setNativeUser) window.setNativeUser('$userId', '$userEmail');
            })();
        """.trimIndent()
        
        backgroundWebView.evaluateJavascript(script, null)
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery), getString(R.string.cancel))
        AlertDialog.Builder(this)
            .setTitle(R.string.select_image_source)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> galleryLauncher.launch("image/*")
                    else -> dialog.dismiss()
                }
            }.show()
    }

    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(null), "IMG_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraImageUri?.let { cameraLauncher.launch(it) }
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

    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            tvAuthStatus.visibility = View.VISIBLE
            tvAuthStatus.text = getString(R.string.signed_in)
            btnHeaderLogin.text = getString(R.string.sign_out)
        } else {
            tvAuthStatus.visibility = View.GONE
            btnHeaderLogin.text = getString(R.string.sign_in)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
