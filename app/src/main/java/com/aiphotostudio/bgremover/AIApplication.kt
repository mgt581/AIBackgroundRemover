package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.aiphotostudiobgremover.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class AIApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase on main thread (required)
        try {
            FirebaseApp.initializeApp(this)
            Log.d("AIApplication", "Firebase initialized successfully")
            
            // Move App Check to background to avoid blocking main thread
            Thread {
                setupAppCheck()
            }.start()
            
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }

    private fun setupAppCheck() {
        try {
            val appCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                Log.d("AIApplication", "App Check: Installing DebugAppCheckProviderFactory")
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                val playServicesStatus = GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this)
                if (playServicesStatus == ConnectionResult.SUCCESS) {
                    Log.d("AIApplication", "App Check: Installing PlayIntegrityAppCheckProviderFactory")
                    appCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                } else {
                    Log.w("AIApplication", "App Check disabled: Play Services unavailable ($playServicesStatus)")
                }
            }
            appCheck.setTokenAutoRefreshEnabled(true)
            Log.d("AIApplication", "App Check configured successfully")
        } catch (e: Exception) {
            Log.e("AIApplication", "App Check configuration failed", e)
        }
    }
}
