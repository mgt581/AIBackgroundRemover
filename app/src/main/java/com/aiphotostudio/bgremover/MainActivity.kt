package com.aiphotostudio.bgremover

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
        binding.backgroundWebView.apply {
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
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
        
        binding.btnHeaderLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.backgroundWebView.canGoBack()) {
                    binding.backgroundWebView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
