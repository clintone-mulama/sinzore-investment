package com.example.common

import com.example.models.UserActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityLogger {
    fun logActivity(
        userId: String,
        userName: String,
        activityType: String,
        amount: Double,
        details: String,
        isTreasuryAction: Boolean = false
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("activities").document()
            val now = Date()
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
            val activity = UserActivity(
                id = docRef.id,
                userId = userId,
                userName = userName,
                activityType = activityType,
                amount = amount,
                details = details,
                timestamp = System.currentTimeMillis(),
                dateStr = dateStr,
                isTreasuryAction = isTreasuryAction,
                serverTimestamp = null
            )
            docRef.set(activity)
        } catch (e: Exception) {
            android.util.Log.e("ActivityLogger", "Failed to log activity: ${e.message}", e)
        }
    }
}
