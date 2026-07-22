package com.example.models

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.Locale

/**
 * Model representing a loan application and its repayment state.
 */
data class Loan(
    val id: String = "",
    
    // Core Architectural Specs Fields
    val applicantId: String = "",
    val applicantName: String = "",
    val amount: Double = 0.0,
    val totalRepayment: Double = 0.0,
    val status: String = "PENDING", // PENDING, APPROVED, CLEARED, DENIED
    val denialReason: String = "",
    val source: String = "user", // 'treasurer' or 'user'
    val createdAt: Any? = null, // Long, Timestamp, or Date
    val repayments: List<Repayment> = emptyList(),
    
    // Legacy / Backward Compatibility fields
    val userId: String = "", // Same as memberId / applicantId
    val memberId: String = "", // Same as userId / applicantId
    val applicantUid: String = "", // Same as applicantId
    val applicantFullName: String = "", // Same as applicantName
    val applicantIdNumber: String = "",
    val applicantPhoneNumber: String = "",
    val loanAmount: Double = 0.0,
    val principalAmount: Double = 0.0,
    val interestRate: Double = 0.10,
    val interestAmount: Double = 0.0,
    
    // Approval Details
    val approvedBy: String = "",
    val approvedAt: Date? = null,
    val approvedExactDate: String = "",
    val approvedExactTime: String = "",
    
    // Denial Details
    val deniedBy: String = "",
    val deniedReason: String = "",
    val deniedAt: Date? = null,
    val deniedExactDate: String = "",
    val deniedExactTime: String = "",
    val decisionDate: Date? = null,
    
    // Completion Details
    val clearedBy: String = "",
    val clearedAt: Date? = null,
    val clearedExactDate: String = "",
    val clearedExactTime: String = "",
    
    @get:PropertyName("isManual")
    @PropertyName("isManual")
    val isManual: Boolean = false,
    val dateSubmitted: Date? = null,
    val exactDate: String = "",
    val exactTime: String = "",
    val firestoreServerTimestamp: Date? = null
) {
    // Robust Getters for Spec and Compatibility compatibility
    val effectiveApplicantId: String
        get() = applicantId.ifBlank { userId.ifBlank { memberId.ifBlank { applicantUid } } }

    val effectiveApplicantName: String
        get() = applicantName.ifBlank { applicantFullName.ifBlank { "Member" } }

    val effectiveAmount: Double
        get() = if (amount > 0.0) amount else if (principalAmount > 0.0) principalAmount else loanAmount

    val effectiveTotalRepayment: Double
        get() = if (totalRepayment > 0.0) totalRepayment else (effectiveAmount * 1.10)

    val effectiveStatus: String
        get() = status.uppercase(Locale.getDefault())

    val effectiveDenialReason: String
        get() = denialReason.ifBlank { deniedReason }

    val effectiveSource: String
        get() = source.lowercase(Locale.getDefault())

    val createdAtLong: Long
        get() {
            return when (val c = createdAt) {
                is Long -> c
                is Number -> c.toLong()
                is com.google.firebase.Timestamp -> c.seconds * 1000
                is Date -> c.time
                else -> 0L
            }
        }

    val totalPaid: Double
        get() {
            @Suppress("SENSELESS_COMPARISON")
            if (repayments == null) return 0.0
            return repayments.sumOf { it?.amount ?: 0.0 }
        }

    val remainingBalance: Double
        get() {
            return (effectiveTotalRepayment - totalPaid).coerceAtLeast(0.0)
        }

    val progressPercentage: Float
        get() {
            val total = effectiveTotalRepayment
            if (total <= 0.0) return 0f
            return (totalPaid / total).toFloat().coerceIn(0f, 1f)
        }
}

enum class LoanStatus {
    PENDING,
    APPROVED,
    DENIED,
    CLEARED
}

