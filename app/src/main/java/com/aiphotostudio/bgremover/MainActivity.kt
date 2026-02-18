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
        injectNativeConfig() // Re-inject on every resume
    }

    /**
     * Binds UI elements from the layout.
     */
    private fun initViews() {
        tvAuthStatus = findViewById(R.id.tv_auth_status)
        btnHeaderLogin = findViewById(R.id.btn_header_login)
        backgroundWebView = findViewById(R.id.backgroundWebView)
    }

    /**
     * Sets up click listeners for header buttons.
     */
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
    }

    /**
     * Configures the WebView, its settings, and the JavaScript interface.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        backgroundWebView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
            }

            val webInterface = WebAppInterface(
                context = this@MainActivity,
                onBackgroundPickerRequested = { runOnUiThread { showImageSourceDialog() } },
                onGoogleSignInRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginSuccess = { runOnUiThread { backgroundWebView.reload() } },
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
                        return true // Prevent the WebView from loading the web login page
                    }
                    return false // Let the WebView handle all other links
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
     * Injects JavaScript into the WebView to synchronize authentication state.
     */
    private fun injectNativeConfig() {
        val user = auth.currentUser
        val userId = user?.uid ?: ""
        val userEmail = user?.email ?: ""

        val script = """
            (function() {
                console.log('Injecting Native Auth State. User ID: $userId');
                
                // Forcefully set auth info for the web app to use
                window.NATIVE_AUTH_USER_ID = '$userId';
                window.NATIVE_AUTH_EMAIL = '$userEmail';

                if ('$userId' !== '') {
                    localStorage.setItem('userId', '$userId');
                    sessionStorage.setItem('userId', '$userId');
                    // Trigger any JS function that re-checks auth
                    if (window.onNativeAuthResolved) {
                        window.onNativeAuthResolved('$userId', '$userEmail');
                    }
                }

                // Hide all web-based login elements permanently
                var style = document.createElement('style');
                style.innerHTML = '.auth-container, .login-btn, .signup-btn, #auth-section, [href*="signin.html"] { display: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        
        backgroundWebView.evaluateJavascript(script, null)
    }

    /**
     * Displays a dialog to choose between taking a photo or selecting from the gallery.
     */
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

    /**
     * Opens the camera to take a photo.
     */
    private fun openCamera() {
        val photoFile = File(getExternalFilesDir(null), "IMG_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraLauncher.launch(cameraImageUri)
    }

    /**
     * Sets up the back press handler to navigate WebView history.
     */
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

    /**
     * Updates the header UI to reflect the current authentication state.
     */
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
