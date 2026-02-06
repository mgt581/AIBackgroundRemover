package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var auth: FirebaseAuth
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // UI Elements
    private lateinit var tvSignedInStatus: TextView
    private lateinit var btnAuthSignin: Button

    // 1. Permission Launcher (Fixed)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] != true) {
            Toast.makeText(this, "Camera permission required for photos", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Gallery Launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // 3. Camera Launcher
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
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

        // PILL BUTTON: "Choose Photo" manually opens the dialog
        findViewById<Button>(R.id.btn_choose_photo).setOnClickListener { 
            showSourceDialog() 
        }

        // PILL BUTTON: "Remove BG" triggers the website's button
        findViewById<Button>(R.id.btn_remove_bg).setOnClickListener {
            webView.evaluateJavascript("(function(){ var b=Array.from(document.querySelectorAll('button')).find(x=>x.innerText.includes('Remove Background')); if(b) b.click(); })();", null)
        }

        findViewById<Button>(R.id.btn_footer_privacy).setOnClickListener { 
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aiphotostudio.co.uk/privacy"))) 
        }

        btnAuthSignin.setOnClickListener { 
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java)) 
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                f: ValueCallback<Array<Uri>>?,
                p: WebChromeClient.FileChooserParams?
            ): Boolean {
                filePathCallback = f
                showSourceDialog()
                return true
            }
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val photoFile = File(externalCacheDir, "camera_photo.jpg")
                    cameraImageUri = FileProvider.getUriForFile(this, "$packageName.provider", photoFile)
                    takePhotoLauncher.launch(cameraImageUri)
                } else {
                    pickImageLauncher.launch("image/*")
                }
            }
            .setOnCancelListener { 
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null 
            }
            .show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
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
}
