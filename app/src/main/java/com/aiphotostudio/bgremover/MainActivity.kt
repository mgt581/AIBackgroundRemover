package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Activity for the application.
 * Manages the primary WebView and synchronizes native authentication with it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var backgroundWebView: WebView? = null // Make WebView nullable for safety
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private lateinit var tvAuthStatus: TextView
    private lateinit var btnHeaderLogin: Button

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { filePathCallback?.onReceiveValue(arrayOf(it)) }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            auth = FirebaseAuth.getInstance()
            if (!initViews()) {
                Log.e(TAG, "FATAL: View initialization failed. Check activity_main.xml for missing UI elements.")
                Toast.makeText(this, "Critical Error: App UI failed to load.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            
            setupClickListeners()
            setupWebView()
            setupBackNavigation()

        } catch (e: Exception) {
            Log.e(TAG, "FATAL CRASH during onCreate", e)
            Toast.makeText(this, "App failed to start. Check Logcat for MainActivity_FATAL.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
        injectNativeConfig()
    }

    private fun initViews(): Boolean {
        try {
            tvAuthStatus = findViewById(R.id.tv_auth_status)
            btnHeaderLogin = findViewById(R.id.btn_header_login)
            backgroundWebView = findViewById(R.id.backgroundWebView)

            if (backgroundWebView == null) {
                Log.e(TAG, "Could not find WebView with ID 'backgroundWebView' in your layout.")
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during view initialization", e)
            return false
        }
    }

    private fun setupClickListeners() {
        btnHeaderLogin.setOnClickListener {
            if (auth.currentUser != null) {
                auth.signOut()
                updateHeaderUi()
                backgroundWebView?.reload()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        backgroundWebView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
            }

            val webInterface = WebAppInterface(
                context = this@MainActivity,
                onBackgroundPickerRequested = { runOnUiThread { showImageSourceDialog() } },
                onGoogleSignInRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginSuccess = { runOnUiThread { backgroundWebView?.reload() } },
                callback = { success, message ->
                    runOnUiThread {
                        val toastMessage = if (success) getString(R.string.saved_to_gallery) else message ?: getString(R.string.save_failed)
                        Toast.makeText(this@MainActivity, toastMessage, Toast.LENGTH_SHORT).show()
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
                    if (url.contains("signin.html") || url.contains("/login")) {
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

    internal fun injectNativeConfig() {
        val userId = auth.currentUser?.uid ?: ""
        val script = """
            (function() {
                window.NATIVE_AUTH_USER_ID = '$userId';
                if ('$userId' !== '') {
                    localStorage.setItem('userId', '$userId');
                    if (window.onNativeAuthResolved) window.onNativeAuthResolved('$userId');
                }
                var style = document.createElement('style');
                style.innerHTML = '.auth-container, .login-btn, .signup-btn, #auth-section, [href*="signin.html"], .watermark, #watermark { display: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        backgroundWebView?.evaluateJavascript(script, null)
    }

    internal fun showImageSourceDialog() {
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
                if (backgroundWebView?.canGoBack() == true) {
                    backgroundWebView?.goBack()
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
            tvAuthStatus.text = user.email ?: getString(R.string.signed_in)
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
