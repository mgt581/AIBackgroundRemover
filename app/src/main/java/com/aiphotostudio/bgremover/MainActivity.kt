@file:Suppress("DEPRECATION")

package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
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
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var auth: FirebaseAuth

    private lateinit var btnHeaderGallery: Button
    private lateinit var btnHeaderSettings: Button
    private lateinit var btnAuthSignin: Button
    private lateinit var btnAuthSignup: Button
    private lateinit var tvSignedInStatus: TextView

    private var lastCapturedBase64: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        auth = FirebaseAuth.getInstance()
        
        // Initialize Views
        webView = findViewById(R.id.webView)
        btnHeaderGallery = findViewById(R.id.btn_header_gallery)
        btnHeaderSettings = findViewById(R.id.btn_header_settings)
        btnAuthSignin = findViewById(R.id.btn_auth_signin)
        btnAuthSignup = findViewById(R.id.btn_auth_signup)
        tvSignedInStatus = findViewById(R.id.tv_signed_in_status)

        try {
            findViewById<View>(R.id.fab_save).setOnClickListener {
                lastCapturedBase64?.let { saveImageToGallery(it) } ?: run {
                    Toast.makeText(this, "No image to save yet", Toast.LENGTH_SHORT).show()
                }
            }

            btnHeaderGallery.setOnClickListener {
                startActivity(Intent(this, GalleryActivity::class.java))
            }

            btnHeaderSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            btnAuthSignin.setOnClickListener {
                if (auth.currentUser != null) {
                    signOut()
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }

            btnAuthSignup.setOnClickListener {
                if (auth.currentUser == null) {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }

            findViewById<Button>(R.id.btn_plan_day).setOnClickListener { webView.loadUrl("https://aiphotostudio.co.uk/pricing") }
            findViewById<Button>(R.id.btn_plan_monthly).setOnClickListener { webUrl("https://aiphotostudio.co.uk/pricing") }
            findViewById<Button>(R.id.btn_plan_yearly).setOnClickListener { webUrl("https://aiphotostudio.co.uk/pricing") }

            findViewById<Button>(R.id.btn_link_bds).setOnClickListener { openUrl("https://bryantdigitalsolutions.com") }
            findViewById<Button>(R.id.btn_link_bgh).setOnClickListener { openUrl("https://bryantgroupholdings.co.uk") }
            findViewById<Button>(R.id.btn_footer_terms).setOnClickListener { webUrl("https://aiphotostudio.co.uk/terms") }

            if (savedInstanceState == null) {
                handleIntent(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    private fun webUrl(url: String) {
        webView.loadUrl(url)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.path?.contains("/auth") == true) {
            webView.loadUrl(data.toString())
        } else {
            webView.loadUrl("https://aiphotostudio.co.uk")
        }
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            btnAuthSignin.text = getString(R.string.sign_out)
            btnAuthSignup.visibility = View.GONE
            tvSignedInStatus.text = getString(R.string.signed_in_as, user.email?.take(20) ?: "User")
            tvSignedInStatus.visibility = View.VISIBLE
        } else {
            btnAuthSignin.text = getString(R.string.sign_in)
            btnAuthSignup.visibility = View.VISIBLE
            tvSignedInStatus.visibility = View.GONE
        }
    }

    private fun signOut() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            updateHeaderUi()
            Toast.makeText(this, getString(R.string.signed_out_success), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery(base64Data: String) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Photo Studio")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
                contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Photo Studio")
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, fileName)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }
            runOnUiThread { Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            Log.e("MainActivity", "Save failed", e)
            runOnUiThread { Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show() }
        }
    }

}
