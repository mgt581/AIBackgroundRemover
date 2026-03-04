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
import org.json.JSONObject
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
                                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf", "text/plain"))
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
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Inject markers as early as possible
                    val jsEarly = "window.isAndroidApp = true; window.isMobileApp = true; document.documentElement.classList.add('android-app');"
                    view?.evaluateJavascript(jsEarly, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // Inject JS to mark the HTML as being in the Android app and hide elements continuously
                    val css = """
                        .payment-button, .buy-now, .pricing-section, .subscription-btn, .pricing-row, #upgradeMsg,
                        [class*='payment'], [id*='payment'], [class*='pricing'], [id*='pricing'],
                        [class*='stripe'], [id*='stripe'], .stripe-payment-provider, .StripeElement,
                        iframe[src*='stripe'], .stripe-checkout, .pay-button,
                        .watermark, [class*='watermark'], [id*='watermark'], .branded-watermark,
                        .logo-overlay, .upgrade-overlay, .premium-badge, .remove-watermark-btn,
                        [class*='upgrade'], [id*='upgrade'], [class*='premium'], [id*='premium'],
                        .checkout-container, .checkout-button, .billing-section, .pricing-plan,
                        .floating-watermark, .img-watermark, .overlay-watermark, [src*='watermark'] { 
                            display: none !important; 
                            visibility: hidden !important;
                            opacity: 0 !important;
                            height: 0 !important;
                            width: 0 !important;
                            pointer-events: none !important;
                            position: absolute !important;
                            top: -9999px !important;
                        }
                    """.trimIndent()

                    val jsInjection = """
                        (function() {
                          try {
                            // 1. Mark as app
                            document.documentElement.classList.add('android-app');
                            window.isAndroidApp = true;
                            window.isMobileApp = true;

                            // 2. Inject CSS
                            var style = document.getElementById('android-app-styles');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'android-app-styles';
                                document.head.appendChild(style);
                            }
                            style.innerHTML = ${JSONObject.quote(css)};

                            // 3. Continuous Cleanup (MutationObserver)
                            var observer = new MutationObserver(function(mutations) {
                                mutations.forEach(function(mutation) {
                                    if (mutation.addedNodes.length) {
                                        // Ensure styles are still there
                                        if (!document.getElementById('android-app-styles')) {
                                            document.head.appendChild(style);
                                        }
                                    }
                                });
                            });
                            observer.observe(document.body, { childList: true, subtree: true });
                            
                            // 4. Manual sweep for any tricky elements
                            function hideTricky() {
                                var selectors = ['.watermark', '[class*="watermark"]', '[id*="watermark"]', '.stripe-payment-provider', '.stripe-checkout', 'iframe[src*="stripe"]'];
                                selectors.forEach(function(s) {
                                    var elms = document.querySelectorAll(s);
                                    elms.forEach(function(el) { el.style.display = 'none'; el.style.visibility = 'hidden'; });
                                });
                            }
                            setInterval(hideTricky, 1000);
                          } catch (e) {}
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(jsInjection, null)
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
                val ua = userAgent ?: settings.userAgentString
                downloadAndSaveImage(url, ua, contentDisposition, mimetype, contentLength)
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
        binding.btnHeaderLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun launchCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

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

    private fun downloadAndSaveImage(
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimetype: String? = null,
        contentLength: Long = 0
    ) {
        if (url.startsWith("blob:")) {
            val js = """
                (async function() {
                  try {
                    const res = await fetch('$url');
                    const blob = await res.blob();
                    const reader = new FileReader();
                    reader.onloadend = function() {
                      AndroidBridge.saveImageToDevice(reader.result);
                    };
                    reader.readAsDataURL(blob);
                  } catch (e) {
                    AndroidBridge.saveImageToDevice("ERROR:" + e.toString());
                  }
                })();
            """.trimIndent()
            binding.backgroundWebView?.evaluateJavascript(js, null)
            return
        }

        if (url.startsWith("data:image")) {
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

        if (url.startsWith("ERROR:")) {
            Toast.makeText(this, "WebView download error: ${url.removePrefix("ERROR:")}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)

            request.setMimeType(mimetype)
            val ua = userAgent ?: "Mozilla/5.0"
            request.addRequestHeader("User-Agent", ua)

            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) {
                request.addRequestHeader("Cookie", cookie)
            }

            request.addRequestHeader("Referer", BASE_URL)
            request.addRequestHeader("X-Platform", "android")
            request.addRequestHeader("X-App-Id", packageName)

            request.setDescription("Downloading image...")
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "AIPhotoStudio/$filename")

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission is required to save photos.", Toast.LENGTH_SHORT).show()
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
