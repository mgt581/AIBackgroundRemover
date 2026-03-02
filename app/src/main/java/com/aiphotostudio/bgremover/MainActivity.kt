package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.aiphotostudio.bgremover.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupFooterButtons()
        setupOnBackPressed()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.backgroundWebView ?: return
        
        webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("MainActivity", "Page finished loading: $url")
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.e("MainActivity", "Error loading page: $description")
                }
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                databaseEnabled = true
            }

            addJavascriptInterface(
                WebAppInterface(
                    context = this@MainActivity,
                    onBackgroundPickerRequested = { /* Handle background picker */ },
                    onGoogleSignInRequested = { /* Handle Google Sign-In */ },
                    onLoginRequested = {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    },
                    onLoginSuccess = { /* Handle login success */ },
                    callback = { success, message ->
                        runOnUiThread {
                            if (!success && message != null) {
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ),
                "AndroidInterface"
            )

            loadUrl("https://aiphotostudio.co.uk")
        }
    }

    private fun setupFooterButtons() {
        binding.footerBtnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        binding.footerBtnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.footerBtnPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        binding.footerBtnTerms.setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
        
        binding.btnHeaderLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = binding.backgroundWebView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
