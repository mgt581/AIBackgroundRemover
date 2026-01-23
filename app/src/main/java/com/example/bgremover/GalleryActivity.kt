package com.example.bgremover

import android.os.Bundle
import android.os.Environment
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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rv_gallery)
        tvEmpty = findViewById(R.id.tv_empty)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        loadImages()
    }

    private fun loadImages() {
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadFolder.listFiles { file ->
            file.isFile && (file.extension.lowercase() == "jpg" || file.extension.lowercase() == "png" || file.extension.lowercase() == "jpeg")
        } ?: emptyArray()

        // Sort by last modified (newest first)
        val sortedFiles = files.sortedByDescending { it.lastModified() }

        if (sortedFiles.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter = GalleryAdapter(sortedFiles.toMutableList()) { file ->
                // Delete logic
                if (file.delete()) {
                    loadImages()
                }
            }
            recyclerView.adapter = adapter
        }
    }
}
