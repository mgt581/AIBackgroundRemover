package com.example.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
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
import android.webkit.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Handling both Gallery and Camera results
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("MainActivity", "FileChooser result: ${result.resultCode}")
        val results = if (result.resultCode == RESULT_OK) {
            val dataUri = result.data?.data
            if (dataUri != null) {
                Log.d("MainActivity", "Gallery URI: $dataUri")
                arrayOf(dataUri)
            } else if (cameraImageUri != null) {
                Log.d("MainActivity", "Camera URI: $cameraImageUri")
                arrayOf(cameraImageUri!!)
            } else {
                null
            }
        } else {
            null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    // Launcher for runtime permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        
        val readImagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false ||
                    permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] ?: false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }

        Log.d("MainActivity", "Permissions granted: Camera=$cameraGranted, Media=$readImagesGranted")
        
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
        // We don't necessarily want to toast here if they selected partial access
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            webView = findViewById(R.id.webView)
            val btnGallery = findViewById<Button>(R.id.btn_gallery)

            setupWebView()
            setupDownloadListener()
            checkAndRequestPermissions()

            btnGallery.setOnClickListener {
                val intent = Intent(this, GalleryActivity::class.java)
                startActivity(intent)
            }

            webView.loadUrl("https://aiphotostudio.co")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Critical Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java", ReplaceWith("shouldOverrideUrlLoading(view, request)"))
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e("MainActivity", "WebView Error: ${error?.description}")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d("MainActivity", "onShowFileChooser called")
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                try {
                    openChooser(fileChooserParams)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error opening chooser", e)
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }
        }
    }

    private fun openChooser(params: WebChromeClient.FileChooserParams?) {
        val intents = mutableListOf<Intent>()

        // 1. Camera Intent
        try {
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_image_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(this, "com.aiphotostudio.bgremover.provider", imageFile)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            intents.add(captureIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to setup camera intent", e)
        }

        // 2. Gallery Intent
        val selectionIntent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        // 3. Create Combined Chooser
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, selectionIntent)
            putExtra(Intent.EXTRA_TITLE, "Select Source")
            if (intents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
            }
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (url.startsWith("data:")) {
                handleDataUri(url)
            } else {
                try {
                    val request = DownloadManager.Request(url.toUri()).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        setDescription("Downloading image...")
                        setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
                    }
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this, "Download started. It will appear in 'My Gallery' soon.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to download image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleDataUri(dataUri: String) {
        try {
            val base64String = dataUri.substringAfter(",")
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            val fileName = "bg_remover_${System.currentTimeMillis()}.png"
            
            val directory = File(filesDir, "saved_images")
            if (!directory.exists()) directory.mkdirs()

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)

            outputStream.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "Image saved to app gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (Selected Photo Access)
            val hasFullAccess = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val hasPartialAccess = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            
            if (!hasFullAccess && !hasPartialAccess) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}