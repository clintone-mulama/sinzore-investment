package com.example.repository

import com.example.models.Contribution
import com.example.models.Loan
import com.example.models.Member
import com.example.models.Payment
import com.example.network.DashboardSummaryDto
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface defining data access contracts for the Sinzore Investment Platform.
 */
interface InvestmentRepository {

    // --- Secure Backend API Integration ---
    suspend fun applyForLoanBackend(principalAmount: Double, durationMonths: Int = 1, purpose: String = "Personal"): Result<String>
    suspend fun approveLoanBackend(loanId: String): Result<String>
    suspend fun denyLoanBackend(loanId: String, reason: String = "Does not meet criteria"): Result<String>
    suspend fun recordManualLoanBackend(memberId: String, memberName: String, principalAmount: Double, notes: String? = null): Result<String>
    suspend fun clearLoanBackend(loanId: String, notes: String? = null): Result<String>

    suspend fun recordContributionBackend(amount: Double, paymentMethod: String = "MPESA", referenceId: String? = null, memberId: String? = null, memberName: String? = null, isManual: Boolean = false): Result<String>
    suspend fun recordRepaymentBackend(loanId: String, amount: Double, paymentMethod: String = "MPESA", referenceId: String? = null): Result<String>

    suspend fun initiateStkPushBackend(phoneNumber: String, amount: Double): Result<String>
    suspend fun broadcastNotificationBackend(title: String, message: String, targetRole: String = "ALL"): Result<String>
    suspend fun fetchDashboardSummaryBackend(): Result<DashboardSummaryDto>

    // --- Member Directory Management ---
    fun observeMembers(): Flow<List<Member>>
    suspend fun getMemberById(memberId: String): Member?
    suspend fun createMember(member: Member): Result<Unit>
    suspend fun updateMemberStatus(memberId: String, isActive: Boolean): Result<Unit>

    // --- Loan Ledger Management ---
    fun observeLoans(): Flow<List<Loan>>
    fun observeLoansByMember(memberId: String): Flow<List<Loan>>
    suspend fun applyForLoan(loan: Loan): Result<Unit>
    suspend fun updateLoanStatus(loanId: String, status: com.example.models.LoanStatus): Result<Unit>

    // --- Savings & Contributions ---
    fun observeContributions(): Flow<List<Contribution>>
    fun observeContributionsByMember(memberId: String): Flow<List<Contribution>>
    suspend fun recordContribution(contribution: Contribution): Result<Unit>

    // --- Payments Ledger ---
    fun observePayments(): Flow<List<Payment>>
    suspend fun submitPayment(payment: Payment): Result<Unit>
}

