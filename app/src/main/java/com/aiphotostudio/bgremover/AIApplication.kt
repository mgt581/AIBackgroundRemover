package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

class AIApplication : Application() {
    
    companion object {
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
            }

            Log.d("AIApplication", "Firebase and App Check initialized successfully")
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }
}
