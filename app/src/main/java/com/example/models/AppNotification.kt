package com.example.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Model representing a shared application notification/announcement.
 */
data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String = "",
    val type: String = "announcement", // e.g., "announcement", "member_joined", "loan_decision", "manual_record", "member_removed", "loan_repayment", "balance_update"
    @ServerTimestamp
    val serverTimestamp: Date? = null
)
