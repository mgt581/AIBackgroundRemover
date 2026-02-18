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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Interface for JavaScript to interact with the native app.
 *
 * @property context The application context.
 * @property onBackgroundPickerRequested Callback to trigger the native background picker.
 * @property onGoogleSignInRequested Callback to trigger native Google Sign-In.
 * @property onLoginRequested Callback to trigger the native login screen.
 * @property onLoginSuccess Callback executed after a successful login.
 * @property callback General purpose callback for results.
 */
@Suppress("unused", "HardcodedStringLiteral", "SpellCheckingInspection")
class WebAppInterface(
    private val context: Context,
    private val onBackgroundPickerRequested: () -> Unit,
    private val onGoogleSignInRequested: () -> Unit,
    private val onLoginRequested: () -> Unit,
    private val onLoginSuccess: () -> Unit,
    private val callback: (Boolean, String?) -> Unit
) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    @JavascriptInterface
    fun saveImage(base64: String) {
        saveToDevice(base64, null)
    }

    @JavascriptInterface
    fun saveImage(base64: String, fileName: String?) {
        saveToDevice(base64, fileName)
    }

    @JavascriptInterface
    fun saveToGallery(base64: String) {
        val name = "img_${System.currentTimeMillis()}.png"
        saveToDevice(base64, name)
    }

    @JavascriptInterface
    fun downloadImage(base64: String) {
        saveToDevice(base64, null)
    }

    @JavascriptInterface
    fun saveToDevice(base64: String) {
        saveToDevice(base64, null)
    }

    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String?) {
        Log.d(TAG, "saveToDevice called")
        try {
            val name = fileName ?: "AI_Studio_${System.currentTimeMillis()}.png"
            val comma = ","
            val cleanBase64 = if (base64.contains(comma)) {
                base64.substringAfter(comma)
            } else {
                base64
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

            saveToMediaStore(bytes, name)
            saveToInternalStorage(bytes, name)
            uploadToFirebase(bytes, name)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            callback(false, e.message)
        }
    }

    private fun saveToMediaStore(bytes: ByteArray, name: String) {
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
            callback(true, itemUri.toString())
        } ?: callback(false, context.getString(R.string.error_mediastore))
    }

    private fun uploadToFirebase(bytes: ByteArray, fileName: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid
        val storageRef = storage.reference.child("users/$userId/gallery/${UUID.randomUUID()}.png")

        storageRef.putBytes(bytes)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveMetadataToFirestore(userId, uri.toString(), fileName)
                }
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Firebase Storage upload failed", e)
            }
    }

    private fun saveMetadataToFirestore(userId: String, downloadUrl: String, fileName: String) {
        val photoData = hashMapOf(
            "url" to downloadUrl,
            "title" to fileName,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users")
            .document(userId)
            .collection("gallery")
            .add(photoData)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore metadata saved")
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Firestore metadata save failed", e)
            }
    }

    @JavascriptInterface
    fun showBackgroundPicker() {
        onBackgroundPickerRequested()
    }

    @JavascriptInterface
    fun googleSignIn() {
        onGoogleSignInRequested()
    }

    @JavascriptInterface
    fun requestLogin() {
        onLoginRequested()
    }

    @JavascriptInterface
    fun getUserId(): String? {
        return auth.currentUser?.uid
    }

    @JavascriptInterface
    fun showToast(message: String) {
        callback(false, message)
    }

    private fun saveToInternalStorage(bytes: ByteArray, fileName: String) {
        try {
            val userId = auth.currentUser?.uid ?: "guest"
            val userDir = File(context.filesDir, "saved_images/$userId")
            if (!userDir.exists() && !userDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory")
                return
            }
            val file = File(userDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Internal save failed", e)
        }
    }

    companion object {
        private const val TAG = "WebAppInterface"
    }
}
