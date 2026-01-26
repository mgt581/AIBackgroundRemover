package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class AIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            Firebase.initialize(this)
            
            val appCheck = Firebase.appCheck
            if (BuildConfig.DEBUG) {
                Log.d("AIApplication", "Installing DebugAppCheckProviderFactory")
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                Log.d("AIApplication", "Installing PlayIntegrityAppCheckProviderFactory")
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }
}
