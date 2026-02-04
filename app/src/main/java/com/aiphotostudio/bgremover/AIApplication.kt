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

class AIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Firebase
            Firebase.initialize(this)
            
            val appCheck = Firebase.appCheck

            if (BuildConfig.DEBUG) {
                Log.d("AIApplication", "App Check: Installing DebugAppCheckProviderFactory")
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

    companion object {
        internal fun shouldEnablePlayIntegrity(playServicesStatus: Int): Boolean {
            return playServicesStatus == ConnectionResult.SUCCESS
        }
    }
}
