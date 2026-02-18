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
 */
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var backgroundWebView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private lateinit var tvAuthStatus: TextView
    private lateinit var btnHeaderLogin: Button

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        else filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
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

        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/settings.html")
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            backgroundWebView.loadUrl("https://mgt581.github.io/photo-static-main-3/gallery.html")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        backgroundWebView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            val webInterface = WebAppInterface(
                context = this@MainActivity,
                onBackgroundPickerRequested = { runOnUiThread { showImageSourceDialog() } },
                onGoogleSignInRequested = { runOnUiThread { startNativeGoogleSignIn() } },
                onLoginRequested = { runOnUiThread { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) } },
                onLoginSuccess = { runOnUiThread { backgroundWebView.reload() } },
                callback = { success, uriOrMessage ->
                    runOnUiThread {
                        val msg = if (success) "Saved to Gallery" else uriOrMessage ?: "Save Failed"
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        backgroundWebView.evaluateJavascript("if(window.onNativeSaveSuccess) window.onNativeSaveSuccess('$uriOrMessage');", null)
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
                        if (auth.currentUser == null) startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = callback
                    showImageSourceDialog()
                    return true
                }
            }
            loadUrl("https://mgt581.github.io/photo-static-main-3/")
        }
    }

    private fun injectNativeConfig() {
        val user = auth.currentUser
        val userId = user?.uid ?: ""
        val userEmail = user?.email ?: "Guest"

        val script = """
            (function() {
                window.MVP_FREE_PRO = true;
                window.NATIVE_AUTH_USER_ID = '$userId';
                window.NATIVE_AUTH_EMAIL = '$userEmail';
                
                if ('$userId' !== '') {
                    localStorage.setItem('userId', '$userId');
                    sessionStorage.setItem('userId', '$userId');
                    if (window.onNativeAuthResolved) window.onNativeAuthResolved('$userId', '$userEmail');
                }

                var style = document.createElement('style');
                style.innerHTML = '.auth-container, .login-btn, .signup-btn, .web-login-overlay { display: none !important; }';
                document.head.appendChild(style);
                
                console.log('Native Sync: ' + '$userId');
            })()
        """.trimIndent()
        backgroundWebView.evaluateJavascript(script, null)
    }

    private fun startNativeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInLauncher.launch(GoogleSignIn.getClient(this, gso).signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateHeaderUi()
                    injectNativeConfig()
                    backgroundWebView.reload()
                }
            }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this).setTitle("Select Image").setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    val file = File(getExternalFilesDir(null), "IMG_${System.currentTimeMillis()}.jpg")
                    cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    cameraImageUri?.let { cameraLauncher.launch(it) }
                }
                1 -> galleryLauncher.launch("image/*")
                else -> { filePathCallback?.onReceiveValue(null); filePathCallback = null; dialog.dismiss() }
            }
        }.show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backgroundWebView.canGoBack()) backgroundWebView.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        tvAuthStatus.visibility = if (user != null) View.VISIBLE else View.GONE
        tvAuthStatus.text = user?.email ?: ""
        btnHeaderLogin.text = if (user != null) "Logout" else "Sign In"
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Link Error", Toast.LENGTH_SHORT).show() }
    }
}
