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

    /**
     * Initializes the activity, UI, and Firebase services.
     * @param savedInstanceState The saved instance state.
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
     * Binds UI elements from the layout.
     */
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

    /**
     * Sets up all click listeners for the authentication buttons.
     */
    private fun setupClickListeners() {
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isSignIn = (checkedId == R.id.btn_tab_signin)
                updateUiForAuthMode()
            }
        }

        btnEmailLogin.setOnClickListener {
            handleEmailPasswordAuth()
        }

        btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
        btnAnonymousSignIn.setOnClickListener { signInAnonymously() }

        findViewById<Button>(R.id.btn_forgot_password).setOnClickListener {
            handlePasswordReset()
        }
    }

    /**
     * Handles the logic for email/password sign-in or sign-up.
     */
    private fun handleEmailPasswordAuth() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            showToast(getString(R.string.email_and_password_required))
            return
        }

        setLoading(true)
        val authAction = if (isSignIn) {
            auth.signInWithEmailAndPassword(email, password)
        } else {
            auth.createUserWithEmailAndPassword(email, password)
        }

        authAction.addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                showToast(if (isSignIn) getString(R.string.signin_successful) else getString(R.string.signup_successful))
                finish()
            } else {
                val error = when (task.exception) {
                    is FirebaseAuthUserCollisionException -> getString(R.string.err_email_in_use)
                    else -> task.exception?.message ?: getString(R.string.auth_failed)
                }
                showToast(getString(if (isSignIn) R.string.signin_failed_with_message else R.string.signup_failed_with_message, error))
            }
        }
    }

    /**
     * Handles password reset requests.
     */
    private fun handlePasswordReset() {
        val email = etEmail.text.toString().trim()
        if (email.isEmpty()) {
            showToast(getString(R.string.enter_email_to_reset))
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { showToast(getString(R.string.password_reset_email_sent)) }
            .addOnFailureListener { showToast(getString(R.string.failed_to_send_reset_email, it.message)) }
    }

    /**
     * Initiates Google sign-in using Credential Manager.
     */
    private fun signInWithGoogle() {
        setLoading(true)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential as? GoogleIdTokenCredential
                credential?.idToken?.let { firebaseAuthWithGoogle(it) } ?: setLoading(false)
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e(TAG, CREDENTIAL_MANAGER_ERROR, e)
                val msg = e.message ?: getString(R.string.auth_failed)
                showToast(getString(R.string.google_signin_failed_with_message, msg))
            }
        }
    }

    /**
     * Authenticates with Firebase using the Google ID token.
     * @param idToken The Google ID token.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                showToast(getString(R.string.google_signin_successful))
                finish()
            } else {
                showToast(getString(R.string.google_signin_failed, task.exception?.message))
            }
        }
    }

    /**
     * Initiates the Firebase anonymous sign-in flow.
     */
    private fun signInAnonymously() {
        setLoading(true)
        auth.signInAnonymously().addOnCompleteListener(this) { task ->
            setLoading(false)
            if (task.isSuccessful) {
                Log.d(TAG, SIGNIN_SUCCESS_MSG)
                showToast(getString(R.string.logged_in_as_guest))
                finish()
            } else {
                Log.e(TAG, SIGNIN_FAILED_MSG, task.exception)
                showToast(getString(R.string.guest_login_failed, task.exception?.message))
            }
        }
    }

    /**
     * Updates the UI text based on the current auth mode (Sign In/Sign Up).
     */
    private fun updateUiForAuthMode() {
        btnEmailLogin.text = if (isSignIn) getString(R.string.sign_in) else getString(R.string.sign_up)
    }

    /**
     * Controls the visibility of the loading spinner and button states.
     * @param isLoading True to show loading, false to hide.
     */
    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnEmailLogin.isEnabled = !isLoading
        btnGoogleSignIn.isEnabled = !isLoading
        btnAnonymousSignIn.isEnabled = !isLoading
    }

    /**
     * Displays a short toast message.
     * @param message The message to display.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val CREDENTIAL_MANAGER_ERROR = "Credential Manager Error"
        private const val SIGNIN_SUCCESS_MSG = "Anonymous sign-in successful."
        private const val SIGNIN_FAILED_MSG = "Anonymous sign-in failed."
    }
}
