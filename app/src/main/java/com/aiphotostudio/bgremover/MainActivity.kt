package com.aiphotostudio.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private lateinit var auth: FirebaseAuth

    private lateinit var btnAuthAction: Button
    private lateinit var btnHeaderGallery: Button
    private lateinit var btnHeaderSignup: Button
    private lateinit var btnHeaderSettings: Button
    private lateinit var tvSignedInStatus: TextView

    private lateinit var btnFooterTerms: Button
    private lateinit var btnFooterPrivacy: Button

    // Prevent injecting the same JS multiple times
    private var bridgeInjectedForUrl: String? = null

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
        if (permissions[Manifest.permission.CAMERA] == true) {
            // Permission granted
        } else {
            Toast.makeText(this, "Camera permission required for photos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        try {
            setContentView(R.layout.activity_main)

            webView = findViewById(R.id.webView)
            btnAuthAction = findViewById(R.id.btn_auth_action)
            btnHeaderGallery = findViewById(R.id.btn_header_gallery)
            btnHeaderSignup = findViewById(R.id.btn_header_signup)
            btnHeaderSettings = findViewById(R.id.btn_header_settings)
            tvSignedInStatus = findViewById(R.id.tv_signed_in_status)

            btnFooterTerms = findViewById(R.id.btn_footer_terms)
            btnFooterPrivacy = findViewById(R.id.btn_footer_privacy)

            updateHeaderUi()

            setupWebView()
            setupDownloadListener()
            checkAndRequestPermissions()

            btnAuthAction.setOnClickListener {
                if (auth.currentUser != null) {
                    signOut()
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }

            btnHeaderGallery.setOnClickListener {
                startActivity(Intent(this, GalleryActivity::class.java))
            }

            btnHeaderSignup.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            btnHeaderSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            btnFooterTerms.setOnClickListener {
                webView.loadUrl("https://aiphotostudio.co/terms")
            }

            btnFooterPrivacy.setOnClickListener {
                webView.loadUrl("https://aiphotostudio.co/privacy")
            }

            if (savedInstanceState == null) {
                webView.loadUrl("https://aiphotostudio.co")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    override fun onResume() {
        super.onResume()
        updateHeaderUi()
    }

    private fun updateHeaderUi() {
        val user = auth.currentUser
        if (user != null) {
            btnAuthAction.text = "Sign Out"
            tvSignedInStatus.text = "✓ ${user.email?.take(10) ?: "User"}"
            tvSignedInStatus.visibility = View.VISIBLE
            btnHeaderSignup.visibility = View.GONE
        } else {
            btnAuthAction.text = "Sign In"
            tvSignedInStatus.visibility = View.GONE
            btnHeaderSignup.visibility = View.VISIBLE
        }
    }

    private fun signOut() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(this, gso).signOut().addOnCompleteListener {
            updateHeaderUi()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            @Suppress("DEPRECATION")
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }

        // Add Javascript Interface to handle Blobs BEFORE loadUrl
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun processBlob(base64Data: String) {
                // IMPORTANT: Do not do heavy work on UI thread
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Processing download...", Toast.LENGTH_SHORT).show()
                }

                Thread {
                    saveImageToGallery(base64Data)
                }.start()
            }
        }, "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Avoid re-injecting repeatedly for same url
                if (url != null && bridgeInjectedForUrl == url) return
                bridgeInjectedForUrl = url

                injectBlobBridge()
            }

            private fun handleUrl(url: String): Boolean {
                return if (url.contains("aiphotostudio.co") ||
                    url.contains("accounts.google") ||
                    url.contains("facebook.com") ||
                    url.contains("firebase.com", ignoreCase = true)
                ) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        true
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to open URL", e)
                        false
                    }
                }
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

    /**
     * This injection is MUCH more reliable than only listening for <a href="blob:..."> clicks.
     * It hooks URL.createObjectURL(blob) and also keeps the click-capture fallback.
     */
    private fun injectBlobBridge() {
        val js = """
            javascript:(function() {
              try {
                if (window.__AI_BG_BRIDGE_INSTALLED__) return;
                window.__AI_BG_BRIDGE_INSTALLED__ = true;

                // Hook createObjectURL so we capture blobs regardless of UI element type.
                var originalCreateObjectURL = URL.createObjectURL;
                URL.createObjectURL = function(blob) {
                  try {
                    if (blob && (blob.type || '').indexOf('image') !== -1) {
                      var reader = new FileReader();
                      reader.onloadend = function() {
                        try { AndroidInterface.processBlob(reader.result); } catch(e) {}
                      };
                      reader.readAsDataURL(blob);
                    }
                  } catch(e) {}
                  return originalCreateObjectURL.call(URL, blob);
                };

                // Fallback: intercept clicks on anchors with blob href
                window.addEventListener('click', function(e) {
                  var link = e.target && e.target.closest ? e.target.closest('a') : null;
                  if (link && link.href && link.href.indexOf('blob:') === 0) {
                    e.preventDefault();
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', link.href, true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                      try {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                          try { AndroidInterface.processBlob(reader.result); } catch(e) {}
                        };
                        reader.readAsDataURL(xhr.response);
                      } catch(e) {}
                    };
                    xhr.send();
                  }
                }, true);

              } catch(err) {
                // swallow errors to avoid breaking the page
              }
            })();
        """.trimIndent()

        webView.loadUrl(js)
    }

    private fun saveImageToGallery(base64Data: String) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)

            // Decode bitmap (optional but useful for ensuring valid PNG)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: throw Exception("Failed to decode image data")

            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/AI Background Remover"
                    )
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Failed to create MediaStore entry")

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                } ?: throw Exception("Failed to open output stream")

                runOnUiThread {
                    Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                }

            } else {
                // Older versions
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "AI Background Remover"
                )
                if (!directory.exists()) directory.mkdirs()

                val file = File(directory, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.absolutePath),
                    arrayOf("image/png"),
                    null
                )

                runOnUiThread {
                    Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save image", e)
            runOnUiThread {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                if (which == 0) launchCamera() else launchGallery()
            }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "AIPhotoStudio")
            if (!directory.exists()) directory.mkdirs()
            val imageFile = File(directory, "temp_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "com.aiphotostudio.bgremover.fileprovider", imageFile)
            cameraImageUri = uri
            takePicture.launch(uri)
        } else {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun launchGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            when {
                url.startsWith("data:") -> {
                    // data:image/png;base64,...
                    Thread { saveImageToGallery(url) }.start()
                }
                url.startsWith("blob:") -> {
                    // Blobs are handled by the JavascriptInterface + injected script
                    Log.d("MainActivity", "Blob download detected, handling via JS interface")
                    Toast.makeText(this, "Preparing save…", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    try {
                        val request = DownloadManager.Request(url.toUri()).apply {
                            setMimeType(mimetype)
                            addRequestHeader("User-Agent", userAgent)
                            setTitle("AI Background Remover Download")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                "processed_image_${System.currentTimeMillis()}.png"
                            )
                        }
                        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Download failed", e)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.CAMERA)

        // WRITE_EXTERNAL_STORAGE only required pre-Android 10 for saving to public dirs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }
    }
}