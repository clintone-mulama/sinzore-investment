package com.example.models

import java.util.Date

/**
 * Model representing a single loan repayment installment.
 */
data class Repayment(
    val repaymentId: String = "",
    val loanId: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "M-PESA",
    val receiptNumber: String = "",
    val transactionId: String = "", // Matches spec
    val source: String = "Member",
    val recordedBy: String = "",
    val date: Any? = null, // Supports Long, Timestamp, Date, or String
    val exactDate: String = "",
    val exactTime: String = "",
    val firestoreServerTimestamp: Date? = null
) {
    val dateAsLong: Long
        get() {
            return when (val d = date) {
                is Long -> d
                is Number -> d.toLong()
                is String -> d.toLongOrNull() ?: 0L
                is com.google.firebase.Timestamp -> d.seconds * 1000
                is Date -> d.time
                else -> 0L
            }
        }
    
    val effectiveTransactionId: String
        get() = transactionId.ifBlank { receiptNumber }
}

