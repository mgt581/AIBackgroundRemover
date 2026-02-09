@file:Suppress("DEPRECATION")
package com.aiphotostudio.bgremover

import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
    private lateinit var btnTabSignIn: Button
    private lateinit var btnTabSignUp: Button
    private lateinit var progressBar: ProgressBar
    
    private var isSignUpMode = false
    
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
        btnTabSignIn = findViewById(R.id.btn_tab_signin)
        btnTabSignUp = findViewById(R.id.btn_tab_signup)
        progressBar = findViewById(R.id.progressBar)

        // Set up tab listeners
        btnTabSignIn.setOnClickListener {
            switchToSignInMode()
        }

        btnTabSignUp.setOnClickListener {
            switchToSignUpMode()
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

        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            openUrl("https://aiphotostudio.co/privacy")
        }

        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }

        // Initialize in sign-in mode
        switchToSignInMode()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening URL", e)
        }
    }

    private fun switchToSignInMode() {
        isSignUpMode = false
        updateTabStyles(btnTabSignIn, btnTabSignUp)
        btnLogin.text = getString(R.string.sign_in)
        Log.d(TAG, "Switched to Sign In mode")
    }

    private fun switchToSignUpMode() {
        isSignUpMode = true
        updateTabStyles(btnTabSignUp, btnTabSignIn)
        btnLogin.text = getString(R.string.sign_up)
        Log.d(TAG, "Switched to Sign Up mode")
    }

    private fun updateTabStyles(activeTab: Button, inactiveTab: Button) {
        val primaryColor = ContextCompat.getColor(this, R.color.brand_primary)
        val surfaceColor = ContextCompat.getColor(this, R.color.brand_surface)
        val whiteColor = ContextCompat.getColor(this, R.color.white)

        activeTab.backgroundTintList = ColorStateList.valueOf(primaryColor)
        activeTab.setTextColor(whiteColor)
        
        inactiveTab.backgroundTintList = ColorStateList.valueOf(surfaceColor)
        inactiveTab.setTextColor(whiteColor)
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

        progressBar.visibility = View.VISIBLE
        
        if (isSignUpMode) {
            // Sign up mode: create new account
            Log.d(TAG, "Attempting to create account with email: $email")
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        Log.d(TAG, "Account creation successful")
                        Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                        Log.e(TAG, "Account creation failed: ${task.exception?.message}, error code: $errorCode", task.exception)
                        
                        val errorMessage = when (errorCode) {
                            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists"
                            "ERROR_WEAK_PASSWORD" -> "Password is too weak. Please use a stronger password"
                            "ERROR_INVALID_EMAIL" -> "Invalid email address format"
                            else -> "Sign up failed: ${task.exception?.message}"
                        }
                        
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Sign in mode: authenticate with existing account
            Log.d(TAG, "Attempting to sign in with email: $email")
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        Log.d(TAG, "Email/password sign-in successful")
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        finish()
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
                finish()
            } else {
                val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                Log.e(TAG, "Anonymous sign-in failed: ${task.exception?.message}, error code: $errorCode", task.exception)
                Toast.makeText(this, "Guest login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()

                fun validateAndFinishLogin() {
                    val user = FirebaseAuth.getInstance().currentUser

                    if (user == null) {
                        Log.e(TAG, "User is null after login")
                        Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show()
                        return
                    }

                    user.getIdToken(true)
                        .addOnSuccessListener { result ->
                            Log.d(TAG, "Token (first 20 chars): ${result.token?.take(20)}...")

                            setResult(RESULT_OK)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Token refresh failed", e)
                            Toast.makeText(this, "Session error. Please log in again.", Toast.LENGTH_LONG).show()
                        }
                }


            }
        }
    }
}
