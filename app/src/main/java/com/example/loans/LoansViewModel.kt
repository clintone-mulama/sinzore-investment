package com.example.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.GlobalConfig
import com.example.models.LedgerEntry
import com.example.models.Loan
import com.example.models.Repayment
import com.example.models.User
import com.example.settings.PreferencesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface LoansUiState {
    object Loading : LoansUiState
    data class Success(
        val currentUser: User,
        val config: GlobalConfig,
        val loans: List<Loan>,
        val members: List<User> = emptyList()
    ) : LoansUiState
    data class Error(val message: String) : LoansUiState
}

class LoansViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoansUiState>(LoansUiState.Loading)
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private var userListenerRegistration: ListenerRegistration? = null
    private var configListenerRegistration: ListenerRegistration? = null
    private var loansListenerRegistration: ListenerRegistration? = null
    private var membersListenerRegistration: ListenerRegistration? = null

    private var currentUser: User? = null
    private var currentConfig: GlobalConfig = GlobalConfig()
    private var currentLoans: List<Loan> = emptyList()
    private var currentMembers: List<User> = emptyList()

    init {
        startSync()
    }

    fun retry() {
        startSync()
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    private fun startSync() {
        _uiState.value = LoansUiState.Loading
        clearMessages()

        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _uiState.value = LoansUiState.Error("Session expired. Please log in again.")
            return
        }

        cleanupListeners()

        // 1. Sync User Profile
        userListenerRegistration = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = LoansUiState.Error("Profile sync failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        currentUser = user
                        updateState()
                    } else {
                        _uiState.value = LoansUiState.Error("User profile could not be read.")
                    }
                } else {
                    _uiState.value = LoansUiState.Error("User profile does not exist.")
                }
            }

        // 2. Live Global Summary config sync
        configListenerRegistration = firestore.collection("config")
            .document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    currentConfig = snapshot.toObject(GlobalConfig::class.java) ?: GlobalConfig()
                } else {
                    currentConfig = GlobalConfig()
                }
                updateState()
            }

        // 3. Real-time Loans list Sync (order by createdAt or dateSubmitted)
        loansListenerRegistration = firestore.collection("loans")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback to ordering by dateSubmitted if createdAt is missing or unindexed
                    firestore.collection("loans")
                        .orderBy("dateSubmitted", Query.Direction.DESCENDING)
                        .addSnapshotListener { backupSnapshot, backupError ->
                            if (backupError != null) {
                                return@addSnapshotListener
                            }
                            if (backupSnapshot != null) {
                                val list = mutableListOf<Loan>()
                                for (doc in backupSnapshot.documents) {
                                    val loan = doc.toObject(Loan::class.java)
                                    if (loan != null) {
                                        list.add(loan.copy(id = doc.id))
                                    }
                                }
                                currentLoans = list
                                updateState()
                            }
                        }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = mutableListOf<Loan>()
                    for (doc in snapshot.documents) {
                        val loan = doc.toObject(Loan::class.java)
                        if (loan != null) {
                            list.add(loan.copy(id = doc.id))
                        }
                    }
                    currentLoans = list
                    updateState()
                }
            }

        // 4. Sync Registered Members (for Treasurer Manual Loan creation and selector)
        membersListenerRegistration = firestore.collection("users")
            .orderBy("fullName")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<User>()
                    for (doc in snapshot.documents) {
                        val u = doc.toObject(User::class.java)
                        if (u != null) {
                            list.add(u)
                        }
                    }
                    currentMembers = list
                    updateState()
                }
            }
    }

    private fun updateState() {
        val user = currentUser
        if (user != null) {
            _uiState.value = LoansUiState.Success(
                currentUser = user,
                config = currentConfig,
                loans = currentLoans,
                members = currentMembers
            )
        }
    }

    /**
     * Applies for a loan as a Member. Handles constraints.
     */
    fun applyLoan(amount: Double) {
        val user = currentUser ?: return
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            _errorMessage.value = "Please enter a valid loan amount greater than zero."
            return
        }

        // Outstanding Loan Constraint: check if user already has an active loan (Approved and remaining balance > 0)
        // or a Pending loan request
        val activeOrPendingLoan = currentLoans.firstOrNull {
            val isMyLoan = (it.effectiveApplicantId == user.uid)
            val isPending = it.effectiveStatus == "PENDING"
            val isApprovedAndUnpaid = it.effectiveStatus == "APPROVED" && it.remainingBalance > 0.0
            isMyLoan && (isPending || isApprovedAndUnpaid)
        }

        if (activeOrPendingLoan != null) {
            val statusStr = activeOrPendingLoan.effectiveStatus
            _errorMessage.value = "You cannot apply for a new loan because you already have a $statusStr loan."
            return
        }

        // Treasury Constraint: loan amount must not exceed treasury balance
        if (amount > currentConfig.totalBalance) {
            _errorMessage.value = "Loan amount exceeds available treasury cash (KES ${String.format("%,.2f", currentConfig.totalBalance)})."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val loanDocRef = firestore.collection("loans").document()
        val interestRate = 0.10
        val interestAmount = amount * interestRate
        val totalRepayment = amount + interestAmount

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val loanData = hashMapOf(
            "id" to loanDocRef.id,
            // Spec fields
            "applicantId" to user.uid,
            "applicantName" to user.fullName,
            "amount" to amount,
            "totalRepayment" to totalRepayment,
            "status" to "PENDING",
            "denialReason" to "",
            "source" to "user",
            "createdAt" to FieldValue.serverTimestamp(),
            "repayments" to emptyList<Repayment>(),

            // Legacy compatibility fields
            "userId" to user.uid,
            "memberId" to user.uid,
            "applicantUid" to user.uid,
            "applicantFullName" to user.fullName,
            "applicantIdNumber" to user.idNumber,
            "applicantPhoneNumber" to user.phoneNumber,
            "loanAmount" to amount,
            "principalAmount" to amount,
            "interestRate" to interestRate,
            "interestAmount" to interestAmount,
            "isManual" to false,
            "dateSubmitted" to FieldValue.serverTimestamp(),
            "exactDate" to exactDateStr,
            "exactTime" to exactTimeStr
        )

        loanDocRef.set(loanData)
            .addOnSuccessListener {
                com.example.common.ActivityLogger.logActivity(
                    userId = user.uid,
                    userName = user.fullName,
                    activityType = "Loan Application",
                    amount = amount,
                    details = "You applied for a loan of KES ${String.format("%,.2f", amount)}",
                    isTreasuryAction = false
                )
                _isSubmitting.value = false
                _successMessage.value = "Loan application for KES ${String.format("%,.2f", amount)} submitted successfully!"
            }
            .addOnFailureListener { e ->
                _isSubmitting.value = false
                _errorMessage.value = "Failed to submit loan application: ${e.localizedMessage}"
            }
    }

    /**
     * Approves a loan using an atomic transaction to decrease available treasury cash
     * and record to the Financial Ledger. (Treasurer Only)
     */
    fun approveLoan(loanId: String) {
        val user = currentUser ?: return
        if (user.role != "Treasurer") {
            _errorMessage.value = "Only Treasurers can approve loans."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val configRef = firestore.collection("config").document("global")
        val loanRef = firestore.collection("loans").document(loanId)
        val ledgerDocRef = firestore.collection("ledger").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val loanSnapshot = transaction.get(loanRef)

            val totalBalance = configSnapshot.getDouble("totalBalance") ?: 0.0
            val activeLoansVal = configSnapshot.getDouble("activeLoans") ?: 0.0

            val principal = loanSnapshot.getDouble("amount") ?: loanSnapshot.getDouble("principalAmount") ?: loanSnapshot.getDouble("loanAmount") ?: 0.0
            val applicantName = loanSnapshot.getString("applicantName") ?: loanSnapshot.getString("applicantFullName") ?: "Member"
            val applicantId = loanSnapshot.getString("applicantId") ?: loanSnapshot.getString("userId") ?: loanSnapshot.getString("memberId") ?: loanSnapshot.getString("applicantUid") ?: ""

            if (principal > totalBalance) {
                throw Exception("Insufficient Available Treasury Cash to approve this loan (Requires KES ${String.format("%,.2f", principal)}, Available KES ${String.format("%,.2f", totalBalance)}).")
            }

            // 1. Update Loan Document
            transaction.update(loanRef, mapOf(
                "status" to "APPROVED",
                "approvedBy" to user.fullName,
                "approvedAt" to Date(),
                "approvedExactDate" to exactDateStr,
                "approvedExactTime" to exactTimeStr,
                "decisionDate" to Date() // backward compatibility
            ))

            // 2. Update Global Config
            transaction.update(configRef, mapOf(
                "totalBalance" to totalBalance - principal,
                "activeLoans" to activeLoansVal + principal,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // 3. Write Immutable Ledger Entry
            val ledgerEntry = LedgerEntry(
                ledgerId = ledgerDocRef.id,
                transactionType = "Loan Approval",
                relatedDocumentId = loanId,
                memberId = applicantId,
                memberName = applicantName,
                performedBy = user.fullName,
                amount = principal,
                treasuryBalanceBefore = totalBalance,
                treasuryBalanceAfter = totalBalance - principal,
                paymentMethod = "Internal Transfer",
                receiptNumber = "",
                source = "Treasurer",
                notes = "Approved loan of KES ${String.format("%,.2f", principal)} for $applicantName",
                date = dateStr,
                exactDate = exactDateStr,
                exactTime = exactTimeStr,
                firestoreServerTimestamp = null
            )
            transaction.set(ledgerDocRef, ledgerEntry)

            // 2. Treasurer's official treasury activities log
            val activityDocRef2 = firestore.collection("activities").document()
            val userActivity2 = com.example.models.UserActivity(
                id = activityDocRef2.id,
                userId = user.uid,
                userName = user.fullName,
                activityType = "Loan Approval",
                amount = principal,
                details = "Treasurer ${user.fullName} approved loan of KES ${String.format("%,.2f", principal)} for $applicantName",
                timestamp = System.currentTimeMillis(),
                dateStr = exactDateStr,
                isTreasuryAction = true,
                serverTimestamp = null
            )
            transaction.set(activityDocRef2, userActivity2)
        }.addOnSuccessListener {
            _isSubmitting.value = false
            _successMessage.value = "Loan request approved and disbursed!"
        }
            .addOnFailureListener { e ->
                _isSubmitting.value = false
                _errorMessage.value = "Failed to approve loan: ${e.localizedMessage}"
            }
    }

    /**
     * Denies a loan request with a mandatory reason. (Treasurer Only)
     */
    fun denyLoan(loanId: String, reason: String) {
        val user = currentUser ?: return
        if (user.role != "Treasurer") {
            _errorMessage.value = "Only Treasurers can deny loans."
            return
        }
        if (reason.trim().isEmpty()) {
            _errorMessage.value = "Denial reason cannot be empty."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val now = Date()
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val loanRef = firestore.collection("loans").document(loanId)

        firestore.runTransaction { transaction ->
            val loanSnapshot = transaction.get(loanRef)
            val applicantId = loanSnapshot.getString("applicantId") ?: loanSnapshot.getString("userId") ?: loanSnapshot.getString("memberId") ?: loanSnapshot.getString("applicantUid") ?: ""
            val applicantName = loanSnapshot.getString("applicantName") ?: loanSnapshot.getString("applicantFullName") ?: "Member"
            val principal = loanSnapshot.getDouble("amount") ?: loanSnapshot.getDouble("principalAmount") ?: loanSnapshot.getDouble("loanAmount") ?: 0.0

            transaction.update(loanRef, mapOf(
                "status" to "DENIED",
                "denialReason" to reason.trim(),
                "deniedBy" to user.fullName,
                "deniedReason" to reason.trim(),
                "deniedAt" to Date(),
                "deniedExactDate" to exactDateStr,
                "deniedExactTime" to exactTimeStr,
                "decisionDate" to Date()
            ))

            // 2. Treasurer's official treasury activities log
            val activityDocRef2 = firestore.collection("activities").document()
            val userActivity2 = com.example.models.UserActivity(
                id = activityDocRef2.id,
                userId = user.uid,
                userName = user.fullName,
                activityType = "Loan Denial",
                amount = principal,
                details = "Treasurer ${user.fullName} denied loan of KES ${String.format("%,.2f", principal)} for $applicantName. Reason: ${reason.trim()}",
                timestamp = System.currentTimeMillis(),
                dateStr = exactDateStr,
                isTreasuryAction = true,
                serverTimestamp = null
            )
            transaction.set(activityDocRef2, userActivity2)
        }.addOnSuccessListener {
            _isSubmitting.value = false
            _successMessage.value = "Loan request denied."
        }.addOnFailureListener { e ->
            _isSubmitting.value = false
            _errorMessage.value = "Failed to deny loan: ${e.localizedMessage}"
        }
    }

    /**
     * Records a repayment against an approved loan. (Member/Treasurer workflow)
     */
    fun submitRepayment(loanId: String, amount: Double, paymentMethod: String, receiptNumber: String) {
        val user = currentUser ?: return
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            _errorMessage.value = "Repayment amount must be greater than zero."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val configRef = firestore.collection("config").document("global")
        val loanRef = firestore.collection("loans").document(loanId)
        val ledgerDocRef = firestore.collection("ledger").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val loanSnapshot = transaction.get(loanRef)

            val totalBalance = configSnapshot.getDouble("totalBalance") ?: 0.0
            val activeLoansVal = configSnapshot.getDouble("activeLoans") ?: 0.0

            val loan = loanSnapshot.toObject(Loan::class.java) ?: throw Exception("Loan not found.")
            val remaining = loan.remainingBalance

            if (amount > remaining) {
                throw Exception("Repayment amount KES ${String.format("%,.2f", amount)} exceeds outstanding loan balance of KES ${String.format("%,.2f", remaining)}.")
            }

            // Generate Repayment entry
            val repaymentId = firestore.collection("loans").document().id
            val repayment = Repayment(
                repaymentId = repaymentId,
                loanId = loanId,
                amount = amount,
                paymentMethod = paymentMethod,
                receiptNumber = receiptNumber,
                transactionId = receiptNumber, // Matches transactionId spec
                source = if (user.role == "Treasurer") "treasurer" else "user",
                recordedBy = user.fullName,
                date = System.currentTimeMillis(), // Matches Long date spec
                exactDate = exactDateStr,
                exactTime = exactTimeStr
            )

            val updatedRepayments = loan.repayments + repayment
            val updatedTotalPaid = updatedRepayments.sumOf { it.amount }
            val loanTotalRepayment = loan.effectiveTotalRepayment
            val newRemaining = (loanTotalRepayment - updatedTotalPaid).coerceAtLeast(0.0)

            val loanUpdates = hashMapOf<String, Any>(
                "repayments" to updatedRepayments
            )

            val isCleared = newRemaining <= 0.01 // allow tiny margin for double division
            if (isCleared) {
                loanUpdates["status"] = "CLEARED"
                loanUpdates["clearedBy"] = user.fullName
                loanUpdates["clearedAt"] = Date()
                loanUpdates["clearedExactDate"] = exactDateStr
                loanUpdates["clearedExactTime"] = exactTimeStr
            }

            // 1. Update Loan Document (append repayments, update status if fully paid)
            transaction.update(loanRef, loanUpdates)

            // 2. Update config values atomically
            val nextBalance = totalBalance + amount
            val nextActiveLoans = (activeLoansVal - amount).coerceAtLeast(0.0)
            transaction.update(configRef, mapOf(
                "totalBalance" to nextBalance,
                "activeLoans" to nextActiveLoans,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // 3. Write Immutable Ledger Entry
            val ledgerEntry = LedgerEntry(
                ledgerId = ledgerDocRef.id,
                transactionType = if (isCleared) "Loan Clearance" else "Loan Repayment",
                relatedDocumentId = loanId,
                memberId = loan.effectiveApplicantId,
                memberName = loan.effectiveApplicantName,
                performedBy = user.fullName,
                amount = amount,
                treasuryBalanceBefore = totalBalance,
                treasuryBalanceAfter = nextBalance,
                paymentMethod = paymentMethod,
                receiptNumber = receiptNumber,
                source = if (user.role == "Treasurer") "treasurer" else "user",
                notes = "Recorded repayment of KES ${String.format("%,.2f", amount)} for ${loan.effectiveApplicantName}. Status: ${if (isCleared) "Cleared" else "Approved"}",
                date = dateStr,
                exactDate = exactDateStr,
                exactTime = exactTimeStr,
                firestoreServerTimestamp = null
            )
            transaction.set(ledgerDocRef, ledgerEntry)

            val activityDocRef = firestore.collection("activities").document()
            val isMyRepayment = (user.uid == loan.effectiveApplicantId)
            val activityType = if (isCleared) "Loan Repayment Completion" else "Loan Repayment"

            if (isMyRepayment) {
                val activityDetails = if (isCleared) {
                    "You completed loan repayment of KES ${String.format("%,.2f", amount)}. Loan is fully cleared!"
                } else {
                    "You paid loan instalment of KES ${String.format("%,.2f", amount)}"
                }
                val userActivity = com.example.models.UserActivity(
                    id = activityDocRef.id,
                    userId = loan.effectiveApplicantId,
                    userName = loan.effectiveApplicantName,
                    activityType = activityType,
                    amount = amount,
                    details = activityDetails,
                    timestamp = System.currentTimeMillis(),
                    dateStr = exactDateStr,
                    isTreasuryAction = false,
                    serverTimestamp = null
                )
                transaction.set(activityDocRef, userActivity)
            } else {
                // 1. Applicant's personal activity log
                val userActivity = com.example.models.UserActivity(
                    id = activityDocRef.id,
                    userId = loan.effectiveApplicantId,
                    userName = loan.effectiveApplicantName,
                    activityType = activityType,
                    amount = amount,
                    details = "Treasurer ${user.fullName} manually recorded a loan repayment of KES ${String.format("%,.2f", amount)} for you",
                    timestamp = System.currentTimeMillis(),
                    dateStr = exactDateStr,
                    isTreasuryAction = false,
                    serverTimestamp = null
                )
                transaction.set(activityDocRef, userActivity)

                // 2. Treasurer's official treasury activities log
                val treasuryActivityDocRef = firestore.collection("activities").document()
                val treasuryActivity = com.example.models.UserActivity(
                    id = treasuryActivityDocRef.id,
                    userId = user.uid,
                    userName = user.fullName,
                    activityType = activityType,
                    amount = amount,
                    details = "Treasurer ${user.fullName} manually recorded a loan repayment of KES ${String.format("%,.2f", amount)} for ${loan.effectiveApplicantName}",
                    timestamp = System.currentTimeMillis(),
                    dateStr = exactDateStr,
                    isTreasuryAction = true,
                    serverTimestamp = null
                )
                transaction.set(treasuryActivityDocRef, treasuryActivity)
            }
        }.addOnSuccessListener {
            _isSubmitting.value = false
            _successMessage.value = "Repayment of KES ${String.format("%,.2f", amount)} registered successfully!"
        }
            .addOnFailureListener { e ->
                _isSubmitting.value = false
                _errorMessage.value = "Repayment failed: ${e.localizedMessage}"
            }
    }

    /**
     * Records a manual approved loan on behalf of a registered member. (Treasurer Only)
     */
    fun recordManualLoan(member: User, amount: Double) {
        val user = currentUser ?: return
        if (user.role != "Treasurer") {
            _errorMessage.value = "Only Treasurers can disburse manual loans."
            return
        }
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            _errorMessage.value = "Please enter a valid loan amount greater than zero."
            return
        }

        // Outstanding Loan Constraint: check if user already has an active loan
        val memberLoans = currentLoans.filter {
            val isMyLoan = (it.effectiveApplicantId == member.uid)
            val isApprovedAndUnpaid = it.effectiveStatus == "APPROVED" && it.remainingBalance > 0.0
            isMyLoan && isApprovedAndUnpaid
        }
        if (memberLoans.isNotEmpty()) {
            _errorMessage.value = "Member ${member.fullName} already has an active Approved loan."
            return
        }

        _isSubmitting.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val configRef = firestore.collection("config").document("global")
        val loanDocRef = firestore.collection("loans").document()
        val ledgerDocRef = firestore.collection("ledger").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val interestRate = 0.10
        val interestAmount = amount * interestRate
        val totalRepayment = amount + interestAmount

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val totalBalance = configSnapshot.getDouble("totalBalance") ?: 0.0
            val activeLoansVal = configSnapshot.getDouble("activeLoans") ?: 0.0

            if (amount > totalBalance) {
                throw Exception("Insufficient Available Treasury Cash (Available KES ${String.format("%,.2f", totalBalance)}, Requested KES ${String.format("%,.2f", amount)}).")
            }

            val loanData = hashMapOf(
                "id" to loanDocRef.id,
                // Spec fields
                "applicantId" to member.uid,
                "applicantName" to member.fullName,
                "amount" to amount,
                "totalRepayment" to totalRepayment,
                "status" to "APPROVED",
                "denialReason" to "",
                "source" to "treasurer",
                "createdAt" to Date(),
                "repayments" to emptyList<Repayment>(),

                // Legacy compatibility fields
                "userId" to member.uid,
                "memberId" to member.uid,
                "applicantUid" to member.uid,
                "applicantFullName" to member.fullName,
                "applicantIdNumber" to member.idNumber,
                "applicantPhoneNumber" to member.phoneNumber,
                "loanAmount" to amount,
                "principalAmount" to amount,
                "interestRate" to interestRate,
                "interestAmount" to interestAmount,
                "isManual" to true,
                "approvedBy" to user.fullName,
                "approvedAt" to Date(),
                "approvedExactDate" to exactDateStr,
                "approvedExactTime" to exactTimeStr,
                "decisionDate" to Date(),
                "dateSubmitted" to Date(),
                "exactDate" to exactDateStr,
                "exactTime" to exactTimeStr
            )

            // 1. Create Loan Document
            transaction.set(loanDocRef, loanData)

            // 2. Update config values atomically
            transaction.update(configRef, mapOf(
                "totalBalance" to totalBalance - amount,
                "activeLoans" to activeLoansVal + amount,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // 3. Write Immutable Ledger Entry
            val ledgerEntry = LedgerEntry(
                ledgerId = ledgerDocRef.id,
                transactionType = "Manual Loan",
                relatedDocumentId = loanDocRef.id,
                memberId = member.uid,
                memberName = member.fullName,
                performedBy = user.fullName,
                amount = amount,
                treasuryBalanceBefore = totalBalance,
                treasuryBalanceAfter = totalBalance - amount,
                paymentMethod = "Manual Recording",
                receiptNumber = "",
                source = "Treasurer",
                notes = "Recorded manual loan of KES ${String.format("%,.2f", amount)} for ${member.fullName}",
                date = dateStr,
                exactDate = exactDateStr,
                exactTime = exactTimeStr,
                firestoreServerTimestamp = null
            )
            transaction.set(ledgerDocRef, ledgerEntry)

            // 2. Treasurer's official treasury activities log
            val treasuryActivityDocRef = firestore.collection("activities").document()
            val treasuryActivity = com.example.models.UserActivity(
                id = treasuryActivityDocRef.id,
                userId = user.uid,
                userName = user.fullName,
                activityType = "Manual Loan Record Added",
                amount = amount,
                details = "Treasurer ${user.fullName} manually recorded a loan of KES ${String.format("%,.2f", amount)} for ${member.fullName}",
                timestamp = System.currentTimeMillis(),
                dateStr = exactDateStr,
                isTreasuryAction = true,
                serverTimestamp = null
            )
            transaction.set(treasuryActivityDocRef, treasuryActivity)
        }.addOnSuccessListener {
            _isSubmitting.value = false
            _successMessage.value = "Manual Approved Loan of KES ${String.format("%,.2f", amount)} recorded successfully!"
        }
            .addOnFailureListener { e ->
                _isSubmitting.value = false
                _errorMessage.value = "Manual loan recording failed: ${e.localizedMessage}"
            }
    }

    private fun cleanupListeners() {
        userListenerRegistration?.remove()
        userListenerRegistration = null
        configListenerRegistration?.remove()
        configListenerRegistration = null
        loansListenerRegistration?.remove()
        loansListenerRegistration = null
        membersListenerRegistration?.remove()
        membersListenerRegistration = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
