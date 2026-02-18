package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlin.concurrent.thread

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
@Suppress("unused")
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

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Triggers the native login screen.
     */
    @JavascriptInterface
    fun requestLogin() {
        onLoginRequested()
    }

    /**
     * Saves an image from base64 string.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveImage(base64: String) {
        saveToDevice(base64, null)
    }

    /**
     * Saves an image to the gallery.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveToGallery(base64: String) {
        saveToDevice(base64, null)
    }

    /**
     * Saves an image to the device and uploads it to Firebase.
     * @param base64 The base64 image data.
     * @param fileName The target filename.
     *
     * IMPORTANT: This runs heavy work off the UI/WebView thread to avoid ANR freezes.
     */
    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String?) {
        // Offload heavy decode + IO work so the phone doesn't freeze
        thread(name = "SaveToDeviceThread") {
            try {
                val name = fileName ?: "AI_Studio_${System.currentTimeMillis()}.png"
                val cleanBase64 = if (base64.contains(COMMA)) {
                    base64.substringAfter(COMMA)
                } else {
                    base64
                }

                Log.d(TAG, "saveToDevice called. base64Len=${cleanBase64.length} name=$name")

                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                // Save locally
                val savedUri = saveToMediaStore(bytes, name)

                // Tell JS/UI we saved locally
                if (savedUri != null) {
                    postResult(true, savedUri.toString())
                } else {
                    postResult(false, context.getString(R.string.error_mediastore))
                    return@thread
                }

                // Upload to cloud (if signed in)
                uploadToFirebase(bytes, name)

            } catch (e: Exception) {
                Log.e(TAG, "saveToDevice failed", e)
                postResult(false, e.message)
            }
        }
    }

    /**
     * Saves the image bytes to the device's MediaStore.
     * @param bytes The image data in bytes.
     * @param name The filename for the image.
     * @return Uri if saved, null otherwise.
     */
    private fun saveToMediaStore(bytes: ByteArray, name: String): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_IMAGE_PNG)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_SUB_DIRECTORY)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let { itemUri ->
                context.contentResolver.openOutputStream(itemUri)?.use { outputStream ->
                    outputStream.write(bytes)
                }
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "saveToMediaStore error", e)
            null
        }
    }

    /**
     * Uploads the image to Firebase Storage and saves metadata to Firestore.
     * @param bytes The image data in bytes.
     * @param fileName The filename for the image.
     */
    private fun uploadToFirebase(bytes: ByteArray, fileName: String) {
        val user = auth.currentUser
        if (user == null) {
            // Don't fail the local save — just inform that cloud save needs sign-in
            postResult(false, "Saved to device. Sign in to save to cloud gallery.")
            return
        }

        val userId = user.uid
        val storageRef = storage.reference.child("users/$userId/gallery/${UUID.randomUUID()}.png")

        storageRef.putBytes(bytes)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri: Uri ->
                    val data = hashMapOf(
                        "url" to uri.toString(),
                        "title" to fileName,
                        "createdAt" to Timestamp.now()
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("gallery")
                        .add(data)
                        .addOnSuccessListener {
                            postResult(true, "Saved to cloud gallery")
                        }
                        .addOnFailureListener { e ->
                            postResult(false, "Cloud metadata save failed: ${e.message}")
                        }
                }.addOnFailureListener { e ->
                    postResult(false, "Failed to get download URL: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                postResult(false, "Cloud upload failed: ${e.message}")
            }
    }

    /**
     * Triggers native background picker.
     */
    @JavascriptInterface
    fun showBackgroundPicker() = onBackgroundPickerRequested()

    /**
     * Triggers native Google Sign-In.
     */
    @JavascriptInterface
    fun googleSignIn() = onGoogleSignInRequested()

    /**
     * Returns the current user ID or null if not signed in.
     * @return The user ID or null.
     */
    @JavascriptInterface
    fun getUserId(): String? = auth.currentUser?.uid

    /**
     * Navigates to the native Gallery Activity.
     */
    @JavascriptInterface
    fun openGallery() {
        try {
            val intent = Intent(context, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            postResult(false, "Failed to open Gallery: ${e.message}")
        }
    }

    /**
     * Navigates to the native Settings Activity.
     */
    @JavascriptInterface
    fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            postResult(false, "Failed to open Settings: ${e.message}")
        }
    }

    private fun postResult(success: Boolean, message: String?) {
        mainHandler.post {
            callback(success, message)
        }
    }

    companion object {
        private const val TAG = "WebAppInterface"
        private const val COMMA = ","
        private const val MIME_TYPE_IMAGE_PNG = "image/png"
        private const val PICTURES_SUB_DIRECTORY = "Pictures/AIPhotoStudio"
    }
}
