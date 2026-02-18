package com.aiphotostudio.bgremover

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Activity for user authentication.
 * Handles Email, Google, and Anonymous Sign-In.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnEmailLogin: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var btnAnonymousSignIn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private var isSignIn = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnEmailLogin = findViewById(R.id.btn_email_login)
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        btnAnonymousSignIn = findViewById(R.id.btn_anonymous_sign_in)
        progressBar = findViewById(R.id.progressBar)
        toggleGroup = findViewById(R.id.toggle_group)

        findViewById<Button>(R.id.btn_close_login).setOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isSignIn = checkedId == R.id.btn_tab_signin
                btnEmailLogin.text = if (isSignIn) getString(R.string.sign_in) else getString(R.string.sign_up)
            }
        }

        btnEmailLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showToast(getString(R.string.email_and_password_required))
                return@setOnClickListener
            }

            setLoading(true)
            if (isSignIn) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            showToast(getString(R.string.signin_successful))
                            finish()
                        } else {
                            val msg = task.exception?.message ?: getString(R.string.auth_failed)
                            showToast(getString(R.string.signin_failed_with_message, msg))
                        }
                    }
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            showToast(getString(R.string.signup_successful))
                            finish()
                        } else {
                            val message = if (task.exception is FirebaseAuthUserCollisionException) {
                                getString(R.string.err_email_in_use)
                            } else {
                                task.exception?.message ?: getString(R.string.auth_failed)
                            }
                            showToast(getString(R.string.signup_failed_with_message, message))
                        }
                    }
            }
        }

        btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
        
        // FIXING ANON BUTTON
        btnAnonymousSignIn.setOnClickListener {
            Log.d(TAG, "Anonymous Button Clicked")
            Toast.makeText(this, "Signing in as Guest...", Toast.LENGTH_SHORT).show()
            signInAnonymously()
        }

        findViewById<Button>(R.id.btn_forgot_password).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                showToast(getString(R.string.enter_email_to_reset))
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { showToast(getString(R.string.password_reset_email_sent)) }
                .addOnFailureListener {
                    showToast(getString(R.string.failed_to_send_reset_email, it.message))
                }
        }
    }

    private fun signInWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                setLoading(true)
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                        setLoading(false)
                        if (task.isSuccessful) {
                            showToast(getString(R.string.google_signin_successful))
                            finish()
                        } else {
                            showToast(task.exception?.message ?: "Google Auth Failed")
                        }
                    }
                } else {
                    setLoading(false)
                }
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e(TAG, "Credential Manager Error", e)
                showToast("Google error: ${e.message}")
            }
        }
    }

    private fun signInAnonymously() {
        setLoading(true)
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "Anon Success")
                    showToast(getString(R.string.logged_in_as_guest))
                    finish()
                } else {
                    val msg = task.exception?.message ?: "Guest login failed"
                    Log.e(TAG, "Anon Error: $msg")
                    showToast(msg)
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnEmailLogin.isEnabled = !isLoading
        btnGoogleSignIn.isEnabled = !isLoading
        btnAnonymousSignIn.isEnabled = !isLoading
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
