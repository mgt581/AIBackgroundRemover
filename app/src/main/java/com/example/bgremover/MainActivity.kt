package com.example.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Photo Picker Launcher (Gallery)
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        Log.d("MainActivity", "Photo Picker result: $uri")
        filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        filePathCallback = null
    }

    // Camera Launcher
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        Log.d("MainActivity", "Camera result: $success")
        filePathCallback?.onReceiveValue(if (success && cameraImageUri != null) arrayOf(cameraImageUri!!) else null)
        filePathCallback = null
    }

    // Launcher for runtime permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
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
                when (which) {
                    0 -> launchCamera()
                    1 -> launchGallery()
                }
            }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_image_${System.currentTimeMillis()}.jpg")
                cameraImageUri = FileProvider.getUriForFile(this, "com.aiphotostudio.bgremover.provider", imageFile)
                takePicture.launch(cameraImageUri)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch camera", e)
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun launchGallery() {
        try {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to launch Photo Picker", e)
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
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