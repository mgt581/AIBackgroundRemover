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
import android.webkit.WebResourceResponse
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Activity for the AI Background Remover application.
 * Manages the primary WebView and native authentication flows.
 */
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var backgroundWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // UI Elements
    private lateinit var tvAuthStatus: TextView
    private lateinit var btnHeaderLogin: Button

    // Social Links
    private lateinit var btnWhatsApp: TextView
    private lateinit var btnTikTok: TextView
    private lateinit var btnFacebook: TextView

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val results = if (uri != null) arrayOf(uri) else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraImageUri != null) {
                filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    account?.idToken?.let { firebaseAuthWithGoogle(it) }
                } catch (e: ApiException) {
                    Log.e(TAG, "Google sign in failed", e)
                    Toast.makeText(
                        this,
                        getString(R.string.login_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after
     *     previously being shut down then this Bundle contains the data it most
     *     recently supplied in onSaveInstanceState(Bundle).
     */
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

    /**
     * Called after onCreate(Bundle) or onRestart() when the activity is being displayed to the user.
     */
    override fun onResume() {
        super.onResume()
        updateHeaderUi()
        injectNativeConfig()
    }

    /**
     * Initializes UI components by finding them in the layout.
     */
    private fun initViews() {
        tvAuthStatus = findViewById(R.id.tv_auth_status)
        btnHeaderLogin = findViewById(R.id.btn_header_login)

        // Social Links
        btnWhatsApp = findViewById(R.id.btn_whatsapp)
        btnTikTok = findViewById(R.id.btn_tiktok)
        btnFacebook = findViewById(R.id.btn_facebook)

        // Main Content WebView
        backgroundWebView = findViewById(R.id.backgroundWebView)
    }

    /**
     * Sets up click listeners for various UI elements.
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

        // Social Links
        btnWhatsApp.setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        btnTikTok.setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        btnFacebook.setOnClickListener { openUrl(getString(R.string.facebook_url)) }

        // Footer Navigation Buttons redirecting to WebView URLs
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/settings.html")
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/gallery.html")
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/privacy")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/terms")
        }
    }

    /**
     * Configures the WebView with necessary settings and a JavaScript interface.
     */
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
                databaseEnabled = true
            }

            val webInterface = WebAppInterface(
                context = this@MainActivity,
                onBackgroundPickerRequested = {
                    runOnUiThread {
                        showImageSourceDialog()
                    }
                },
                onGoogleSignInRequested = {
                    runOnUiThread {
                        startNativeGoogleSignIn()
                    }
                },
                onLoginRequested = {
                    runOnUiThread {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    }
                },
                onLoginSuccess = {
                    runOnUiThread {
                        backgroundWebView.reload()
                    }
                },
                callback = { success, uriOrMessage ->
                    this@MainActivity.runOnUiThread {
                        if (success) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.saved_to_device_gallery),
                                Toast.LENGTH_SHORT
                            ).show()
                            backgroundWebView.evaluateJavascript(
                                "if(window.onNativeSaveSuccess) window.onNativeSaveSuccess('$uriOrMessage');",
                                null
                            )
                        } else {
                            val message = uriOrMessage ?: getString(R.string.save_failed_msg)
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                            backgroundWebView.evaluateJavascript(
                                "if(window.onNativeSaveFailed) window.onNativeSaveFailed('$message');",
                                null
                            )
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

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
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

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
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

            loadUrl("https://mgt581.github.io/photo-static-main-3/")
        }
    }

    /**
     * Injects a native configuration script into the WebView.
     */
    private fun injectNativeConfig() {
        val user = auth.currentUser
        val userId = user?.uid ?: ""
        val userEmail = user?.email ?: getString(R.string.guest)

        val script = """
            (function() {
                // Set Global Native Config
                window.MVP_FREE_PRO = true;
                window.NATIVE_AUTH_USER_ID = '$userId';
                window.NATIVE_AUTH_EMAIL = '$userEmail';
                
                // Hide Web Authentication and Payment UI
                var style = document.createElement('style');
                style.innerHTML = `
                    .auth-container, .login-btn, .signup-btn, #auth-section,
                    .payment-button, .checkout-btn, #payment-section, 
                    .buy-now, [class*="payment"], [id*="payment"],
                    .stripe-button, .paypal-button, .nav-auth-links { 
                        display: none !important; 
                    }
                `;
                document.head.appendChild(style);
                
                // Override internal gallery logic to use Native ID
                if (window.setNativeUser) {
                    window.setNativeUser('$userId', '$userEmail');
                }
                
                console.log('Native config injected. User: ' + '$userId');
            })()
        """.trimIndent()

        backgroundWebView.evaluateJavascript(script, null)
    }

    /**
     * Initiates the native Google Sign-In flow.
     */
    private fun startNativeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    /**
     * Authenticates with Firebase using a Google ID token.
     * @param idToken The Google ID token provided by Google Sign-In.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    updateHeaderUi()
                    injectNativeConfig()
                    backgroundWebView.reload()
                    Toast.makeText(this, getString(R.string.signin_successful), Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(this, getString(R.string.firebase_auth_failed), Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }

    /**
     * Displays a dialog to choose the source for an image (camera or gallery).
     */
    private fun showImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_from_gallery),
            getString(R.string.cancel)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.select_image_source)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        val photoFile = File(
                            getExternalFilesDir(null),
                            "IMG_${
                                SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.getDefault()
                                ).format(Date())
                            }.jpg"
                        )
                        cameraImageUri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            photoFile
                        )
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

    /**
     * Configures a callback to handle the back button press.
     */
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backgroundWebView.canGoBack()) backgroundWebView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    /**
     * Updates the header UI based on the user's authentication status.
     */
    @SuppressLint("SetTextI18n")
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

    /**
     * Opens a URL in an external browser.
     * @param url The URL to open.
     */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_link), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
