package com.example.bgremover

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiphotostudio.bgremover.GalleryAdapter
import com.aiphotostudio.bgremover.R
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        loadImages()
    }

    private fun loadImages() {
        val imageList = mutableListOf<Uri>()
        
        // Load from app's private internal storage
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
                onDeleteClick = { uri -> deleteImage(uri) },
                onDownloadClick = { /* Not needed for local internal files */ }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun deleteImage(uri: Uri) {
        try {
            val file = File(uri.path!!)
            if (file.exists()) {
                file.delete()
                loadImages() // Refresh gallery
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
