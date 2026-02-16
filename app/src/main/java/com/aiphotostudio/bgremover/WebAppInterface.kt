package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.OutputStream

class WebAppInterface(
    private val context: Context,
    private val callback: (Boolean, String?) -> Unit
) {

    @JavascriptInterface
    fun saveImage(base64: String, fileName: String) {
        try {
            val cleanBase64 = base64.substringAfter(",")
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

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

                callback(true, it.toString())
            } ?: callback(false, null)

        } catch (e: Exception) {
            callback(false, null)
        }
    }
}
