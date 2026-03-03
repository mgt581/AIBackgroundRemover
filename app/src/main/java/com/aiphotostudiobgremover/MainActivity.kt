package com.aiphotostudiobgremover

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.aiphotostudiobgremover.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val BASE_URL = "https://aiphotostudio.co.uk"

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
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            
            addJavascriptInterface(WebAppInterface(), "AndroidBridge")

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Handle Auth and Social redirects by opening in browser
                    if (url.contains("google.com/accounts") || 
                        url.contains("facebook.com") || 
                        url.contains("appleid.apple.com") ||
                        url.contains("whatsapp.com") ||
                        url.contains("tiktok.com")) {
                        openUrl(url)
                        return true
                    }

                    return when {
                        url.contains("gallery.html") -> {
                            startActivity(Intent(this@MainActivity, GalleryActivity::class.java))
                            true
                        }
                        url.contains("signin.html") || url.contains("login") -> {
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            true
                        }
                        url.contains("settings") -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            true
                        }
                        else -> false
                    }
                }
            }

            loadUrl(BASE_URL)
        }
    }

    private fun setupButtons() {
        // Shared Footer Buttons
        binding.footerBtnGallery?.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        binding.footerBtnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Header Buttons
        binding.btnHeaderLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.btnSignUp?.setOnClickListener { 
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Social Buttons - Using direct links
        binding.btnWhatsapp?.setOnClickListener { openUrl("https://wa.me/447459142721") } // Updated with example
        binding.btnTiktok?.setOnClickListener { openUrl("https://tiktok.com/@aiphotostudio") }
        binding.btnFacebook?.setOnClickListener { openUrl("https://facebook.com/aiphotostudio") }

        // Landscape specific main buttons
        binding.btnChoosePhoto?.setOnClickListener {
            // Logic for choosing photo
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No application found to open this link", Toast.LENGTH_SHORT).show()
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
        fun saveImageToDevice(imageUrl: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Downloading image...", Toast.LENGTH_SHORT).show()
                downloadAndSaveImage(imageUrl)
            }
        }

        @JavascriptInterface
        fun autoSaveToGallery(imageUrl: String) {
            saveImageToDevice(imageUrl)
        }
    }

    private fun downloadAndSaveImage(url: String) {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    saveBitmapToGallery(resource)
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
            })
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        @Suppress("DEPRECATION")
        val savedImageURL = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "AI_Photo_${System.currentTimeMillis()}",
            "Downloaded from AI Photo Studio"
        )

        if (savedImageURL != null) {
            Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }
}
