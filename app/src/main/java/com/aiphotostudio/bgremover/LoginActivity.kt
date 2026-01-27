@file:Suppress("DEPRECATION")

package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var progressBar: ProgressBar

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { 
                Log.d("LoginActivity", "Google sign in successful, authenticating with Firebase")
                firebaseAuthWithGoogle(it) 
            } ?: run {
                Log.e("LoginActivity", "Google ID Token is null")
                resetUi()
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google sign in failed. Code: ${e.statusCode}", e)
            resetUi()
            val message = when (e.statusCode) {
                7 -> "Network Error. Check your connection."
                10 -> "Developer Error: SHA-1 mismatch or Package Name error in Firebase."
                12500 -> "Sign-in failed. Check Play Services or SHA-1."
                else -> "Sign-in failed (Code ${e.statusCode}). Check Firebase/SHA-1."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progressBar)
        val btnGoogleSignIn = findViewById<SignInButton>(R.id.btn_google_sign_in)
        val btnHeaderSignIn = findViewById<Button>(R.id.btn_header_sign_in)
        
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnEmailLogin = findViewById<Button>(R.id.btn_email_login)
        val btnAnonymousSignIn = findViewById<Button>(R.id.btn_anonymous_sign_in)

        // Hardcoded Web Client ID from your google-services.json for maximum reliability
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("495715411996-iijvtbo02cn7tgvk6tp7284kjmdhefu0.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleSignIn.setOnClickListener { signIn() }
        btnHeaderSignIn.setOnClickListener { signIn() }
        
        btnEmailLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginWithEmail(email, password)
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }

        btnAnonymousSignIn.setOnClickListener {
            loginAnonymously()
        }
        
        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            // Privacy policy logic
        }
        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        googleSignInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun loginWithEmail(email: String, password: String) {
        progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    // If sign in fails, try to create a new user
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { createTab ->
                            progressBar.visibility = View.GONE
                            if (createTab.isSuccessful) {
                                startMainActivity()
                            } else {
                                Log.e("LoginActivity", "Email Auth failed", createTab.exception)
                                Toast.makeText(this, "Authentication Failed: ${createTab.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                resetUi()
                            }
                        }
                }
            }
    }

    private fun loginAnonymously() {
        progressBar.visibility = View.VISIBLE
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    Log.e("LoginActivity", "Anonymous Auth failed", task.exception)
                    Toast.makeText(this, "Guest login failed", Toast.LENGTH_SHORT).show()
                    resetUi()
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    Log.e("LoginActivity", "Firebase Auth failed", task.exception)
                    Toast.makeText(this, "Firebase Auth Failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    resetUi()
                }
            }
    }

    private fun resetUi() {
        progressBar.visibility = View.GONE
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}