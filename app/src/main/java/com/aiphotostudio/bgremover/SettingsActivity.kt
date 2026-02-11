package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        auth = FirebaseAuth.getInstance()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            goToHome()
        }

        val tvUserEmail = findViewById<TextView>(R.id.tv_user_email)
        val user = auth.currentUser
        tvUserEmail.text = user?.email ?: getString(R.string.not_signed_in)

        findViewById<Button>(R.id.btn_about_terms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_about_privacy).setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.privacy_policy))
                    .putExtra(WebPageActivity.EXTRA_URL, "https://aiphotostudio.co/privacy")
            )
        }

        findViewById<Button>(R.id.btn_back_home).setOnClickListener {
            goToHome()
        }
    }

    private fun goToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
