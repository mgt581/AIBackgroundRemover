package com.aiphotostudio.bgremover

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

fun Context.openUrl(url: String) {
    if (url.isBlank()) {
        Log.w("UrlUtils", "Empty URL provided; skipping openUrl call")
        return
    }
    val parsedUri = Uri.parse(url)
    val scheme = parsedUri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        Log.w("UrlUtils", "Unsupported URL scheme; skipping openUrl call")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, parsedUri)
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.w("UrlUtils", "No browser available to open URL")
    }
}
