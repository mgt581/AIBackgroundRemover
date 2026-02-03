package com.aiphotostudio.bgremover

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log

fun Context.openUrl(url: String) {
    if (url.isBlank()) {
        Log.w("UrlUtils", "Empty URL provided; skipping openUrl call (${this.javaClass.simpleName})")
        return
    }
    val parsedUri = Uri.parse(url)
    val scheme = parsedUri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        Log.w("UrlUtils", "Unsupported URL scheme ($scheme); skipping openUrl call")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, parsedUri)
    if (this !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        val safeUrl = buildString {
            append(parsedUri.buildUpon().clearQuery().fragment(null).build().toString())
            if (!parsedUri.query.isNullOrBlank()) append("?…")
            if (!parsedUri.fragment.isNullOrBlank()) append("#…")
        }
        Log.w("UrlUtils", "No browser available to open URL $safeUrl")
    }
}
