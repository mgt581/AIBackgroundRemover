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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aiphotostudio.co/privacy"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_back_home).setOnClickListener {
            goToHome()
        }
    }

    private fun openBillingPortal() {
        functions.getHttpsCallable("createBillingPortalLink")
            .call()
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *>
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
