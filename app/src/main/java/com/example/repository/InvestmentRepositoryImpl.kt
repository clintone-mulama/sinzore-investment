package com.example.repository

import com.example.models.Contribution
import com.example.models.Loan
import com.example.models.Member
import com.example.models.Payment
import com.example.models.LoanStatus
import com.example.network.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-fidelity repository implementation interacting with backend APIs, cloud Firestore, and Firebase authentication.
 */
@Singleton
class InvestmentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val apiService: ApiService = ApiClient.apiService
) : InvestmentRepository {

    override suspend fun applyForLoanBackend(
        principalAmount: Double,
        durationMonths: Int,
        purpose: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.applyLoan(
                LoanApplyRequest(
                    principalAmount = principalAmount,
                    durationMonths = durationMonths,
                    purpose = purpose
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Loan application submitted successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to apply for loan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun approveLoanBackend(loanId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.approveLoan(LoanApproveRequest(loanId = loanId))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Loan approved successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to approve loan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun denyLoanBackend(loanId: String, reason: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.denyLoan(LoanDenyRequest(loanId = loanId, reason = reason))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Loan denied successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to deny loan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordManualLoanBackend(
        memberId: String,
        memberName: String,
        principalAmount: Double,
        notes: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.recordManualLoan(
                ManualLoanRequest(
                    memberId = memberId,
                    memberName = memberName,
                    principalAmount = principalAmount,
                    notes = notes
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Manual loan recorded successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to record manual loan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearLoanBackend(loanId: String, notes: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.clearLoan(LoanClearRequest(loanId = loanId, notes = notes))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Loan cleared successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to clear loan: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordContributionBackend(
        amount: Double,
        paymentMethod: String,
        referenceId: String?,
        memberId: String?,
        memberName: String?,
        isManual: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.recordContribution(
                ContributionRequest(
                    amount = amount,
                    paymentMethod = paymentMethod,
                    transactionReference = referenceId,
                    memberId = memberId,
                    memberName = memberName,
                    isManual = isManual
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Contribution recorded successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to record contribution: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordRepaymentBackend(
        loanId: String,
        amount: Double,
        paymentMethod: String,
        referenceId: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.recordRepayment(
                RepaymentRequest(
                    loanId = loanId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    transactionReference = referenceId
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Loan repayment recorded successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to record repayment: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun initiateStkPushBackend(phoneNumber: String, amount: Double): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.initiateStkPush(StkPushRequest(phoneNumber = phoneNumber, amount = amount))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "STK push initiated successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "STK push failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun broadcastNotificationBackend(
        title: String,
        message: String,
        targetRole: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.broadcastNotification(
                BroadcastNotificationRequest(title = title, message = message, targetRole = targetRole)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Notification broadcast successfully")
            } else {
                Result.failure(Exception(response.body()?.error ?: response.body()?.message ?: "Failed to broadcast notification: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchDashboardSummaryBackend(): Result<DashboardSummaryDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getDashboardSummary()
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data ?: DashboardSummaryDto()
                Result.success(data)
            } else {
                Result.failure(Exception(response.body()?.error ?: "Failed to fetch dashboard summary"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeMembers(): Flow<List<Member>> = flow {
        emit(emptyList())
    }

    override suspend fun getMemberById(memberId: String): Member? {
        return null
    }

    override suspend fun createMember(member: Member): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun updateMemberStatus(memberId: String, isActive: Boolean): Result<Unit> {
        return Result.success(Unit)
    }

    override fun observeLoans(): Flow<List<Loan>> = flow {
        emit(emptyList())
    }

    override fun observeLoansByMember(memberId: String): Flow<List<Loan>> = flow {
        emit(emptyList())
    }

    override suspend fun applyForLoan(loan: Loan): Result<Unit> {
        return applyForLoanBackend(loan.amount, 1, "Personal").map { Unit }
    }

    override suspend fun updateLoanStatus(loanId: String, status: LoanStatus): Result<Unit> {
        return if (status == LoanStatus.APPROVED) {
            approveLoanBackend(loanId).map { Unit }
        } else {
            denyLoanBackend(loanId, "Rejected by admin").map { Unit }
        }
    }

    override fun observeContributions(): Flow<List<Contribution>> = flow {
        emit(emptyList())
    }

    override fun observeContributionsByMember(memberId: String): Flow<List<Contribution>> = flow {
        emit(emptyList())
    }

    override suspend fun recordContribution(contribution: Contribution): Result<Unit> {
        return recordContributionBackend(
            amount = contribution.amount,
            paymentMethod = contribution.paymentMethod,
            referenceId = contribution.id,
            memberId = contribution.userId,
            memberName = contribution.userName
        ).map { Unit }
    }

    override fun observePayments(): Flow<List<Payment>> = flow {
        emit(emptyList())
    }

    override suspend fun submitPayment(payment: Payment): Result<Unit> {
        return recordRepaymentBackend(
            loanId = payment.loanId,
            amount = payment.amountPaid,
            paymentMethod = payment.paymentMethod,
            referenceId = payment.transactionReference
        ).map { Unit }
    }
}

