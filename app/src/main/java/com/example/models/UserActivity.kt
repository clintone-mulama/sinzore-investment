package com.example.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Model representing a single user activity record.
 */
data class UserActivity(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val activityType: String = "", // e.g. "Loan Application", "Contribution Payment", "Loan Repayment", "Loan Repayment Completion", "Manual Contribution Record Added", "Manual Loan Record Added"
    val amount: Double = 0.0,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val dateStr: String = "",
    @get:PropertyName("isTreasuryAction")
    @PropertyName("isTreasuryAction")
    val isTreasuryAction: Boolean = false,
    @ServerTimestamp
    val serverTimestamp: Date? = null
)
