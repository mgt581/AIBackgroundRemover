package com.aiphotostudio.bgremover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSignIn: SignInButton
    private lateinit var credentialManager: CredentialManager

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
        credentialManager = CredentialManager.create(this)

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

        val webClientId = getString(R.string.default_web_client_id)

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                Log.e("LoginActivity", "Credential Manager error", e)
                resetUi()
                
                if (e !is GetCredentialCancellationException) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Sign in failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential

        when {
            credential is GoogleIdTokenCredential -> {
                Log.d("LoginActivity", "Direct GoogleIdTokenCredential received")
                firebaseAuthWithGoogle(credential.idToken)
            }

            credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                try {
                    Log.d("LoginActivity", "Unpacking CustomCredential")
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Failed to parse CustomCredential", e)
                    resetUi()
                }
            }

            else -> {
                Log.e("LoginActivity", "Unexpected credential type: ${credential.type}")
                resetUi()
                Toast.makeText(this, "Authentication type not recognized", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Firebase login success")
                    startMainActivity()
                } else {
                    Log.e("LoginActivity", "Firebase login failed", task.exception)
                    resetUi()
                    Toast.makeText(this, "Firebase Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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
