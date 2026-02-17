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

/**
 * Interface for JavaScript to interact with the native app.
 */
class WebAppInterface(
    /**
     *
     */
    private val context: Context,
    private val onBackgroundPickerRequested: () -> Unit,
    /**
     *
     */
    private val callback: (Boolean, String?) -> Unit
) {

    /**
     * Saves an image from a base64 string.
     */
    @JavascriptInterface
    fun saveImage(base64: String, fileName: String? = null) {
        saveToDevice(base64, fileName)
    }

    /**
     * Saves an image to the gallery from a base64 string.
     */
    @JavascriptInterface
    fun saveToGallery(base64: String) {
        val name = "img_" + System.currentTimeMillis().toString() + ".png"
        saveToDevice(base64, name)
    }

    /**
     * Fallback for saveToGallery when no arguments are passed.
     */
    @JavascriptInterface
    fun saveToGallery() {
        callback(false, context.getString(R.string.no_saved_images))
    }

    /**
     * Saves image data to the device's public storage.
     */
    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String? = null) {
        try {
            val name = fileName ?: ("AI_Studio_" + System.currentTimeMillis().toString() + ".png")
            val comma = ","
            val cleanBase64 = if (base64.contains(comma)) {
                base64.substringAfter(comma)
            } else {
                base64
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val resolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIPhotoStudio")
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues()
                    updateValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, updateValues, null, null)
                }

                saveToInternalStorage(bytes, name)
                callback(true, uri.toString())
            } else {
                callback(false, context.getString(R.string.save_failed, "MediaStore"))
            }

        } catch (e: Exception) {
            callback(false, e.message)
        }
    }

    /**
     * Downloads an image from a base64 string.
     */
    @JavascriptInterface
    fun downloadImage(base64: String) {
        saveToDevice(base64)
    }

    /**
     * Fallback for downloadImage when no arguments are passed.
     */
    @JavascriptInterface
    fun downloadImage() {
        callback(false, context.getString(R.string.no_saved_images))
    }

    /**
     * Requests the native background picker to be shown.
     */
    @JavascriptInterface
    fun showBackgroundPicker() {
        onBackgroundPickerRequested()
    }

    /**
     * Shows a toast message from JavaScript.
     */
    @JavascriptInterface
    fun showToast(message: String) {
        callback(false, message)
    }

    /**
     * Saves the image to internal storage.
     */
    private fun saveToInternalStorage(bytes: ByteArray,/**
     *
     */
    fileName: String) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            val userId = user?.uid ?: "guest"
            val dirPath = "saved_images/" + userId
            val userDir = File(context.filesDir, dirPath)
            if (!userDir.exists()) {
                userDir.mkdirs()
            }
            val file = File(userDir, fileName)
            FileOutputStream(file).use { stream ->
                stream.write(bytes)
            }
        } catch (e: Exception) {
            // Error ignored
        }
    }
}
