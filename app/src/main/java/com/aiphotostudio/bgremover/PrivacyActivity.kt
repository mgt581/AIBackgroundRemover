package com.aiphotostudio.bgremover

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PrivacyActivity : AppCompatActivity() {

    private fun openPage(title: String, url: String) {
        startActivity(
            Intent(this, WebPageActivity::class.java).apply {
                putExtra(WebPageActivity.EXTRA_TITLE, title)
                putExtra(WebPageActivity.EXTRA_URL, url)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openPage("Privacy Policy", "https://aiphotostudio.co.uk/privacy.html")
        finish()
    }
}
