package com.aiphotostudio.bgremover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private lateinit var btnAuthAction: Button
    private lateinit var tvSignedInStatus: TextView
    private lateinit var btnHeaderGallery: Button

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { 
                Log.d("LoginActivity", "Google sign in successful, authenticating with Firebase")
                firebaseAuthWithGoogle(it) 
            } ?: run {
                Log.e("LoginActivity", "Google ID Token is null")
                resetUi()
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google sign in failed. Code: ${e.statusCode}", e)
            resetUi()
            val message = when (e.statusCode) {
                7 -> "Network Error. Check your connection."
                10 -> "Developer Error: Likely SHA-1 or Package Name mismatch in Firebase Console."
                12500 -> "Sign-in failed. Ensure Google Play Services is updated."
                12501 -> "Sign-in canceled."
                else -> "Login Error: ${e.localizedMessage}"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progressBar)
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        btnAuthAction = findViewById(R.id.btn_auth_action)
        tvSignedInStatus = findViewById(R.id.tv_signed_in_status)
        btnHeaderGallery = findViewById(R.id.btn_header_gallery)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        updateUiForAuthStatus()

        btnGoogleSignIn.setOnClickListener { signIn() }
        
        btnAuthAction.setOnClickListener {
            if (auth.currentUser != null) {
                signOut()
            } else {
                signIn()
            }
        }

        btnHeaderGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<TextView>(R.id.tv_privacy_policy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aiphotostudio.co/privacy-policy")))
        }

        findViewById<TextView>(R.id.tv_terms_of_service).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
        
        // If user clicks the main title/logo area, they might want to go to MainActivity if signed in
        findViewById<TextView>(R.id.tv_title).setOnClickListener {
            if (auth.currentUser != null) {
                startMainActivity()
            }
        }
    }

    private fun updateUiForAuthStatus() {
        val user = auth.currentUser
        if (user != null) {
            btnAuthAction.text = getString(R.string.sign_out)
            tvSignedInStatus.visibility = View.VISIBLE
            btnHeaderGallery.visibility = View.VISIBLE
            btnGoogleSignIn.visibility = View.GONE
            // Optional: Auto-redirect or let them stay? User said "when your signed in it will say... then button changes to sign out"
            // Let's stay on this page to show the status as requested, but provide a way to go to main app
            startMainActivity() // Typical flow, but user's request is specific about UI state.
        } else {
            btnAuthAction.text = getString(R.string.sign_in)
            tvSignedInStatus.visibility = View.GONE
            btnHeaderGallery.visibility = View.GONE
            btnGoogleSignIn.visibility = View.VISIBLE
        }
    }

    private fun signIn() {
        Log.d("LoginActivity", "Initiating sign in")
        progressBar.visibility = View.VISIBLE
        btnGoogleSignIn.visibility = View.GONE

        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            updateUiForAuthStatus()
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "Firebase Auth successful")
                    updateUiForAuthStatus()
                } else {
                    Log.e("LoginActivity", "Firebase Auth failed", task.exception)
                    Toast.makeText(this, "Firebase Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    resetUi()
                }
            }
    }

    private fun resetUi() {
        progressBar.visibility = View.GONE
        updateUiForAuthStatus()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        // We don't finish() if we want them to be able to come back to "index" to sign out?
        // Actually, normally we finish, but the user's "index" request suggests it's a persistent hub.
    }
}
