package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoanApplyRequest(
    @Json(name = "principalAmount") val principalAmount: Double,
    @Json(name = "durationMonths") val durationMonths: Int? = 1,
    @Json(name = "purpose") val purpose: String? = "Personal",
    @Json(name = "memberId") val memberId: String? = null,
    @Json(name = "memberName") val memberName: String? = null
)

@JsonClass(generateAdapter = true)
data class LoanApproveRequest(
    @Json(name = "loanId") val loanId: String
)

@JsonClass(generateAdapter = true)
data class LoanDenyRequest(
    @Json(name = "loanId") val loanId: String,
    @Json(name = "reason") val reason: String? = "Does not meet criteria"
)

@JsonClass(generateAdapter = true)
data class ManualLoanRequest(
    @Json(name = "memberId") val memberId: String,
    @Json(name = "memberName") val memberName: String,
    @Json(name = "principalAmount") val principalAmount: Double,
    @Json(name = "interestRate") val interestRate: Double? = 0.10,
    @Json(name = "durationMonths") val durationMonths: Int? = 1,
    @Json(name = "purpose") val purpose: String? = "Manual Loan",
    @Json(name = "paymentMethod") val paymentMethod: String? = "Manual Recording",
    @Json(name = "referenceId") val referenceId: String? = null,
    @Json(name = "notes") val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class LoanClearRequest(
    @Json(name = "loanId") val loanId: String,
    @Json(name = "notes") val notes: String? = "Loan cleared"
)

@JsonClass(generateAdapter = true)
data class ContributionRequest(
    @Json(name = "amount") val amount: Double,
    @Json(name = "paymentMethod") val paymentMethod: String? = "MPESA",
    @Json(name = "transactionReference") val transactionReference: String? = null,
    @Json(name = "memberId") val memberId: String? = null,
    @Json(name = "memberName") val memberName: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "isManual") val isManual: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RepaymentRequest(
    @Json(name = "loanId") val loanId: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "paymentMethod") val paymentMethod: String? = "MPESA",
    @Json(name = "transactionReference") val transactionReference: String? = null,
    @Json(name = "memberId") val memberId: String? = null,
    @Json(name = "memberName") val memberName: String? = null,
    @Json(name = "notes") val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class StkPushRequest(
    @Json(name = "phoneNumber") val phoneNumber: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "accountReference") val accountReference: String? = "SinzoreFund",
    @Json(name = "transactionDesc") val transactionDesc: String? = "Payment"
)

@JsonClass(generateAdapter = true)
data class StkPushResponseDto(
    @Json(name = "MerchantRequestID") val merchantRequestId: String? = null,
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String? = null,
    @Json(name = "ResponseCode") val responseCode: String? = null,
    @Json(name = "ResponseDescription") val responseDescription: String? = null,
    @Json(name = "CustomerMessage") val customerMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class UnreadCountDto(
    @Json(name = "unreadCount") val unreadCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class BroadcastNotificationRequest(
    @Json(name = "title") val title: String,
    @Json(name = "message") val message: String,
    @Json(name = "type") val type: String? = "ANNOUNCEMENT",
    @Json(name = "targetRole") val targetRole: String? = "ALL",
    @Json(name = "recipientId") val recipientId: String? = null
)

@JsonClass(generateAdapter = true)
data class DashboardSummaryDto(
    @Json(name = "totalBalance") val totalBalance: Double? = 0.0,
    @Json(name = "totalExpectedBalance") val totalExpectedBalance: Double? = 0.0,
    @Json(name = "activeLoans") val activeLoans: Double? = 0.0,
    @Json(name = "numActiveLoans") val numActiveLoans: Int? = 0,
    @Json(name = "numPendingLoans") val numPendingLoans: Int? = 0,
    @Json(name = "totalContributions") val totalContributions: Double? = 0.0,
    @Json(name = "pendingLoans") val pendingLoans: Double? = 0.0,
    @Json(name = "registeredMembers") val registeredMembers: Int? = 0
)
