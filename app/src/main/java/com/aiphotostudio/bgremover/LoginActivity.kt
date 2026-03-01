package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        progressBar = findViewById(R.id.progressBar)

        findViewById<Button>(R.id.btn_google_sign_in).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<Button>(R.id.btn_close_login).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    /**
     * Initiates Google sign-in using Credential Manager.
     */
    private fun signInWithGoogle() {
        setLoading(true)

        // Web OAuth Client ID from google-services.json (Corrected ID)
        val serverClientId = "411346648650-614s08k3rp9f9er43unjo4djefmht63t.apps.googleusercontent.com"

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    this@LoginActivity,
                    request
                )

                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    val idToken = credential.idToken
                    Log.d("LOGIN", "Google ID token received")
                    firebaseAuthWithGoogle(idToken)
                } else {
                    setLoading(false)
                    showToast("Google credential not returned")
                }
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e("LOGIN", "Google sign-in error", e)
                showToast("Google sign-in failed: ${e.message}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Log.d("LOGIN", "Firebase Auth successful")
                    finish()
                } else {
                    Log.e("LOGIN", "Firebase Auth failed", task.exception)
                    showToast("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
