package com.aiphotostudio.bgremover

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Home button (top left) redirects to MainActivity
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_home) // We'll need to ensure this exists or use default

        toolbar.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        loadImages()
    }

    private fun loadImages() {
        val imageList = mutableListOf<Uri>()
        
        val directory = File(filesDir, "saved_images")
        if (directory.exists()) {
            val files = directory.listFiles()
            files?.sortByDescending { it.lastModified() }
            files?.forEach { file ->
                imageList.add(Uri.fromFile(file))
            }
        }

        if (imageList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = GalleryAdapter(
                imageList,
                onDeleteClick = { uri -> deleteImage(uri) }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun deleteImage(uri: Uri) {
        try {
            val file = File(uri.path!!)
            if (file.exists()) {
                file.delete()
                loadImages()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
