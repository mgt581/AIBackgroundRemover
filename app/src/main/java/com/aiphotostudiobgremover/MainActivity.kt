package com.aiphotostudiobgremover

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aiphotostudiobgremover.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val BASE_URL = "https://aiphotostudio.co.uk"

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Permission launcher for Camera
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted! Tap the upload button again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    // Handles the result from either the Gallery or the Camera
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data: Intent? = result.data
        val results: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            if (data?.data != null || data?.clipData != null) {
                // User picked a file from the gallery
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            } else {
                // User took a photo with the camera
                cameraImageUri?.let { arrayOf(it) }
            }
        } else {
            null
        }

        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backgroundWebView?.let { webView ->
            setupWebView(webView)
            setupOnBackPressed(webView)
        }
        setupButtons()
    }

    private fun setupWebView(webView: WebView) {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            addJavascriptInterface(WebAppInterface(), "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // 1. Check Camera Permission first
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        return false
                    }

                    // 2. Reset any previous callback
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    // 3. Create Intent to take a photo
                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (takePictureIntent.resolveActivity(packageManager) != null) {
                        val photoFile: File? = try { createCapturedImageFile() } catch (ex: IOException) { null }
                        photoFile?.let {
                            cameraImageUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${applicationContext.packageName}.fileprovider",
                                it
                            )
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                        }
                    }

                    // 4. Create Intent to pick files/gallery
                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }

                    // 5. Combine them into a Chooser
                    val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                        putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                        putExtra(Intent.EXTRA_TITLE, "Select Action")
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
                    }

                    try {
                        fileChooserLauncher.launch(chooserIntent)
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        return false
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.contains("google.com/accounts") || url.contains("facebook.com") ||
                        url.contains("whatsapp.com") || url.contains("tiktok.com")) {
                        openUrl(url)
                        return true
                    }
                    return when {
                        url.contains("gallery.html") -> {
                            startActivity(Intent(this@MainActivity, GalleryActivity::class.java)); true
                        }
                        url.contains("signin.html") || url.contains("login") -> {
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java)); true
                        }
                        url.contains("settings") -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java)); true
                        }
                        else -> false
                    }
                }
            }

            setDownloadListener { url, _, _, _, _ -> downloadAndSaveImage(url) }
            loadUrl(BASE_URL)
        }
    }

    @Throws(IOException::class)
    private fun createCapturedImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun setupButtons() {
        binding.footerBtnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
        binding.footerBtnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnHeaderLogin?.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        binding.btnSignUp?.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        binding.btnWhatsapp.setOnClickListener { openUrl("https://wa.me/447459142721") }
        binding.btnTiktok.setOnClickListener { openUrl("https://tiktok.com/@aiphotostudio") }
        binding.btnFacebook.setOnClickListener { openUrl("https://facebook.com/aiphotostudio") }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No application found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOnBackPressed(webView: WebView) {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun saveImageToDevice(imageUrl: String) {
            runOnUiThread { downloadAndSaveImage(imageUrl) }
        }
    }

    private fun downloadAndSaveImage(url: String) {
        Glide.with(this).asBitmap().load(url).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                saveBitmapToGallery(resource)
            }
            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
        })
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val savedImageURL = MediaStore.Images.Media.insertImage(
            contentResolver, bitmap, "AI_Photo_${System.currentTimeMillis()}", "Downloaded from AI Photo Studio"
        )
        val message = if (savedImageURL != null) "Saved to Gallery!" else "Failed to save image"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

