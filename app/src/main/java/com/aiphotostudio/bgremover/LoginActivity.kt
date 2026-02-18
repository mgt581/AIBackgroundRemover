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
 * Activity for user authentication, including email/password, Google Sign-In,
 * and Anonymous authentication.
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
                updateUiForAuthMode()
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
                            val errorMessage = task.exception?.message ?: getString(R.string.auth_failed)
                            showToast(getString(R.string.signin_failed_with_message, errorMessage))
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
                            val exception = task.exception
                            val message = if (exception is FirebaseAuthUserCollisionException) {
                                getString(R.string.err_email_in_use)
                            } else {
                                exception?.message ?: getString(R.string.auth_failed)
                            }
                            showToast(getString(R.string.signup_failed_with_message, message))
                        }
                    }
            }
        }

        btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
        
        // RE-LINKING ANON BUTTON
        btnAnonymousSignIn.setOnClickListener {
            Log.d(TAG, "Anonymous Sign-In clicked")
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
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    firebaseAuthWithGoogle(credential.idToken)
                } else {
                    setLoading(false)
                    showToast(getString(R.string.google_sign_in_failed))
                }
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e(TAG, "Credential Manager Error", e)
                val msg = if (e.message?.contains("No credentials available") == true) {
                    "No Google accounts found on device."
                } else {
                    e.message ?: "Unknown error"
                }
                showToast(getString(R.string.google_signin_failed_with_message, msg))
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    showToast(getString(R.string.google_signin_successful))
                    finish()
                } else {
                    val errorMessage = task.exception?.message ?: getString(R.string.auth_failed)
                    showToast(getString(R.string.google_signin_failed, errorMessage))
                }
            }
    }

    private fun signInAnonymously() {
        Log.d(TAG, "Starting signInAnonymously flow")
        setLoading(true)
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "Anonymous sign-in SUCCESS")
                    showToast(getString(R.string.logged_in_as_guest))
                    finish()
                } else {
                    val msg = task.exception?.message ?: getString(R.string.auth_failed)
                    Log.e(TAG, "Anonymous sign-in FAILED: $msg")
                    showToast("Guest login failed: $msg")
                }
            }
    }

    private fun updateUiForAuthMode() {
        btnEmailLogin.text = if (isSignIn) getString(R.string.sign_in) else getString(R.string.sign_up)
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
