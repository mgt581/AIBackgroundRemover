package com.aiphotostudio.bgremover

import android.app.Application
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

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
            FirebaseApp.initializeApp(this)
            
            val appCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                Log.d("AIApplication", "App Check: Installing DebugAppCheckProviderFactory")
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                val playServicesStatus = GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this)
                if (shouldEnablePlayIntegrity(playServicesStatus)) {
                    Log.d("AIApplication", "App Check: Installing PlayIntegrityAppCheckProviderFactory")
                    appCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                    )
                } else {
                    Log.w(
                        "AIApplication",
                        "App Check disabled: Play Services unavailable (${GoogleApiAvailability.getInstance().getErrorString(playServicesStatus)})"
                    )
                }
            }
            appCheck.setTokenAutoRefreshEnabled(true)

            Log.d("AIApplication", "Firebase and App Check initialized successfully")
        } catch (e: Exception) {
            Log.e("AIApplication", "Firebase initialization failed", e)
        }
    }

    enum class BuildConfig {
        ;

        companion object {
            val DEBUG: Boolean = false
        }

    }
}
