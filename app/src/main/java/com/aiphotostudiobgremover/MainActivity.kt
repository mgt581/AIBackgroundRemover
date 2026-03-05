package com.aiphotostudio.bgremover

import android.Manifest
import android.app.DownloadManager
import android.content.ContentValues
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
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.aiphotostudio.bgremover.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val baseUrl = "https://aiphotostudio.co.uk/?platform=android"
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

        binding.backgroundWebView?.let { webView ->
            setupWebView(webView)
            setupOnBackPressed(webView)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile AIPhotoStudioApp Safari/537.36"
            }

            addJavascriptInterface(WebAppInterface(), "AndroidBridge")
            addJavascriptInterface(AndroidSaveBridge(this@MainActivity), "AndroidSave")

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
                    // Early injection to capture Blobs and set markers
                    val jsEarly = """
                        (function() {
                            window.__AIPS_IS_ANDROID_APP__ = true;
                            window.isAndroidApp = true;
                            window.isMobileApp = true;
                            document.documentElement.classList.add('android-app');
                            
                            window.blobCache = window.blobCache || {};
                            if (!window.urlPatched) {
                                const originalCreate = URL.createObjectURL;
                                URL.createObjectURL = function(obj) {
                                    const url = originalCreate.call(URL, obj);
                                    if (obj instanceof Blob) {
                                        const reader = new FileReader();
                                        reader.onloadend = function() {
                                            window.blobCache[url] = reader.result;
                                        };
                                        reader.readAsDataURL(obj);
                                    }
                                    return url;
                                };
                                window.urlPatched = true;
                            }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsEarly, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

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
                        }
                    """.trimIndent()

                    val jsInjection = """
                        (function() {
                          try {
                            window.__AIPS_IS_ANDROID_APP__ = true;
                            document.documentElement.classList.add('android-app');
                            window.isAndroidApp = true;
                            window.isMobileApp = true;

                            var style = document.getElementById('android-app-styles');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'android-app-styles';
                                document.head.appendChild(style);
                            }
                            style.innerHTML = ${JSONObject.quote(css)};

                            var observer = new MutationObserver(function(mutations) {
                                if (!document.getElementById('android-app-styles')) {
                                    document.head.appendChild(style);
                                }
                            });
                            observer.observe(document.body, { childList: true, subtree: true });
                            
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
                    return false
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                val ua = userAgent ?: settings.userAgentString
                downloadAndSaveImage(url, ua, contentDisposition, mimetype)
            }

            loadUrl(baseUrl)
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
        } catch (_: IOException) {
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
        mimetype: String? = null
    ) {
        if (url.startsWith("blob:")) {
            val js = """
                (async function() {
                  try {
                    const blobUrl = '$url';
                    
                    const blobToBase64 = (blob) => {
                      return new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onloadend = () => resolve(reader.result);
                        reader.onerror = () => reject("FileReader failed");
                        reader.readAsDataURL(blob);
                      });
                    };

                    // 1. Check Blob Cache
                    if (window.blobCache && window.blobCache[blobUrl]) {
                      const dataUrl = window.blobCache[blobUrl];
                      const base64 = dataUrl.split(',')[1];
                      const mime = dataUrl.split(';')[0].split(':')[1];
                      const filename = 'AIPStudio_' + Date.now() + '.png';
                      if (window.AndroidSave) {
                        window.AndroidSave.saveBase64Image(base64, filename, mime);
                      } else {
                        AndroidBridge.saveImageToDevice(dataUrl);
                      }
                      return;
                    }

                    // 2. Try DOM extraction
                    const elements = [...document.querySelectorAll('canvas'), ...document.querySelectorAll('img')];
                    for (const el of elements) {
                      try {
                        const isMatch = el.src === blobUrl || (el.tagName === 'CANVAS' && el.width > 100);
                        if (isMatch) {
                          const canvas = el.tagName === 'CANVAS' ? el : document.createElement('canvas');
                          if (el.tagName !== 'CANVAS') {
                            canvas.width = el.naturalWidth || el.width;
                            canvas.height = el.naturalHeight || el.height;
                            canvas.getContext('2d').drawImage(el, 0, 0);
                          }
                          const dataUrl = canvas.toDataURL('image/png');
                          if (dataUrl.length > 1000) {
                            const base64 = dataUrl.split(',')[1];
                            const filename = 'AIPStudio_' + Date.now() + '.png';
                            if (window.AndroidSave) {
                              window.AndroidSave.saveBase64Image(base64, filename, 'image/png');
                            } else {
                              AndroidBridge.saveImageToDevice(dataUrl);
                            }
                            return;
                          }
                        }
                      } catch (e) {}
                    }

                    // 3. Fallback: Fetch blob
                    const res = await fetch(blobUrl);
                    const blob = await res.blob();
                    const dataUrl = await blobToBase64(blob);
                    const base64 = dataUrl.split(',')[1];
                    const mime = blob.type || 'image/png';
                    const filename = 'AIPStudio_' + Date.now() + '.png';
                    if (window.AndroidSave) {
                      window.AndroidSave.saveBase64Image(base64, filename, mime);
                    } else {
                      AndroidBridge.saveImageToDevice(dataUrl);
                    }
                  } catch (e) {
                    AndroidBridge.saveImageToDevice("ERROR: Security block or revoked URL - " + e.toString());
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
                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
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
            val request = DownloadManager.Request(url.toUri())
            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)

            request.setMimeType(mimetype)
            val ua = userAgent ?: "Mozilla/5.0"
            request.addRequestHeader("User-Agent", ua)

            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) {
                request.addRequestHeader("Cookie", cookie)
            }

            request.addRequestHeader("Referer", baseUrl)
            request.addRequestHeader("X-Platform", "android")
            request.addRequestHeader("X-App-Id", packageName)

            request.setDescription("Downloading image...")
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "AIPhotoStudio/$filename")

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
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
        val filename = "AI_${System.currentTimeMillis()}.png"
        val resolver = contentResolver
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Photo Studio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            runOnUiThread {
                Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
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
        @Suppress("unused")
        fun saveImageToDevice(url: String) {
            runOnUiThread { downloadAndSaveImage(url) }
        }
    }
}
