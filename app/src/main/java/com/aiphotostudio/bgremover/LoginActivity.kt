package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider

@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // UI
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var progressBar: ProgressBar

    private var isSignUpMode: Boolean = false

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                Log.w(TAG, "Google sign-in cancelled or failed with result code: ${result.resultCode}")
                setLoading(false)
                return@registerForActivityResult
            }

            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken.isNullOrBlank()) {
                    Log.e(TAG, "Google sign-in returned null/blank idToken")
                    Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                    setLoading(false)
                    return@registerForActivityResult
                }

                Log.d(TAG, "Google sign-in successful, authenticating with Firebase")
                firebaseAuthWithGoogle(idToken)
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign-in failed with code: ${e.statusCode}", e)
                Toast.makeText(
                    this,
                    getString(R.string.google_sign_in_failed_with_message, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
                setLoading(false)
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error during Google sign-in", t)
                Toast.makeText(this, getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // UI refs
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_email_login)
        toggleGroup = findViewById(R.id.toggle_group)
        progressBar = findViewById(R.id.progressBar)

        // Toggle group listener
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btn_tab_signin -> switchToSignInMode()
                R.id.btn_tab_signup -> switchToSignUpMode()
            }
        }

        // Buttons
        btnLogin.setOnClickListener { performLogin() }

        findViewById<View>(R.id.btn_anonymous_sign_in).setOnClickListener { signInAnonymously() }
        findViewById<View>(R.id.btn_google_sign_in).setOnClickListener { signInWithGoogle() }
        findViewById<View>(R.id.btn_close_login).setOnClickListener { finish() }

        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.btn_forgot_password).setOnClickListener { showForgotPasswordDialog() }

        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.privacy_policy))
                    .putExtra(WebPageActivity.EXTRA_URL, PRIVACY_URL)
            )
        }

        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.terms_of_service))
                    .putExtra(WebPageActivity.EXTRA_URL, TERMS_URL)
            )
        }

        // Initial state
        if (toggleGroup.checkedButtonId == R.id.btn_tab_signup) {
            switchToSignUpMode()
        } else {
            switchToSignInMode()
        }

        setLoading(false)
    }

    private fun switchToSignInMode() {
        isSignUpMode = false
        btnLogin.text = getString(R.string.sign_in)
        Log.d(TAG, "Switched to Sign In mode")
    }

    private fun switchToSignUpMode() {
        isSignUpMode = true
        btnLogin.text = getString(R.string.sign_up)
        Log.d(TAG, "Switched to Sign Up mode")
    }

    private fun performLogin() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString()?.trim().orEmpty()

        if (email.isBlank()) {
            Toast.makeText(this, getString(R.string.enter_email), Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, getString(R.string.enter_valid_email), Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        if (password.isBlank()) {
            Toast.makeText(this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show()
            etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
            etPassword.requestFocus()
            return
        }

        setLoading(true)

        if (isSignUpMode) {
            Log.d(TAG, "Attempting to create account with email: $email")
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        Log.d(TAG, "Account creation successful")
                        Toast.makeText(this, getString(R.string.account_created), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        handleAuthError(task.exception)
                    }
                }
        } else {
            Log.d(TAG, "Attempting to sign in with email: $email")
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        Log.d(TAG, "Email/password sign-in successful")
                        Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        handleAuthError(task.exception)
                    }
                }
        }
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.enter_email)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_password))
            .setView(input)
            .setPositiveButton(getString(R.string.send)) { dialog, _ ->
                val email = input.text?.toString()?.trim().orEmpty()
                if (email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    setLoading(true)
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            setLoading(false)
                            Toast.makeText(this, getString(R.string.reset_email_sent), Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(
                                this,
                                getString(R.string.reset_email_failed_with_message, e.localizedMessage ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    Toast.makeText(this, getString(R.string.enter_valid_email), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun handleAuthError(exception: Exception?) {
        val errorCode = (exception as? FirebaseAuthException)?.errorCode
        Log.e(TAG, "Auth failed: ${exception?.message}, error code: $errorCode", exception)

        val msg = when (errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.err_email_in_use)
            "ERROR_WEAK_PASSWORD" -> getString(R.string.err_weak_password)
            "ERROR_WRONG_PASSWORD" -> getString(R.string.err_wrong_password)
            "ERROR_USER_NOT_FOUND" -> getString(R.string.err_user_not_found)
            "ERROR_INVALID_CREDENTIAL" -> getString(R.string.err_invalid_credential)
            "ERROR_TOO_MANY_REQUESTS" -> getString(R.string.err_too_many_requests)
            else -> exception?.localizedMessage ?: getString(R.string.auth_failed)
        }

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "Initiating Google sign-in")
        setLoading(true)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "Authenticating with Firebase using Google ID token")
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "Google Firebase authentication successful")
                    Toast.makeText(this, getString(R.string.google_login_successful), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun signInAnonymously() {
        Log.d(TAG, "Attempting anonymous sign-in")
        setLoading(true)

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    Log.d(TAG, "Anonymous sign-in successful")
                    Toast.makeText(this, getString(R.string.logged_in_as_guest), Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        btnLogin.isEnabled = !isLoading
        toggleGroup.isEnabled = !isLoading
        etEmail.isEnabled = !isLoading
        etPassword.isEnabled = !isLoading

        // Optional: disable these too if you want to avoid double taps while loading
        findViewById<View>(R.id.btn_google_sign_in).isEnabled = !isLoading
        findViewById<View>(R.id.btn_anonymous_sign_in).isEnabled = !isLoading
        findViewById<View>(R.id.btn_forgot_password).isEnabled = !isLoading
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val PRIVACY_URL = "https://aiphotostudio.co/privacy"
        private const val TERMS_URL = "https://aiphotostudio.co/terms"
    }
}