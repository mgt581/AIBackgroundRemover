package com.aiphotostudiobgremover

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiphotostudiobgremover.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnHome.setOnClickListener { finish() }
        binding.btnCloseLogin.setOnClickListener { finish() }
    }
}
