package com.aiphotostudio.bgremover

import android.app.Application
import com.google.android.gms.common.ConnectionResult
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class AIApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        // Initialize App Check with Play Integrity
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }

    companion object {
        @JvmStatic
        fun shouldEnablePlayIntegrity(connectionResult: Int): Boolean {
            return connectionResult == ConnectionResult.SUCCESS
        }
    }
}
