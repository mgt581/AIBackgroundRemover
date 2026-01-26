package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class AIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            Firebase.initialize(this)
            
            // Bypass App Check in Debug to allow login during development
            if (!BuildConfig.DEBUG) {
                Log.d("AIApplication", "Installing PlayIntegrityAppCheckProviderFactory")
                val appCheck = Firebase.appCheck
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            } else {
                Log.d("AIApplication", "App Check bypassed in Debug mode")
            }
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }
}
