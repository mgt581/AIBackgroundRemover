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
 */
class WebAppInterface(
    /** The application context used for file operations and resolver. */
    private val context: Context,
    /** Callback triggered when the web requests a background picker. */
    private val onBackgroundPickerRequested: () -> Unit,
    /** Callback to return success status and a message or URI to the web. */
    private val callback: (Boolean, String?) -> Unit
) {

    /**
     * Saves an image from a base64 string.
     *
     * @param base64 The base64 encoded image data.
     * @param fileName Optional filename for the image.
     */
    @JavascriptInterface
    fun saveImage(base64: String, fileName: String? = null) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SAVE_IMAGE)
        }
        saveToDevice(base64, fileName)
    }

    /**
     * Saves an image to the gallery from a base64 string.
     *
     * @param base64 The base64 encoded image data.
     */
    @JavascriptInterface
    fun saveToGallery(base64: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SAVE_GALLERY)
        }
        val fileName = "$FILE_NAME_PREFIX${System.currentTimeMillis()}$FILE_NAME_EXT"
        saveToDevice(base64, fileName)
    }

    /**
     * Fallback for saveToGallery when no arguments are passed.
     */
    @JavascriptInterface
    fun saveToGallery() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SAVE_GALLERY_NO_ARGS)
        }
        callback(false, context.getString(R.string.no_saved_images))
    }

    /**
     * Saves image data to the device's public storage.
     *
     * @param base64 The base64 encoded image data.
     * @param fileName Optional filename for the image.
     */
    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String? = null) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SAVE_DEVICE)
        }
        try {
            val name = fileName ?: "$STUDIO_NAME_PREFIX${System.currentTimeMillis()}$FILE_NAME_EXT"
            val cleanBase64 = if (base64.contains(COMMA)) {
                base64.substringAfter(COMMA)
            } else {
                base64
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            val resolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name)
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_PNG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_PATH)
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
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
            } ?: callback(false, context.getString(R.string.save_failed, ERROR_MEDIASTORE))

        } catch (e: Exception) {
            Log.e(TAG, ERROR_SAVING_IMAGE, e)
            callback(false, e.message)
        }
    }

    /**
     * Downloads an image from a base64 string.
     *
     * @param base64 The base64 encoded image data.
     */
    @JavascriptInterface
    fun downloadImage(base64: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_DOWNLOAD_IMAGE)
        }
        saveToDevice(base64)
    }

    /**
     * Fallback for downloadImage when no arguments are passed.
     */
    @JavascriptInterface
    fun downloadImage() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_DOWNLOAD_IMAGE_NO_ARGS)
        }
        callback(false, context.getString(R.string.no_saved_images))
    }

    /**
     * Requests the native background picker to be shown.
     */
    @JavascriptInterface
    fun showBackgroundPicker() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SHOW_PICKER)
        }
        onBackgroundPickerRequested()
    }

    /**
     * Shows a toast message from JavaScript.
     *
     * @param message The message to show.
     */
    @JavascriptInterface
    fun showToast(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, LOG_SHOW_TOAST)
        }
        callback(false, message)
    }

    /**
     * Saves the image to internal storage.
     *
     * @param bytes The image data bytes.
     * @param fileName The filename to save as.
     */
    private fun saveToInternalStorage(bytes: ByteArray, fileName: String) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: GUEST_ID
            val dirPath = "$INTERNAL_DIR_PREFIX$userId"
            val userDir = File(context.filesDir, dirPath)
            if (!userDir.exists() && !userDir.mkdirs()) {
                Log.e(TAG, ERROR_DIR_CREATE)
            }
            val file = File(userDir, fileName)
            FileOutputStream(file).use { /**
                                          *
                 */
                                         outputStream ->
                outputStream.write(bytes)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, LOG_INTERNAL_SAVED)
            }
        } catch (e: Exception) {
            Log.e(TAG, ERROR_INTERNAL_SAVE, e)
        }
    }

    companion object {
        private const val TAG = "WebAppInterface"
        private const val GUEST_ID = "guest"
        private const val MIME_TYPE_PNG = "image/png"
        private const val PICTURES_PATH = "Pictures/AIPhotoStudio"
        private const val INTERNAL_DIR_PREFIX = "saved_images/"
        private const val FILE_NAME_PREFIX = "img_"
        private const val FILE_NAME_EXT = ".png"
        private const val STUDIO_NAME_PREFIX = "AI_Studio_"
        private const val COMMA = ","

        private const val LOG_SAVE_IMAGE = "saveImage called"
        private const val LOG_SAVE_GALLERY = "saveToGallery called"
        private const val LOG_SAVE_GALLERY_NO_ARGS = "saveToGallery no args"
        private const val LOG_SAVE_DEVICE = "saveToDevice called"
        private const val LOG_DOWNLOAD_IMAGE = "downloadImage called"
        private const val LOG_DOWNLOAD_IMAGE_NO_ARGS = "downloadImage no args"
        private const val LOG_SHOW_PICKER = "showBackgroundPicker called"
        private const val LOG_SHOW_TOAST = "showToast called"
        private const val LOG_INTERNAL_SAVED = "Saved to internal storage"

        private const val ERROR_MEDIASTORE = "MediaStore error"
        private const val ERROR_SAVING_IMAGE = "Error saving image"
        private const val ERROR_DIR_CREATE = "Failed to create directory"
        private const val ERROR_INTERNAL_SAVE = "Internal save failed"
    }
}
