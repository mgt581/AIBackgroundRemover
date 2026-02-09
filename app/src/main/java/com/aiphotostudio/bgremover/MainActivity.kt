@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private lateinit var auth: FirebaseAuth

    private var btnAuthAction: Button? = null
    private var btnHeaderSettings: Button? = null
    private var btnGallery: Button? = null
    private var btnSignUp: Button? = null
    private var tvAuthStatus: TextView? = null

    private var llImageActions: LinearLayout? = null
    private var btnSaveFixed: Button? = null
    private var btnDownloadDevice: Button? = null

    private var lastCapturedBase64: String? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraImageUri
        if (success && uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            webView = findViewById(R.id.webView)
            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            btnGallery = findViewById(R.id.btn_gallery)
            btnSignUp = findViewById(R.id.btn_sign_up)
            tvAuthStatus = findViewById(R.id.tv_auth_status)

            llImageActions = findViewById(R.id.ll_image_actions)
            btnSaveFixed = findViewById(R.id.btn_save_fixed)
            btnDownloadDevice = findViewById(R.id.btn_download_device)

            setupClickListeners()
            setupFooterClickListeners()
            updateHeaderUi()
            setupWebView()
            checkAndRequestPermissions()

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            })

            if (savedInstanceState == null) {
                handleIntent(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Initialization error", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupClickListeners() {
        btnSaveFixed?.setOnClickListener {
            lastCapturedBase64?.let { saveToInternalGallery(it) } ?: run {
                Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show()
            }
        }

        btnDownloadDevice?.setOnClickListener {
            lastCapturedBase64?.let { downloadToDevice(it) } ?: run {
                Toast.makeText(this, "No image to download", Toast.LENGTH_SHORT).show()
            }
        }

        btnAuthAction?.setOnClickListener {
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java))
        }

        btnHeaderSettings?.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnGallery?.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnSignUp?.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
    }

    private fun setupFooterClickListeners() {
        findViewById<ImageButton>(R.id.btn_whatsapp)?.setOnClickListener {
            openUrl(getString(R.string.whatsapp_url))
        }

        findViewById<ImageButton>(R.id.btn_tiktok)?.setOnClickListener {
            openUrl(getString(R.string.tiktok_url))
        }

        findViewById<ImageButton>(R.id.btn_facebook)?.setOnClickListener {
            openUrl(getString(R.string.facebook_url))
        }

        findViewById<ImageButton>(R.id.btn_share)?.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Check out AI Photo Studio: https://aiphotostudio.co")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }

        findViewById<TextView>(R.id.footer_gallery)?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<TextView>(R.id.footer_contact)?.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:${getString(R.string.owner_email)}".toUri()
            }
            startActivity(Intent.createChooser(emailIntent, "Send Email"))
        }

        findViewById<TextView>(R.id.footer_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<TextView>(R.id.footer_privacy)?.setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.privacy_policy))
                    .putExtra(WebPageActivity.EXTRA_URL, "https://aiphotostudio.co/privacy")
            )
        }

        findViewById<TextView>(R.id.footer_terms)?.setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening URL", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        val path = data?.path
        if (data != null && (path == "/auth/callback" || path == "/auth/callback/")) {
            webView.loadUrl(data.toString())
        } else {
            webView.loadUrl("https://aiphotostudio.co")
        }
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        btnAuthAction?.text = if (user != null) getString(R.string.sign_out) else getString(R.string.sign_in)
        btnSignUp?.visibility = if (user != null) View.GONE else View.VISIBLE

        if (user != null) {
            tvAuthStatus?.text = getString(R.string.signed_in_status)
            tvAuthStatus?.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            tvAuthStatus?.text = getString(R.string.sign_in_now)
            tvAuthStatus?.setTextColor("#FF4444".toColorInt())
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

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (HTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun processBlob(base64Data: String) {
                runOnUiThread {
                    lastCapturedBase64 = base64Data
                    llImageActions?.visibility = View.VISIBLE
                }
            }
        }, "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (
                    url.contains("aiphotostudio.co") || url.contains("accounts.google") ||
                    url.contains("facebook.com") || url.contains("firebase")
                ) false else {
                    openUrl(url)
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobBridge()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                showSourceDialog()
                return true
            }
        }
    }

    private fun injectBlobBridge() {
        val js = """
            javascript:(function() {
              if (window.__AI_BG_BRIDGE_INSTALLED__) return;
              window.__AI_BG_BRIDGE_INSTALLED__ = true;
              function sendBlobToAndroid(blob) {
                if (!blob) return;
                var reader = new FileReader();
                reader.onloadend = function() {
                  if (typeof reader.result === 'string' && reader.result.indexOf('data:image') === 0) {
                    AndroidInterface.processBlob(reader.result);
                  }
                };
                reader.readAsDataURL(blob);
              }
              var originalCreateObjectURL = URL.createObjectURL;
              URL.createObjectURL = function(blob) {
                if (blob && blob.size > 2000) sendBlobToAndroid(blob);
                return originalCreateObjectURL.call(URL, blob);
              };
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun saveToInternalGallery(base64Data: String) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: throw Exception("Decode error")
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            val galleryDir = File(filesDir, "saved_images")
            if (!galleryDir.exists()) galleryDir.mkdirs()
            
            val internalFile = File(galleryDir, fileName)
            FileOutputStream(internalFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Toast.makeText(this, "Saved to App Gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadToDevice(base64Data: String) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: throw Exception("Decode error")
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Background Remover")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore insert failed")
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Background Remover")
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }

            Toast.makeText(this, "Downloaded to Device", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_image_source))
            .setItems(options) { _, which -> if (which == 0) launchCamera() else launchGallery() }
            .setOnCancelListener { filePathCallback?.onReceiveValue(null); filePathCallback = null }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIPhotoStudio")
            if (!directory.exists()) directory.mkdirs()
            val imageFile = File(directory, "temp_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
            cameraImageUri = uri
            takePicture.launch(uri)
        } else {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) requestPermissionsLauncher.launch(toRequest.toTypedArray())
    }
}
