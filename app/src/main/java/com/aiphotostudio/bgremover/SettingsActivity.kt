package com.aiphotostudio.bgremover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        auth = FirebaseAuth.getInstance()
        functions = FirebaseFunctions.getInstance()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            goToHome()
        }

        val tvUserEmail = findViewById<TextView>(R.id.tv_user_email)
        val btnManageSub = findViewById<Button>(R.id.btn_manage_subscription)
        
        val user = auth.currentUser
        if (user != null) {
            tvUserEmail.text = user.email
            btnManageSub.visibility = View.VISIBLE
        } else {
            tvUserEmail.text = getString(R.string.not_signed_in)
            btnManageSub.visibility = View.GONE
        }

        btnManageSub.setOnClickListener {
            openBillingPortal()
        }

        findViewById<Button>(R.id.btn_about_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_about_privacy).setOnClickListener {
            openUrl("https://aiphotostudio.co/privacy")
        }

        findViewById<Button>(R.id.btn_back_home).setOnClickListener {
            goToHome()
        }

        setupFooter()
    }

    private fun setupFooter() {
        findViewById<View>(R.id.footer_btn_settings).setOnClickListener {
            // Already here
        }
        findViewById<View>(R.id.footer_btn_sign_in).setOnClickListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Toast.makeText(this, "Already signed in", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.footer_btn_sign_up).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_gallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<View>(R.id.footer_btn_privacy).setOnClickListener {
            openUrl("https://ai-photo-studio-24354.web.app/privacy")
        }
        findViewById<View>(R.id.footer_btn_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBillingPortal() {
        functions.getHttpsCallable("createBillingPortalLink")
            .call()
            .addOnSuccessListener { result ->
                val data = result.getData() as? Map<*, *>
                val url = data?.get("url") as? String
                if (url != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun goToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
