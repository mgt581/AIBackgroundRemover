package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class WebAppInterface(
    private val context: Context,
    private val callback: (Boolean, String?) -> Unit
) {

    @JavascriptInterface
    fun saveImage(base64: String, fileName: String) {
        try {
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            // 1. Save to Public MediaStore (for Google Photos / System Gallery)
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIPhotoStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri: Uri? = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                val stream: OutputStream? = resolver.openOutputStream(it)
                stream?.write(bytes)
                stream?.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }

                // 2. Save to Internal Storage (for the app's native GalleryActivity)
                saveToInternalStorage(bytes, fileName)

                callback(true, it.toString())
            } ?: callback(false, null)

        } catch (e: Exception) {
            callback(false, null)
        }
    }

    private fun saveToInternalStorage(bytes: ByteArray, fileName: String) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
            val userDir = File(context.filesDir, "saved_images/$userId")
            if (!userDir.exists()) userDir.mkdirs()
            
            val file = File(userDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
