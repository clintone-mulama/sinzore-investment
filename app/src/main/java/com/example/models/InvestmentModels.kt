package com.example.models

import java.util.Date

/**
 * Domain representation of an Investment Group Member.
 */
data class Member(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: MemberRole = MemberRole.MEMBER,
    val joinDate: Date = Date(),
    val isActive: Boolean = true
)

enum class MemberRole {
    MEMBER,
    TREASURER,
    SECRETARY,
    ADMIN
}




/**
 * Domain representation of loan repayment transactions.
 */
data class Payment(
    val id: String,
    val loanId: String,
    val memberId: String,
    val amountPaid: Double,
    val date: Date = Date(),
    val paymentMethod: String = "MPESA",
    val transactionReference: String,
    val status: TransactionStatus = TransactionStatus.PENDING
)

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
