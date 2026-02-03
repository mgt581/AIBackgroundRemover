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
        Log.w("UrlUtils", "No activity found to open URL: $url")
    }
}
