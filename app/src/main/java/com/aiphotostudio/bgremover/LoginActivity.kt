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
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Activity for user authentication, including email/password sign-in and sign-up,
 * as well as Google Sign-In integration using modern Credential Manager.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnEmailLogin: Button
    private lateinit var btnGoogleSignIn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private var isSignIn = true

    /**
     * Initializes the activity, setting up Firebase and Credential Manager.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        initViews()
        setupClickListeners()
    }

    /**
     * Initializes UI components by finding them in the layout.
     */
    private fun initViews() {
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnEmailLogin = findViewById(R.id.btn_email_login)
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        progressBar = findViewById(R.id.progressBar)
        toggleGroup = findViewById(R.id.toggle_group)

        findViewById<Button>(R.id.btn_close_login).setOnClickListener { finish() }
    }

    /**
     * Sets up click listeners for the buttons and toggle group.
     */
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
                            val errorMessage = task.exception?.message
                                ?: getString(R.string.auth_failed)
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
                            val errorMessage = task.exception?.message
                                ?: getString(R.string.auth_failed)
                            showToast(getString(R.string.signup_failed_with_message, errorMessage))
                        }
                    }
            }
        }

        btnGoogleSignIn.setOnClickListener { signInWithGoogle() }

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

    /**
     * Initiates Google sign-in using Credential Manager.
     */
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
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                val credential = result.credential
                if (credential is GoogleIdTokenCredential) {
                    firebaseAuthWithGoogle(credential.idToken)
                }
            } catch (e: GetCredentialException) {
                Log.e(TAG, CREDENTIAL_MANAGER_ERROR, e)
                showToast(getString(R.string.google_signin_failed_with_message, e.message))
            }
        }
    }

    /**
     * Authenticates with Firebase using the Google ID token.
     * @param idToken The Google ID token.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        setLoading(true)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    showToast(getString(R.string.google_signin_successful))
                    finish()
                } else {
                    val errorMessage = task.exception?.message
                        ?: getString(R.string.auth_failed)
                    showToast(getString(R.string.google_signin_failed, errorMessage))
                }
            }
    }

    /**
     * Updates the text of the login button based on the current mode (Sign In or Sign Up).
     */
    private fun updateUiForAuthMode() {
        btnEmailLogin.text = if (isSignIn) {
            getString(R.string.sign_in)
        } else {
            getString(R.string.sign_up)
        }
    }

    /**
     * Shows or hides the progress bar and enables/disables the login buttons.
     * @param isLoading Whether the authentication process is ongoing.
     */
    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnEmailLogin.isEnabled = !isLoading
        btnGoogleSignIn.isEnabled = !isLoading
    }

    /**
     * Displays a toast message.
     * @param message The message to be displayed.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val CREDENTIAL_MANAGER_ERROR = "Credential Manager Error"
    }
}
