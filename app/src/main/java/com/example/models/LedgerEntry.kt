package com.example.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Model representing a single immutable entry in the Financial Ledger collection.
 */
data class LedgerEntry(
    val ledgerId: String = "",
    val transactionType: String = "", // e.g. "Treasury Initialization", "Contribution", "Loan Approval", "Loan Repayment", "Manual Contribution", "Manual Loan", "Balance Adjustment", "Loan Clearance"
    val relatedDocumentId: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val performedBy: String = "",
    val amount: Double = 0.0,
    val treasuryBalanceBefore: Double = 0.0,
    val treasuryBalanceAfter: Double = 0.0,
    val paymentMethod: String = "",
    val receiptNumber: String = "",
    val source: String = "",
    val notes: String = "",
    val date: String = "",
    val exactDate: String = "",
    val exactTime: String = "",
    @ServerTimestamp
    val firestoreServerTimestamp: Date? = null
)
