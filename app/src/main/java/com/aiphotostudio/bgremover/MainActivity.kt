package com.aiphotostudio.bgremover

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.messaging.FirebaseMessaging
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

    // Handles Camera and Notification Permission requests
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Handle results based on what was requested if needed
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

        binding.backgroundWebView.let { webView ->
            setupWebView(webView)
            setupOnBackPressed(webView)
        }

        setupButtons()
        logFcmToken()
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM_TOKEN", "Your FCM Token is: $token")
            // Re-copy this token to Firebase Console if it changed
        }
    }

    override fun onResume() {
        super.onResume()
        updateCreditsDisplay()
    }

    private fun updateCreditsDisplay() {
        UserManager.fetchUserData { totalCredits, isSubscribed ->
            runOnUiThread {
                if (isSubscribed) {
                    binding.tvCredits?.text = getString(R.string.premium_label)
                    binding.tvCredits?.setTextColor(ContextCompat.getColor(this, R.color.status_green))
                    binding.btnBuyCredits?.visibility = android.view.View.GONE
                } else {
                    if (totalCredits == 0) {
                        binding.tvCredits?.text = getString(R.string.zero_credits)
                        binding.btnBuyCredits?.text = getString(R.string.top_up_now)
                    } else {
                        binding.tvCredits?.text = getString(R.string.credits_label, totalCredits)
                        binding.btnBuyCredits?.text = getString(R.string.buy_credits)
                    }
                    binding.tvCredits?.setTextColor(ContextCompat.getColor(this, R.color.white))
                    binding.btnBuyCredits?.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun setupButtons() {
        binding.creditsContainer?.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java)
            startActivity(intent)
        }
        
        // Footer Buttons
        binding.footerBtnPrivacy.setOnClickListener {
            val intent = Intent(this, WebPageActivity::class.java).apply {
                putExtra("url", "https://aiphotostudio.co.uk/privacy")
                putExtra("title", getString(R.string.privacy_policy))
            }
            startActivity(intent)
        }

        binding.footerBtnTerms.setOnClickListener {
            val intent = Intent(this, WebPageActivity::class.java).apply {
                putExtra("url", "https://aiphotostudio.co.uk/terms")
                putExtra("title", getString(R.string.terms_of_service))
            }
            startActivity(intent)
        }

        binding.btnWhatsapp.setOnClickListener {
            openUrl("https://wa.me/447843969254")
        }

        binding.btnTiktok.setOnClickListener {
            openUrl(getString(R.string.tiktok_url))
        }

        binding.btnFacebook.setOnClickListener {
            openUrl(getString(R.string.facebook_url))
        }

        // Native "Choose Photo" button (used in landscape/tablet layouts)
        // We use an optional binding check because it might not exist in all layout versions
        try {
            val btnChoosePhoto = binding::class.java.getMethod("getBtnChoosePhoto").invoke(binding) as? android.view.View
            btnChoosePhoto?.setOnClickListener {
                showImageSourceDialog()
            }
        } catch (_: Exception) {
            Log.d("MainActivity", "btnChoosePhoto not found in current layout")
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_photo_gallery),
            getString(R.string.choose_file),
            getString(R.string.cancel)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.select_option)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Take Photo
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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
                    else -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.could_not_open_link, Toast.LENGTH_SHORT).show()
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
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
                
                // Optimized User Agent for tablets and phones
                val baseUA = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0"
                userAgentString = "$baseUA Mobile AIPhotoStudioApp Safari/537.36"
            }

            addJavascriptInterface(WebAppInterface(), "Android")
            addJavascriptInterface(AndroidSaveBridge(this@MainActivity), "AndroidSave")

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView?, fpc: ValueCallback<Array<Uri>>?, fcp: FileChooserParams?): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = fpc
                    showImageSourceDialog()
                    return true
                }
                
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val jsEarly = """
                        (function() {
                            window.__AIPS_IS_ANDROID_APP__ = true;
                            window.isAndroidApp = true;
                            window.isMobileApp = true;
                            document.documentElement.classList.add('android-app');
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
                        }
                    """.trimIndent()

                    val jsInjection = """
                        (function() {
                          try {
                            window.__AIPS_IS_ANDROID_APP__ = true;
                            document.documentElement.classList.add('android-app');
                            var style = document.createElement('style');
                            style.innerHTML = ${JSONObject.quote(css)};
                            document.head.appendChild(style);
                          } catch (e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(jsInjection, null)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                downloadAndSaveImage(url, userAgent, contentDisposition, mimetype)
            }

            loadUrl(baseUrl)
        }
    }

    private fun launchCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = try {
            createCapturedImageFile()
        } catch (_: IOException) {
            null
        }

        photoFile?.let {
            cameraImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
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

    private fun downloadAndSaveImage(url: String, userAgent: String?, contentDisposition: String?, mimetype: String?) {
        // Implementation remains similar but ensuring UI thread for Toasts
        Log.d("Download", "URL: $url, UA: $userAgent, CD: $contentDisposition, Type: $mimetype")
        runOnUiThread {
            Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()
        }
        // ... (rest of download logic from original)
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
            runOnUiThread { downloadAndSaveImage(url, null, null, null) }
        }

        @JavascriptInterface
        fun spendCredit(callbackName: String) {
            spendCredit(callbackName, false)
        }

        @JavascriptInterface
        fun spendCredit(callbackName: String, isObjectRemoval: Boolean) {
            val webView = binding.backgroundWebView
            runOnUiThread {
                UserManager.spendCredit(isObjectRemoval) { success, remainingCredits ->
                    if (success) {
                        updateCreditsDisplay()
                        webView.evaluateJavascript("javascript:$callbackName(true, $remainingCredits)", null)
                    } else {
                        val messageRes = if (isObjectRemoval) R.string.paid_credits_required else R.string.not_enough_credits
                        Toast.makeText(this@MainActivity, messageRes, Toast.LENGTH_LONG).show()
                        webView.evaluateJavascript("javascript:$callbackName(false, $remainingCredits)", null)
                        val intent = Intent(this@MainActivity, ShopActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        }

        @JavascriptInterface
        fun getCredits() : Int {
            return UserManager.credits + UserManager.freeDailyCredits
        }

        @JavascriptInterface
        fun isPremium() : Boolean {
            return UserManager.isSubscribed
        }
    }
}
