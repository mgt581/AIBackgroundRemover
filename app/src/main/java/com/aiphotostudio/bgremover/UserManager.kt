package com.aiphotostudio.bgremover

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

object UserManager {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    var credits: Int = 0
    var freeDailyCredits: Int = 0
    var isSubscribed: Boolean = false

    fun fetchUserData(onComplete: (Int, Boolean) -> Unit = { _, _ -> }) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // If not logged in, try anonymous sign-in for a smoother experience
            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    fetchUserData(onComplete)
                } else {
                    onComplete(credits + freeDailyCredits, isSubscribed)
                }
            }
            return
        }
        val uid = currentUser.uid

        // Owner Mode: Check if current user is the developer
        val isOwner = currentUser.email == "alexbryant3234@gmail.com"

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    credits = document.getLong("credits")?.toInt() ?: 0
                    freeDailyCredits = document.getLong("freeDailyCredits")?.toInt() ?: 0
                    isSubscribed = isOwner || (document.getBoolean("isSubscribed") ?: false)
                    checkDailyRefresh(document.getLong("lastRefreshDate") ?: 0)
                } else {
                    // Initialize new user
                    credits = 0
                    freeDailyCredits = 1
                    isSubscribed = isOwner
                    updateFirestore(System.currentTimeMillis())
                }
                onComplete(credits + freeDailyCredits, isSubscribed)
            }
            .addOnFailureListener { e ->
                Log.e("UserManager", "Error fetching user data", e)
                onComplete(credits + freeDailyCredits, isOwner || isSubscribed)
            }
    }

    private fun checkDailyRefresh(lastRefreshMillis: Long) {
        val lastRefresh = Calendar.getInstance().apply { timeInMillis = lastRefreshMillis }
        val now = Calendar.getInstance()

        val isDifferentDay = lastRefresh[Calendar.DAY_OF_YEAR] != now[Calendar.DAY_OF_YEAR] ||
                lastRefresh[Calendar.YEAR] != now[Calendar.YEAR]

        if (isDifferentDay) {
            // New day: Reset free credit (no rollover)
            freeDailyCredits = 1
            updateFirestore(now.timeInMillis)
        }
    }

    private fun updateFirestore(refreshDate: Long? = null) {
        val uid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any>(
            "credits" to credits,
            "freeDailyCredits" to freeDailyCredits,
            "isSubscribed" to isSubscribed,
        )
        refreshDate?.let { data["lastRefreshDate"] = it }

        db.collection("users").document(uid).set(data, SetOptions.merge())
    }

    fun spendCredit(isObjectRemoval: Boolean, onResult: (Boolean, Int) -> Unit) {
        if (isSubscribed) {
            onResult(true, credits + freeDailyCredits)
            return
        }

        if (isObjectRemoval) {
            // Object removal MUST use paid credits
            if (credits > 0) {
                credits--
                updateFirestore()
                onResult(true, credits + freeDailyCredits)
            } else {
                onResult(false, credits + freeDailyCredits)
            }
        } else {
            // Background removal can use free or paid credits
            if (freeDailyCredits > 0) {
                freeDailyCredits--
                updateFirestore()
                onResult(true, credits + freeDailyCredits)
            } else if (credits > 0) {
                credits--
                updateFirestore()
                onResult(true, credits + freeDailyCredits)
            } else {
                onResult(false, credits + freeDailyCredits)
            }
        }
    }

    fun addCredits(amount: Int) {
        credits += amount
        updateFirestore()
    }

    fun setSubscription(active: Boolean) {
        isSubscribed = active
        updateFirestore()
    }
}
