package com.aiphotostudio.bgremover

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * Main application class for AI Background Remover.
 */
class AIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Firebase.initialize(this)
        
        val appCheck = Firebase.appCheck
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}
