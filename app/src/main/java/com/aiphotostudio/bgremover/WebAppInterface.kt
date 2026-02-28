package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
@Suppress("unused", "HardcodedString")
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

    /**
     * Triggers the native login screen.
     */
    @JavascriptInterface
    fun requestLogin() {
        Log.d(TAG, "requestLogin called")
        onLoginRequested()
    }

    /**
     * Saves an image from base64 string.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveImage(base64: String) {
        Log.d(TAG, "saveImage called")
        saveToDevice(base64, null)
    }

    /**
     * Saves an image to the gallery.
     * Signed-in users only.
     * @param base64 The base64 image data.
     */
    @JavascriptInterface
    fun saveToGallery(base64: String) {
        Log.d(TAG, "saveToGallery called")
        val user = auth.currentUser
        if (user == null) {
            Log.d(TAG, "User not signed in, requesting login")
            onLoginRequested()
            return
        }

        saveToDevice(base64, null)
    }

    /**
     * Saves an image to the device.
     * Uploads to Firebase ONLY if signed in.
     * @param base64 The base64 image data.
     * @param fileName The target filename.
     */
    @JavascriptInterface
    fun saveToDevice(base64: String, fileName: String?) {
        Log.d(TAG, "saveToDevice called with fileName: $fileName")
        try {
            val name = fileName ?: "AI_Studio_${System.currentTimeMillis()}.png"

            val cleanBase64 = if (base64.contains(COMMA)) {
                base64.substringAfter(COMMA)
            } else {
                base64
            }

            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            Log.d(TAG, "Decoded base64, size: ${bytes.size} bytes")

            // ✅ Always save locally
            saveToMediaStore(bytes, name)

            // ✅ Upload ONLY if signed in
            if (auth.currentUser != null) {
                Log.d(TAG, "User is signed in, starting Firebase upload")
                uploadToFirebase(bytes, name)
            } else {
                Log.d(TAG, "User not signed in, skipping Firebase upload")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in saveToDevice: ${e.message}", e)
            callback(false, e.message)
        }
    }

    /**
     * Saves the image bytes to the device's MediaStore.
     *
     * @param bytes The image data in bytes.
     * @param name The filename for the image.
     */
    private fun saveToMediaStore(bytes: ByteArray, name: String) {
        Log.d(TAG, "Saving to MediaStore: $name")
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
            Log.d(TAG, "Successfully saved to MediaStore: $itemUri")
            callback(true, itemUri.toString())
        } ?: run {
            Log.e(TAG, "Failed to insert into MediaStore")
            callback(false, context.getString(R.string.error_mediastore))
        }
    }

    /**
     * Uploads the image to Firebase Storage and saves metadata to Firestore.
     * Only runs if user is signed in.
     *
     * @param bytes The image data in bytes.
     * @param fileName The original filename.
     */
    private fun uploadToFirebase(bytes: ByteArray, fileName: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid

        val storagePath = "$COLLECTION_USERS/$userId/$COLLECTION_GALLERY/${UUID.randomUUID()}.png"
        val storageRef = storage.reference.child(storagePath)

        Log.d(TAG, "Uploading to Firebase Storage: $storagePath")
        storageRef.putBytes(bytes)
            .addOnSuccessListener {
                Log.d(TAG, "Firebase Storage upload success")
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Log.d(TAG, "Got download URL: $downloadUri")
                    val data = hashMapOf(
                        KEY_URL to downloadUri.toString(),
                        KEY_TITLE to fileName,
                        KEY_CREATED_AT to com.google.firebase.Timestamp.now()
                    )

                    db.collection(COLLECTION_USERS)
                        .document(userId)
                        .collection(COLLECTION_GALLERY)
                        .add(data)
                        .addOnSuccessListener {
                            Log.d(TAG, "Firestore metadata save success")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore metadata save failed", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firebase Storage upload failed", e)
            }
    }

    /**
     * Triggers native background picker.
     */
    @JavascriptInterface
    fun showBackgroundPicker() {
        Log.d(TAG, "showBackgroundPicker called")
        onBackgroundPickerRequested()
    }

    /**
     * Triggers native Google Sign-In.
     */
    @JavascriptInterface
    fun googleSignIn() {
        Log.d(TAG, "googleSignIn called")
        onGoogleSignInRequested()
    }

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
        Log.d(TAG, "openGallery called")
        try {
            val intent = Intent(context, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening gallery", e)
            callback(false, context.getString(R.string.save_failed, e.message ?: context.getString(R.string.save_failed_msg)))
        }
    }

    /**
     * Navigates to the native Settings Activity.
     */
    @JavascriptInterface
    fun openSettings() {
        Log.d(TAG, "openSettings called")
        try {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening settings", e)
            callback(false, context.getString(R.string.save_failed, e.message ?: context.getString(R.string.save_failed_msg)))
        }
    }

    companion object {
        private const val TAG = "WebAppInterface"
        private const val COMMA = ","
        private const val MIME_TYPE_IMAGE_PNG = "image/png"
        private const val PICTURES_SUB_DIRECTORY = "Pictures/AIPhotoStudio"

        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_GALLERY = "gallery"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_CREATED_AT = "createdAt"
    }
}
