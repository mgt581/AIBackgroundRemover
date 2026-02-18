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

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed", e)
                Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
        injectNativeConfig()
    }

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
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/settings.html")
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/gallery.html")
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/privacy")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            backgroundWebView.loadUrl("https://aiphotostudio.co.uk/terms")
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
                callback = { success, uriOrMessage ->
                    this@MainActivity.runOnUiThread {
                        if (success) {
                            Toast.makeText(this@MainActivity, "Saved to Device Gallery", Toast.LENGTH_SHORT).show()
                            backgroundWebView.evaluateJavascript("if(window.onNativeSaveSuccess) window.onNativeSaveSuccess('$uriOrMessage');", null)
                        } else {
                            val message = uriOrMessage ?: "Save Failed"
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                            backgroundWebView.evaluateJavascript("if(window.onNativeSaveFailed) window.onNativeSaveFailed('$message');", null)
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
            
            loadUrl("https://aiphotostudio.co.uk/")
        }
    }

    private fun injectNativeConfig() {
        val user = auth.currentUser
        val userId = user?.uid ?: ""
        val userEmail = user?.email ?: "Guest"
        
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

    private fun startNativeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    updateHeaderUi()
                    injectNativeConfig()
                    backgroundWebView.reload()
                    Toast.makeText(this, "Sign-in successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Firebase Auth failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery), getString(R.string.cancel))
        AlertDialog.Builder(this)
            .setTitle(R.string.select_image_source)
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

    @SuppressLint("SetTextI18n")
    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            tvAuthStatus.visibility = View.VISIBLE
            tvAuthStatus.text = user.email ?: "Signed In"
            btnHeaderLogin.text = "Logout"
        } else {
            tvAuthStatus.visibility = View.GONE
            btnHeaderLogin.text = getString(R.string.sign_in)
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
