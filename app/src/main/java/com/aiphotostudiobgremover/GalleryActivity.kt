package com.aiphotostudio.bgremover

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiphotostudio.bgremover.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup back button if it exists in layout
        // binding.btnBack.setOnClickListener { finish() }
    }
}
