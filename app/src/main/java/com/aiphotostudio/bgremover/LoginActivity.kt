package com.aiphotostudio.bgremover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
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
    private lateinit var btnSignIn: SignInButton
    private lateinit var googleSignInClient: GoogleSignInClient

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
                10 -> "Developer Error: Likely SHA-1 or Package Name mismatch in Firebase Console."
                12500 -> "Sign-in failed. Ensure Google Play Services is updated."
                12501 -> "Sign-in canceled."
                else -> "Login Error: ${e.localizedMessage}"
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
        btnSignIn = findViewById(R.id.btn_google_sign_in)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val tvPrivacy = findViewById<TextView>(R.id.tv_privacy_policy)
        val tvTerms = findViewById<TextView>(R.id.tv_terms_of_service)

        btnSignIn.setOnClickListener {
            signIn()
        }

        tvPrivacy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aiphotostudio.co/privacy-policy"))
            startActivity(intent)
        }

        tvTerms.setOnClickListener {
            val intent = Intent(this, TermsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun signIn() {
        Log.d("LoginActivity", "Initiating sign in")
        progressBar.visibility = View.VISIBLE
        btnSignIn.visibility = View.GONE

        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Firebase Auth successful")
                    startMainActivity()
                } else {
                    val exception = task.exception
                    Log.e("LoginActivity", "Firebase Auth failed", exception)
                    resetUi()
                    Toast.makeText(this, "Firebase Error: ${exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun resetUi() {
        progressBar.visibility = View.GONE
        btnSignIn.visibility = View.VISIBLE
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
