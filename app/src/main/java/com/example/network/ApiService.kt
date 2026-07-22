package com.example.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // --- Dashboard ---
    @GET("dashboard/summary")
    suspend fun getDashboardSummary(): Response<ApiResponse<DashboardSummaryDto>>

    // --- Loans ---
    @POST("loans/apply")
    suspend fun applyLoan(@Body request: LoanApplyRequest): Response<ApiResponse<Any>>

    @POST("loans/approve")
    suspend fun approveLoan(@Body request: LoanApproveRequest): Response<ApiResponse<Any>>

    @POST("loans/deny")
    suspend fun denyLoan(@Body request: LoanDenyRequest): Response<ApiResponse<Any>>

    @POST("loans/manual")
    suspend fun recordManualLoan(@Body request: ManualLoanRequest): Response<ApiResponse<Any>>

    @POST("loans/clear")
    suspend fun clearLoan(@Body request: LoanClearRequest): Response<ApiResponse<Any>>

    @GET("loans/my-loans")
    suspend fun getMyLoans(): Response<ApiResponse<List<Map<String, Any>>>>

    @GET("loans/{loanId}")
    suspend fun getLoanStatus(@Path("loanId") loanId: String): Response<ApiResponse<Map<String, Any>>>

    // --- Contributions ---
    @POST("contributions/record")
    suspend fun recordContribution(@Body request: ContributionRequest): Response<ApiResponse<Any>>

    @GET("contributions/history")
    suspend fun getContributionsHistory(@Query("memberId") memberId: String? = null): Response<ApiResponse<List<Map<String, Any>>>>

    // --- Repayments ---
    @POST("repayments/record")
    suspend fun recordRepayment(@Body request: RepaymentRequest): Response<ApiResponse<Any>>

    @GET("repayments/history")
    suspend fun getRepaymentsHistory(@Query("memberId") memberId: String? = null): Response<ApiResponse<List<Map<String, Any>>>>

    // --- M-Pesa ---
    @POST("mpesa/stkpush")
    suspend fun initiateStkPush(@Body request: StkPushRequest): Response<ApiResponse<StkPushResponseDto>>

    @GET("mpesa/transactions")
    suspend fun getPaymentTransactions(): Response<ApiResponse<List<Map<String, Any>>>>

    // --- Notifications ---
    @GET("notifications")
    suspend fun getUserNotifications(): Response<ApiResponse<List<Map<String, Any>>>>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<ApiResponse<UnreadCountDto>>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationAsRead(@Path("id") id: String): Response<ApiResponse<Any>>

    @PUT("notifications/read-all")
    suspend fun markAllNotificationsAsRead(): Response<ApiResponse<Any>>

    @POST("notifications/broadcast")
    suspend fun broadcastNotification(@Body request: BroadcastNotificationRequest): Response<ApiResponse<Any>>

    // --- Reports & Statements ---
    @GET("statements/my-statement")
    suspend fun getMyStatement(): Response<ApiResponse<Map<String, Any>>>

    @GET("statements/{memberId}")
    suspend fun getMemberStatement(@Path("memberId") memberId: String): Response<ApiResponse<Map<String, Any>>>

    @GET("reports/treasury")
    suspend fun getTreasuryReport(): Response<ApiResponse<Map<String, Any>>>

    @GET("reports/contributions")
    suspend fun getContributionReport(): Response<ApiResponse<Map<String, Any>>>

    @GET("reports/loans")
    suspend fun getLoanReport(): Response<ApiResponse<Map<String, Any>>>

    @GET("reports/repayments")
    suspend fun getRepaymentReport(): Response<ApiResponse<Map<String, Any>>>

    // --- System Health ---
    @GET("health")
    suspend fun getSystemHealth(): Response<ApiResponse<Map<String, Any>>>
}
