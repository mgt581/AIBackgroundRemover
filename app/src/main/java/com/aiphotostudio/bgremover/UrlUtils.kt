package com.aiphotostudio.bgremover

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        val tag = this::class.java.simpleName.ifEmpty { "UrlUtils" }
        Log.w(tag, "No activity found to open URL: $url")
    }
}
