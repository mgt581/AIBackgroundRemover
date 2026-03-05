package com.aiphotostudio.bgremover

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.OutputStream

class AndroidSaveBridge(private val context: Context) {

    @JavascriptInterface
    fun saveBase64Image(base64: String, filename: String, mime: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Photo Studio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("MediaStore insert failed")

            var out: OutputStream? = null
            try {
                out = resolver.openOutputStream(uri)
                    ?: throw Exception("OpenOutputStream failed")
                out.write(bytes)
                out.flush()
            } finally {
                out?.close()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            if (context is MainActivity) {
                context.runOnUiThread {
                    Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            if (context is MainActivity) {
                context.runOnUiThread {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
