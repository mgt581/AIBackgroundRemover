package com.aiphotostudiobgremover

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val BASE_URL = "https://aiphotostudio.co.uk/?platform=android"
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Handles Camera Permission request
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    // Handles the result from the File Chooser (Camera or Gallery)
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val results = if (result.resultCode == RESULT_OK) {
            if (result.data?.data != null || result.data?.clipData != null) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else {
                cameraImageUri?.let { arrayOf(it) }
            }
        } else null

        // Critical: Always call onReceiveValue to prevent the WebView from hanging
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backgroundWebView?.let {
            setupWebView(it)
            setupOnBackPressed(it)
        }
        setupButtons()
    }

    private fun setupWebView(webView: WebView) {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile AIPhotoStudioApp Safari/537.36"
            }

            addJavascriptInterface(WebAppInterface(), "AndroidBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView?, fpc: ValueCallback<Array<Uri>>?, fcp: FileChooserParams?): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = fpc

                    val options = arrayOf(
                        getString(R.string.take_photo),
                        getString(R.string.choose_photo_gallery),
                        getString(R.string.choose_file),
                        getString(R.string.cancel)
                    )

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.select_option)
                        .setItems(options) { dialog, which ->
                            when (which) {
                                0 -> { // Take Photo
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        launchCamera()
                                    }
                                }
                                1 -> { // Choose Photo (Gallery)
                                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                    fileChooserLauncher.launch(intent)
                                }
                                2 -> { // Choose File
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf", "text/plain")) // User said "choose file", but usually it's for photos in this app context. I'll allow images and some common types.
                                    }
                                    fileChooserLauncher.launch(intent)
                                }
                                else -> { // Cancel
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    dialog.dismiss()
                                }
                            }
                        }
                        .setOnCancelListener {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                        }
                        .show()

                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // Inject JS to mark the HTML as being in the Android app 
                    // This allows the website's CSS (e.g., html.android-app .pricing-row) to hide elements
                    val jsMarkApp = "document.documentElement.classList.add('android-app');"
                    view?.evaluateJavascript(jsMarkApp, null)

                    // Fallback CSS injection to hide common payment/pricing/watermark patterns
                    val css = """
                        .payment-button, .buy-now, .pricing-section, .subscription-btn, .pricing-row, #upgradeMsg,
                        [class*='payment'], [id*='payment'], [class*='pricing'], [id*='pricing'],
                        .watermark, [class*='watermark'], [id*='watermark'], .branded-watermark,
                        .logo-overlay, .upgrade-overlay, .premium-badge, .remove-watermark-btn,
                        [class*='upgrade'], [id*='upgrade'], [class*='premium'], [id*='premium'] { 
                            display: none !important; 
                        }
                    """.trimIndent()
                    
                    val jsStyle = "var style = document.createElement('style');" +
                            "style.innerHTML = '$css';" +
                            "document.head.appendChild(style);"
                    
                    view?.evaluateJavascript(jsStyle, null)
                }

                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val url = r?.url.toString()
                    return when {
                        url.contains("gallery.html") -> {
                            startActivity(Intent(this@MainActivity, GalleryActivity::class.java))
                            true
                        }
                        url.contains("login") -> {
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            true
                        }
                        else -> false
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                downloadAndSaveImage(url, userAgent, contentDisposition, mimetype, contentLength)
            }
            loadUrl(BASE_URL)
        }
    }

    private fun setupButtons() {
        binding.footerBtnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        binding.footerBtnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun launchCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        
        // Ensure there is a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera app found to take photos.", Toast.LENGTH_SHORT).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            return
        }

        val photoFile = try {
            createCapturedImageFile()
        } catch (e: IOException) {
            null
        }

        photoFile?.let {
            cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
            fileChooserLauncher.launch(takePictureIntent)
        } ?: run {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    @Throws(IOException::class)
    private fun createCapturedImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun downloadAndSaveImage(url: String, userAgent: String? = null, contentDisposition: String? = null, mimetype: String? = null, contentLength: Long = 0) {
        if (url.startsWith("blob:")) {
            // Handle Blob URL via JS injection to convert to Base64
            val js = """
                (function() {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', '$url', true);
                    xhr.responseType = 'blob';
                    xhr.onload = function(e) {
                        if (this.status == 200) {
                            var blob = this.response;
                            var reader = new FileReader();
                            reader.readAsDataURL(blob);
                            reader.onloadend = function() {
                                var base64data = reader.result;
                                AndroidBridge.saveImageToDevice(base64data);
                            }
                        }
                    };
                    xhr.send();
                })();
            """.trimIndent()
            binding.backgroundWebView?.evaluateJavascript(js, null)
            return
        }

        if (url.startsWith("data:image")) {
            // Handle Base64 Data URI
            try {
                val base64Data = if (url.contains(",")) {
                    url.substring(url.indexOf(",") + 1)
                } else {
                    url
                }
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (decodedBitmap != null) {
                    saveBitmapToGallery(decodedBitmap)
                } else {
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error processing data URI: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // For regular URLs, use DownloadManager as requested
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
            
            request.setMimeType(mimetype)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription("Downloading image...")
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "AIPhotoStudio/$filename")
            
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
            
            // Note: DownloadManager handles saving to public directory. 
            // On modern Android, files in public directories are automatically scanned by MediaStore.
        } catch (e: Exception) {
            // Fallback to Glide if DownloadManager fails (e.g. invalid URL for DM)
            Glide.with(this).asBitmap().load(url).into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    saveBitmapToGallery(resource)
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Toast.makeText(this@MainActivity, "failed to download image", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        // For API <= 28, we need WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // We should ideally request it here, but let's at least warn and return for now, 
                // as this is a background process from a JS call usually.
                // Or better, trigger a standard request if it's missing.
                Toast.makeText(this, "Storage permission is required to save photos.", Toast.LENGTH_SHORT).show()
                // You might want to register a launcher for this too if it's frequent.
                return
            }
        }

        val filename = "AI_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        val contentResolver = contentResolver
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIPhotoStudio")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val studioDir = File(imagesDir, "AIPhotoStudio")
                if (!studioDir.exists()) studioDir.mkdirs()
                val image = File(studioDir, filename)
                fos = java.io.FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageUri != null) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    contentResolver.update(imageUri, contentValues, null, null)
                }
                runOnUiThread {
                    Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOnBackPressed(webView: WebView) {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun saveImageToDevice(url: String) {
            runOnUiThread { downloadAndSaveImage(url) }
        }
    }
}









