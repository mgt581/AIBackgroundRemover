@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    
    companion object {
        private const val TAG = "LoginActivity"
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "Google sign-in successful, authenticating with Firebase")
                firebaseAuthWithGoogle(account.idToken!!)
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
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener {
            performLogin()
        }

        findViewById<View>(R.id.btn_anonymous_sign_in).setOnClickListener {
            signInAnonymously()
        }

        findViewById<View>(R.id.btn_google_sign_in).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<View>(R.id.btn_header_sign_in).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            Toast.makeText(this, "Privacy Policy", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            Toast.makeText(this, "Terms of Service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Validate email format
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

        Log.d(TAG, "Attempting to sign in with email: $email")
        progressBar.visibility = View.VISIBLE
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d(TAG, "Email/password sign-in successful")
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                } else {
                    val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                    Log.e(TAG, "Email/password sign-in failed: ${task.exception?.message}, error code: $errorCode", task.exception)
                    
                    val errorMessage = when (errorCode) {
                        "ERROR_INVALID_EMAIL" -> "Invalid email address format"
                        "ERROR_WRONG_PASSWORD" -> "Incorrect password"
                        "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                        "ERROR_USER_DISABLED" -> "This account has been disabled"
                        "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again later"
                        "ERROR_INVALID_CREDENTIAL" -> "Invalid credentials. Please check your email and password"
                        else -> "Authentication failed: ${task.exception?.message}"
                    }
                    
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
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
                    navigateToMainActivity()
                } else {
                    val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                    Log.e(TAG, "Google Firebase authentication failed: ${task.exception?.message}, error code: $errorCode", task.exception)
                    
                    val errorMessage = when (errorCode) {
                        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "An account already exists with this email using a different sign-in method"
                        "ERROR_INVALID_CREDENTIAL" -> "Invalid Google credentials. Please try again"
                        else -> "Google authentication failed: ${task.exception?.message}"
                    }
                    
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
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
                navigateToMainActivity()
            } else {
                val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                Log.e(TAG, "Anonymous sign-in failed: ${task.exception?.message}, error code: $errorCode", task.exception)
                Toast.makeText(this, "Guest login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Navigate to MainActivity and clear the back stack to prevent users from
     * pressing back to return to the login screen after successful authentication.
     */
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Clear the entire back stack and start MainActivity as the root
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
