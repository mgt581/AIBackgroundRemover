@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private lateinit var auth: FirebaseAuth

    private lateinit var btnAuthAction: Button
    private lateinit var btnHeaderSettings: Button
    private lateinit var tvSignedInStatus: TextView
    private lateinit var fabSave: ExtendedFloatingActionButton

    private lateinit var btnFooterTerms: Button

    private lateinit var btnLinkBds: Button
    private lateinit var btnLinkBgh: Button
    private lateinit var btnLinkMpa: Button
    private lateinit var btnLinkEmail: Button

    private var bridgeInjectedForUrl: String? = null
    private var lastBridgeInjectionMs: Long = 0L
    private var lastCapturedBase64: String? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            filePathCallback?.onReceiveValue(arrayOf(cameraImageUri!!))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] != true) {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    // Named class to avoid "unused" warning on JS interface
    inner class WebAppInterface {
        @JavascriptInterface
        fun processBlob(base64Data: String) {
            lastCapturedBase64 = base64Data
            runOnUiThread {
                fabSave.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "Image ready to save! Click the save button below.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase (important!)
        FirebaseApp.initializeApp(this)

        // Initialize Firebase App Check with debug provider for development
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        Log.d("MainActivity", "Firebase App Check initialized (debug mode)")

        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            webView = findViewById(R.id.webView)
            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            tvSignedInStatus = findViewById(R.id.tv_signed_in_status)
            fabSave = findViewById(R.id.fab_save)

            btnFooterTerms = findViewById(R.id.btn_footer_terms)

            btnLinkBds = findViewById(R.id.btn_link_bds)
            btnLinkBgh = findViewById(R.id.btn_link_bgh)
            btnLinkMpa = findViewById(R.id.btn_link_mpa)
            btnLinkEmail = findViewById(R.id.btn_link_email)

            updateHeaderUi()

            setupWebView()
            checkAndRequestPermissions()

            fabSave.setOnClickListener {
                lastCapturedBase64?.let {
                    saveImageToGallery(it)
                } ?: run {
                    Toast.makeText(this, "No image to save yet", Toast.LENGTH_SHORT).show()
                }
            }

            btnAuthAction.setOnClickListener {
                if (auth.currentUser != null) {
                    signOut()
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }

            btnHeaderSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            btnFooterTerms.setOnClickListener {
                webView.loadUrl("https://aiphotostudio.co/terms")
            }

            btnLinkBds.setOnClickListener { openUrl("https://bryantdigitalsolutions.com") }
            btnLinkBgh.setOnClickListener { openUrl("https://bryantgroupholdings.co.uk") }
            btnLinkMpa.setOnClickListener { openUrl("https://multipostapp.co.uk") }
            btnLinkEmail.setOnClickListener {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:alex@bryantdigitalsolutions.com".toUri()
                }
                startActivity(intent)
            }

            if (savedInstanceState == null) {
                handleIntent(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    // ... rest of your MainActivity code unchanged ...
}
