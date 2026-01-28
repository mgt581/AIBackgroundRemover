package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialManager = CredentialManager.create(this)

        setContent {
            var isSignUp by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            
            MaterialTheme {
                LoginScreen(
                    isSignUp = isSignUp,
                    onToggleMode = { isSignUp = !isSignUp },
                    onAuthAction = { email, password ->
                        if (email.isBlank() || password.isBlank()) {
                            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                        } else {
                            if (isSignUp) {
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener { goToMain() }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            } else {
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnSuccessListener { goToMain() }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    },
                    onForgotPassword = { email ->
                        if (email.isBlank()) {
                            Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show()
                        } else {
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Reset email sent to $email", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    onGoogleSignIn = {
                        scope.launch {
                            signInWithGoogle()
                        }
                    },
                    onGoHome = { goToMain() }
                )
            }
        }
    }

    private suspend fun signInWithGoogle() {
        try {
            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(this, request)
            val credential = result.credential
            
            if (credential is androidx.credentials.CustomCredential && 
                credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                
                val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                
                auth.signInWithCredential(firebaseCredential)
                    .addOnSuccessListener { goToMain() }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Firebase Auth Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Google Sign In Error", e)
            Toast.makeText(this, "Google Sign In failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    isSignUp: Boolean,
    onToggleMode: () -> Unit,
    onAuthAction: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoHome: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    val purplePrimary = Color(0xFF6342FF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onGoHome) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back home",
                    tint = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Image(
            painter = painterResource(id = R.mipmap.ic_launcher),
            contentDescription = "Logo",
            modifier = Modifier.size(160.dp),
            contentScale = ContentScale.Fit
        )

        val annotatedTitle = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color(0xFF4256FF), fontWeight = FontWeight.Bold)) {
                append("AI Background ")
            }
            withStyle(style = SpanStyle(color = Color(0xFFA642FF), fontWeight = FontWeight.Normal)) {
                append("Remover")
            }
        }
        
        Text(
            text = annotatedTitle,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Email address", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.LightGray,
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Password", color = Color.Gray) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.LightGray,
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        if (!isSignUp) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "Forgot password?",
                    color = Color(0xFF4256FF),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .clickable { onForgotPassword(email) }
                )
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { onAuthAction(email, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = purplePrimary)
        ) {
            Text(if (isSignUp) "Sign Up" else "Sign In", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))

        Text(text = "OR", color = Color.Gray, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onGoogleSignIn,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "G ", 
                    color = Color(0xFF4285F4), 
                    fontWeight = FontWeight.Black, 
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(8.dp))
                Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(32.dp))

        Row {
            Text(
                text = if (isSignUp) "Already have an account? " else "No account? ",
                color = Color.DarkGray,
                fontSize = 15.sp
            )
            Text(
                text = if (isSignUp) "Sign in" else "Sign up",
                color = Color(0xFF4256FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onToggleMode() }
            )
        }
        
        Spacer(Modifier.height(40.dp))
    }
}
