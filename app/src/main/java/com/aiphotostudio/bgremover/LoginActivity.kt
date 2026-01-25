package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
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

        btnSignIn.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        Log.d("LoginActivity", "Initiating sign in with Credential Manager")
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
                Log.e("LoginActivity", "Credential Manager error: ${e.type}", e)
                resetUi()
                Toast.makeText(this@LoginActivity, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        
        when (credential) {
            is GoogleIdTokenCredential -> {
                firebaseAuthWithGoogle(credential.idToken)
            }
            is CustomCredential -> {
                // This is the fix for "Unexpected credential type"
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                    } catch (e: Exception) {
                        Log.e("LoginActivity", "Error parsing Google ID Token", e)
                        resetUi()
                    }
                } else {
                    Log.e("LoginActivity", "Unexpected custom type: ${credential.type}")
                    resetUi()
                }
            }
            else -> {
                Log.e("LoginActivity", "Unexpected type: ${credential::class.java.simpleName}")
                resetUi()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    Log.e("LoginActivity", "Firebase Auth Failed", task.exception)
                    resetUi()
                    Toast.makeText(this, "Authentication Failed", Toast.LENGTH_SHORT).show()
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
