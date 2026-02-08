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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
    private lateinit var btnGallery: Button
    private lateinit var btnSignUp: Button
    private lateinit var tvAuthStatus: TextView
    
    private lateinit var btnDayPass: Button
    private lateinit var btnMonthly: Button
    private lateinit var btnYearly: Button

    private lateinit var fabSave: ExtendedFloatingActionButton

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
            
            btnDayPass = findViewById(R.id.btn_day_pass)
            btnMonthly = findViewById(R.id.btn_monthly)
            btnYearly = findViewById(R.id.btn_yearly)

            fabSave = findViewById(R.id.fab_save)

            setupClickListeners()
            updateHeaderUi()
            setupWebView()
            checkAndRequestPermissions()

            if (savedInstanceState == null) {
                handleIntent(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    private fun setupClickListeners() {
        fabSave.setOnClickListener {
            lastCapturedBase64?.let { saveImageToGallery(it) } ?: run {
                Toast.makeText(this, "No image to save yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnAuthAction.setOnClickListener {
            if (auth.currentUser != null) signOut() else startActivity(Intent(this, LoginActivity::class.java))
        }

        btnHeaderSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        btnSignUp.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }

        val paymentListener = View.OnClickListener {
            Toast.makeText(this, "Payment integration coming soon!", Toast.LENGTH_SHORT).show()
        }
        btnDayPass.setOnClickListener(paymentListener)
        btnMonthly.setOnClickListener(paymentListener)
        btnYearly.setOnClickListener(paymentListener)

        // Footer Listeners
        findViewById<ImageButton>(R.id.btn_fb).setOnClickListener { openUrl(getString(R.string.facebook_url)) }
        findViewById<ImageButton>(R.id.btn_wa).setOnClickListener { openUrl(getString(R.string.whatsapp_url)) }
        findViewById<ImageButton>(R.id.btn_tiktok).setOnClickListener { openUrl(getString(R.string.tiktok_url)) }
        
        findViewById<Button>(R.id.btn_footer_gallery).setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        findViewById<Button>(R.id.btn_footer_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.btn_footer_contact).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:" + getString(R.string.owner_email)) }
            startActivity(intent)
        }
        
        findViewById<Button>(R.id.btn_footer_mpa).setOnClickListener { openUrl(getString(R.string.mpa_url)) }

        findViewById<Button>(R.id.btn_privacy).setOnClickListener { openUrl("https://aiphotostudio.co/privacy") }
        findViewById<Button>(R.id.btn_terms).setOnClickListener { openUrl("https://aiphotostudio.co/terms") }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        btnAuthAction.text = if (user != null) getString(R.string.sign_out) else getString(R.string.sign_in)
        btnSignUp.visibility = if (user != null) View.GONE else View.VISIBLE
        
        if (user != null) {
            tvAuthStatus.text = getString(R.string.signed_in_status)
            tvAuthStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            tvAuthStatus.text = getString(R.string.sign_in_now)
            tvAuthStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
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
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (HTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        }

        webView.addJavascriptInterface(object {
            @Keep
            @JavascriptInterface
            fun processBlob(base64Data: String) {
                lastCapturedBase64 = base64Data
                runOnUiThread {
                    saveImageToGallery(base64Data, silent = true)
                    fabSave.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Image processed and saved to gallery!", Toast.LENGTH_LONG).show()
                }
            }
        }, "AndroidInterface")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.contains("aiphotostudio.co") || url.contains("accounts.google") ||
                    url.contains("facebook.com") || url.contains("firebase")
                ) false else {
                    openUrl(url)
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBlobBridge()
                webView.postDelayed({ injectBlobBridge() }, 1200)
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
        
        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("data:")) {
                lastCapturedBase64 = url
                runOnUiThread {
                    saveImageToGallery(url, silent = true)
                    fabSave.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Image processed and saved to gallery!", Toast.LENGTH_LONG).show()
                }
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
              window.addEventListener('click', function(e) {
                var link = e.target.closest('a');
                if (link && link.href && link.href.indexOf('blob:') === 0) {
                  e.preventDefault();
                  var xhr = new XMLHttpRequest();
                  xhr.open('GET', link.href, true);
                  xhr.responseType = 'blob';
                  xhr.onload = function() { sendBlobToAndroid(xhr.response); };
                  xhr.send();
                }
              }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun saveImageToGallery(base64Data: String, silent: Boolean = false) {
        try {
            val base64String = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: throw Exception("Decode error")
            val fileName = "AI_Studio_${System.currentTimeMillis()}.png"

            val galleryDir = File(filesDir, "saved_images")
            if (!galleryDir.exists()) galleryDir.mkdirs()
            val internalFile = File(galleryDir, fileName)
            FileOutputStream(internalFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            if (!silent) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Background Remover")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw Exception("Insert error")
                    contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } else {
                    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AI Background Remover")
                    if (!directory.exists()) directory.mkdirs()
                    val file = File(directory, fileName)
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                }
                runOnUiThread { Toast.makeText(this, getString(R.string.saved_to_gallery), Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            if (!silent) runOnUiThread { Toast.makeText(this, getString(R.string.save_failed, e.message), Toast.LENGTH_SHORT).show() }
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

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) requestPermissionsLauncher.launch(toRequest.toTypedArray())
    }
}
