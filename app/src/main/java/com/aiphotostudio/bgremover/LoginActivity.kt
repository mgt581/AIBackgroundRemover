package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-skip if already logged in
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setContent {
            MaterialTheme {
                LoginScreen(
                    onLogin = { email, password ->
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener { goToMain() }
                            .addOnFailureListener {
                                Log.e("AUTH", "Login failed", it)
                            }
                    },
                    onSignUp = { email, password ->
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener { goToMain() }
                            .addOnFailureListener {
                                Log.e("AUTH", "Signup failed", it)
                            }
                    }
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
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "AI Photo Studio\nBackground Remover",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onLogin(email.trim(), password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign In")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onSignUp(email.trim(), password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Account")
        }
    }
}