package com.aiphotostudio.bgremover

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnGuest: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_email_login)
        btnGuest = findViewById(R.id.btn_anonymous_sign_in)
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener {
            performLogin()
        }

        btnGuest.setOnClickListener {
            signInAnonymously()
        }

        findViewById<View>(R.id.btn_google_sign_in).setOnClickListener {
            Toast.makeText(this, "Google Sign-In is being configured", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun signInAnonymously() {
        progressBar.visibility = View.VISIBLE
        auth.signInAnonymously().addOnCompleteListener(this) { task ->
            progressBar.visibility = View.GONE
            if (task.isSuccessful) {
                Toast.makeText(this, "Logged in as guest", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Guest login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
