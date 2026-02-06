package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

private val Firebase.appCheck: kotlin.Any

class AIApplication : Application() {
    
    companion object {
        private const val LOG_SEPARATOR = "================================================================================"
        
        internal fun shouldEnablePlayIntegrity(playServicesStatus: Int): Boolean {
            return playServicesStatus == ConnectionResult.SUCCESS
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase
            Firebase.initialize(this)
            
            val appCheck = Firebase.appCheck

            if (BuildConfig.DEBUG) {
                Log.d("AIApplication", LOG_SEPARATOR)
                Log.d("AIApplication", "App Check: Installing DebugAppCheckProviderFactory")
                Log.d("AIApplication", "IMPORTANT: Look for DebugAppCheckProvider log message below")
                Log.d("AIApplication", "You MUST register the debug token in Firebase Console!")
                Log.d("AIApplication", "Firebase Console > Project Settings > App Check > Manage debug tokens")
                Log.d("AIApplication", LOG_SEPARATOR)
                // IMPORTANT: Find the debug secret in Logcat and add it to Firebase Console
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheck.setTokenAutoRefreshEnabled(true)
            } else {
                val playServicesStatus = GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this)
                if (shouldEnablePlayIntegrity(playServicesStatus)) {
                    Log.d("AIApplication", "App Check: Installing PlayIntegrityAppCheckProviderFactory")
                    appCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                    appCheck.setTokenAutoRefreshEnabled(true)
                } else {
                    Log.w(
                        "AIApplication",
                        "App Check disabled: Play Services unavailable (${GoogleApiAvailability.getInstance().getErrorString(playServicesStatus)})"
                    )
                    appCheck.setTokenAutoRefreshEnabled(false)
                }
            }

            Log.d("AIApplication", "Firebase and App Check initialized successfully")
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }
}
