@file:Suppress("DEPRECATION")
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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Activity for handling user authentication (Sign In, Sign Up, Anonymous, Google).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var progressBar: ProgressBar

    private var isSignUpMode = false

    companion object {
        private const val TAG = "LoginActivity"
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java) ?: return@registerForActivityResult
                Log.d(TAG, "Google sign-in successful, authenticating with Firebase")
                firebaseAuthWithGoogle(account.idToken ?: return@registerForActivityResult)
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign-in failed with code: ${e.statusCode}", e)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "Google sign-in cancelled or failed with result code: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Initialize UI Elements
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_email_login)
        toggleGroup = findViewById(R.id.toggle_group)
        progressBar = findViewById(R.id.progressBar)

        // Set up toggle group listener
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_tab_signin -> switchToSignInMode()
                    R.id.btn_tab_signup -> switchToSignUpMode()
                }
            }
        }

        btnLogin.setOnClickListener {
            performLogin()
        }

        findViewById<View>(R.id.btn_anonymous_sign_in).setOnClickListener {
            signInAnonymously()
        }

        findViewById<View>(R.id.btn_google_sign_in).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<View>(R.id.btn_close_login).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btn_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.btn_forgot_password).setOnClickListener {
            showForgotPasswordDialog()
        }

        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.privacy_policy))
                    .putExtra(WebPageActivity.EXTRA_URL, "https://aiphotostudio.co/privacy")
            )
        }

        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(
                Intent(this, WebPageActivity::class.java)
                    .putExtra(WebPageActivity.EXTRA_TITLE, getString(R.string.terms_of_service))
                    .putExtra(WebPageActivity.EXTRA_URL, "https://aiphotostudio.co/terms")
            )
        }

        // Initialize state
        if (toggleGroup.checkedButtonId == R.id.btn_tab_signup) {
            switchToSignUpMode()
        } else {
            switchToSignInMode()
        }
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
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show()
            etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            etPassword.requestFocus()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false

        if (isSignUpMode) {
            Log.d(TAG, "Attempting to create account with email: $email")
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        Log.d(TAG, "Account creation successful")
                        Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        handleAuthError(task.exception)
                    }
                }
        } else {
            Log.d(TAG, "Attempting to sign in with email: $email")
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        Log.d(TAG, "Email/password sign-in successful")
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        handleAuthError(task.exception)
                    }
                }
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Reset Password")
        val input = EditText(this)
        input.hint = "Enter your email"
        builder.setView(input)

        builder.setPositiveButton("Send") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to send reset email: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun handleAuthError(exception: Exception?) {
        val errorCode = (exception as? FirebaseAuthException)?.errorCode
        Log.e(TAG, "Auth failed: ${exception?.message}, error code: $errorCode", exception)

        val errorMessage = when (errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists"
            "ERROR_WEAK_PASSWORD" -> "Password is too weak"
            "ERROR_WRONG_PASSWORD" -> "Incorrect password"
            "ERROR_USER_NOT_FOUND" -> "No account found with this email"
            "ERROR_INVALID_CREDENTIAL" -> "Invalid email or password"
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later"
            else -> exception?.localizedMessage ?: "Authentication failed"
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "Initiating Google sign-in")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "Authenticating with Firebase using Google ID token")
        progressBar.visibility = View.VISIBLE
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d(TAG, "Google Firebase authentication successful")
                    Toast.makeText(this, "Google login successful", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    handleAuthError(task.exception)
                }
            }
    }

    private fun signInAnonymously() {
        Log.d(TAG, "Attempting anonymous sign-in")
        progressBar.visibility = View.VISIBLE
        auth.signInAnonymously().addOnCompleteListener(this) { task ->
            progressBar.visibility = View.GONE
            if (task.isSuccessful) {
                Log.d(TAG, "Anonymous sign-in successful")
                Toast.makeText(this, "Logged in as Guest", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                handleAuthError(task.exception)
            }
        }
    }
}
