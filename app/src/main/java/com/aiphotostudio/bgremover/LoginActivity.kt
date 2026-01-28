package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isSignUp by remember { mutableStateOf(false) }
            
            MaterialTheme {
                LoginScreen(
                    isSignUp = isSignUp,
                    onToggleMode = { isSignUp = !isSignUp },
                    onAuthAction = { email, password ->
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
                    },
                    onForgotPassword = { email ->
                        if (email.isBlank()) {
                            Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show()
                        } else {
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    onGoogleSignIn = {
                        // This triggers the standard activity logic
                        Toast.makeText(this, "Google Sign In coming via update", Toast.LENGTH_SHORT).show()
                    },
                    onGoHome = { goToMain() }
                )
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

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
    
    val brandBg = colorResource(R.color.brand_background)
    val brandAccent = colorResource(R.color.brand_accent)
    val brandPrimary = colorResource(R.color.brand_primary)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Home Button Top Left
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onGoHome) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Logo
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Logo",
                modifier = Modifier.size(140.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "AI Background Remover",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Professional Photo Studio",
                color = colorResource(R.color.brand_text_secondary),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(40.dp))

            // Form
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address", color = Color.LightGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = brandPrimary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.LightGray) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = brandPrimary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            // Forgot Password (only in sign in mode)
            if (!isSignUp) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = "Forgot Password?",
                        color = brandAccent,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .clickable { onForgotPassword(email) }
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { onAuthAction(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandPrimary)
            ) {
                Text(if (isSignUp) "Create Account" else "Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Text(text = "OR", color = Color.Gray, fontSize = 12.sp)

            Spacer(Modifier.height(24.dp))

            // Google Sign In Button
            Surface(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Continue with Google", color = Color.Black, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(32.dp))

            Row {
                Text(
                    text = if (isSignUp) "Already have an account? " else "Don't have an account? ",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isSignUp) "Sign In" else "Sign Up",
                    color = brandAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleMode() }
                )
            }
        }
    }
}