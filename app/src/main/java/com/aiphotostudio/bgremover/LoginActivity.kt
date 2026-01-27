package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var btnHeaderSignIn: Button

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { 
                Log.d("LoginActivity", "Google sign in successful")
                firebaseAuthWithGoogle(it) 
            } ?: run {
                resetUi()
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Sign in failed: ${e.statusCode}")
            resetUi()
            Toast.makeText(this, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // If user is already signed in, they should go to MainActivity (Index page)
        if (auth.currentUser != null) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progressBar)
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        btnHeaderSignIn = findViewById(R.id.btn_header_sign_in)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleSignIn.setOnClickListener { signIn() }
        btnHeaderSignIn.setOnClickListener { signIn() }
        
        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            // Logic for privacy policy
        }
        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            // Logic for terms
        }
    }

    private fun signIn() {
        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.visibility = View.GONE
        googleSignInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
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
                    Toast.makeText(this, "Auth Failed", Toast.LENGTH_SHORT).show()
                    resetUi()
                }
            }
    }

    private fun resetUi() {
        progressBar.visibility = View.GONE
        btnGoogleSignIn.visibility = View.VISIBLE
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
