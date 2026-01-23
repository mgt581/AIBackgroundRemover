package com.example.bgremover

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Handling both Gallery and Camera results
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val results = if (result.resultCode == RESULT_OK) {
            val dataUri = result.data?.data
            if (dataUri != null) {
                arrayOf(dataUri)
            } else if (cameraImageUri != null) {
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
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                openChooser(fileChooserParams)
                return true
            }
        }
    }

    private fun openChooser(params: WebChromeClient.FileChooserParams?) {
        // 1. Create Camera Intent
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_image_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.provider", imageFile)
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)

        // 2. Create Gallery/File Intent
        val selectionIntent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }

        // 3. Create Combined Chooser
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, selectionIntent)
            putExtra(Intent.EXTRA_TITLE, "Select Source")
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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