package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var auth: FirebaseAuth
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // UI Elements
    private lateinit var tvSignedInStatus: TextView
    private lateinit var btnAuthSignin: Button
    private lateinit var btnAuthSignup: Button

    // Fix for the error in your photo: Permissions Launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
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
        }
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        tvSignedInStatus = findViewById(R.id.tv_signed_in_status)
        btnAuthSignin = findViewById(R.id.btn_auth_signin)
        btnAuthSignup = findViewById(R.id.btn_auth_signup)

        // PILL BUTTON CLICK LISTENERS
        findViewById<Button>(R.id.btn_choose_photo).setOnClickListener { showSourceDialog() }
        
        findViewById<Button>(R.id.btn_remove_bg).setOnClickListener {
            webView.evaluateJavascript("(function(){ var b=Array.from(document.querySelectorAll('button')).find(x=>x.innerText.includes('Remove Background')); if(b) b.click(); })();", null)
        }

        // FOOTER & PRICING
        findViewById<Button>(R.id.btn_plan_day).setOnClickListener { openUrl("https://aiphotostudio.co.uk/pricing") }
        findViewById<Button>(R.id.btn_footer_privacy).setOnClickListener { openUrl("https://aiphotostudio.co.uk/privacy") }
        
        // AUTH
        btnAuthSignin.setOnClickListener { if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java)) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                p: WebChromeClient.FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = f
                showSourceDialog()
                return true
            }
        }
    }

    // Fixed Permission Logic (No more red lines)
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.CAMERA)
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        btnAuthSignin.text = if (user != null) "Sign Out" else "Sign In"
        tvSignedInStatus.text = user?.email ?: "Not signed in"
    }

    private fun signOut() {
        auth.signOut()
        updateHeaderUi()
    }

    private fun saveImageToGallery(base64Data: String) {
        // ... (standard save logic)
    }

    private fun showSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which -> /* Camera or Gallery launch */ }
            .show()
    }
}
