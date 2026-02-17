package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

/**
 * Interface for JavaScript to interact with the native app.
 *
 * @property context The application context.
 * @property onBackgroundPickerRequested Callback for when background picker is requested.
 * @property onGoogleSignInRequested Callback to trigger native Google Sign-In.
 * @property callback General purpose callback for results.
 */
@Suppress("SpellCheckingInspection", "HardcodedStringLiteral")
class WebAppInterface(
    private val context: Context,
    private val onBackgroundPickerRequested: () -> Unit,
    private val onGoogleSignInRequested: () -> Unit,
    private val callback: (Boolean, String?) -> Unit
) {

    /**
     * Saves an image from base64 string.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveImage(base64: String) {
        saveToDevice(base64, null)
    }

    /**
     * Saves an image with a specific filename.
     * @param base64 The base64 image data.
     * @param fileName The target filename.
     */
    @JavascriptInterface
    fun saveImage(base64: String, fileName: String?) {
        saveToDevice(base64, fileName)
    }

    /**
     * Saves to device with default naming.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveToDevice(base64: String) {
        saveToDevice(base64, null)
    }

    /**
     * Core save function.
     * @param base64 The base64 image data.
     * @param fileName Optional filename.
     */
    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String?) {
        try {
            val name = fileName ?: "AI_Studio_${System.currentTimeMillis()}.png"
            val comma = ","
            val cleanBase64 = if (base64.contains(comma)) {
                base64.substringAfter(comma)
            } else {
                base64
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIPhotoStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let { itemUri ->
                resolver.openOutputStream(itemUri)?.use { stream ->
                    stream.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues()
                    updateValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, updateValues, null, null)
                }

                saveToInternalStorage(bytes, name)
                callback(true, itemUri.toString())
            } ?: callback(false, context.getString(R.string.save_failed, "MediaStore error"))

        } catch (e: Exception) {
            Log.e("WebAppInterface", "Error saving image", e)
            callback(false, e.message)
        }
    }

    /**
     * Triggers native background picker.
     */
    @JavascriptInterface
    fun showBackgroundPicker() {
        onBackgroundPickerRequested()
    }

    /**
     * Triggers native Google Sign-In.
     */
    @JavascriptInterface
    fun googleSignIn() {
        onGoogleSignInRequested()
    }

    /**
     * Returns the current user ID or null if not signed in.
     */
    @JavascriptInterface
    fun getUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Displays a toast message.
     * @param message The text to show.
     */
    @JavascriptInterface
    fun showToast(message: String) {
        callback(false, message)
    }

    /**
     * Private storage helper.
     * @param bytes Image data.
     * @param fileName Target filename.
     */
    private fun saveToInternalStorage(bytes: ByteArray, fileName: String) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
            val userDir = File(context.filesDir, "saved_images/$userId")
            if (!userDir.exists() && !userDir.mkdirs()) {
                Log.e("WebAppInterface", "Failed to create directory")
            }
            val file = File(userDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(bytes)
            }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Internal save failed", e)
        }
    }
}
