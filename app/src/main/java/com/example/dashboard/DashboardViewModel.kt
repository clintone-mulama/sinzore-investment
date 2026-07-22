package com.example.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.GlobalConfig
import com.example.models.User
import com.example.settings.PreferencesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val user: User,
        val config: GlobalConfig
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _userActivities = MutableStateFlow<List<com.example.models.UserActivity>>(emptyList())
    val userActivities: StateFlow<List<com.example.models.UserActivity>> = _userActivities.asStateFlow()

    private val _treasuryActivities = MutableStateFlow<List<com.example.models.UserActivity>>(emptyList())
    val treasuryActivities: StateFlow<List<com.example.models.UserActivity>> = _treasuryActivities.asStateFlow()

    private val _members = MutableStateFlow<List<User>>(emptyList())
    val members: StateFlow<List<User>> = _members.asStateFlow()

    private val _pendingLoans = MutableStateFlow<List<com.example.models.Loan>>(emptyList())
    val pendingLoans: StateFlow<List<com.example.models.Loan>> = _pendingLoans.asStateFlow()

    private val _activeLoans = MutableStateFlow<List<com.example.models.Loan>>(emptyList())
    val activeLoans: StateFlow<List<com.example.models.Loan>> = _activeLoans.asStateFlow()

    private val _notifications = MutableStateFlow<List<com.example.models.AppNotification>>(emptyList())
    val notifications: StateFlow<List<com.example.models.AppNotification>> = _notifications.asStateFlow()

    private val _lastReadNotificationsTimestamp = MutableStateFlow<Long>(0L)
    val lastReadNotificationsTimestamp: StateFlow<Long> = _lastReadNotificationsTimestamp.asStateFlow()

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            preferencesManager.saveLastReadNotificationsTimestamp(System.currentTimeMillis())
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private var userListenerRegistration: ListenerRegistration? = null
    private var configListenerRegistration: ListenerRegistration? = null
    private var activitiesListenerRegistration: ListenerRegistration? = null
    private var treasuryActivitiesListenerRegistration: ListenerRegistration? = null
    private var membersListenerRegistration: ListenerRegistration? = null
    private var pendingLoansListenerRegistration: ListenerRegistration? = null
    private var activeLoansListenerRegistration: ListenerRegistration? = null
    private var notificationsListenerRegistration: ListenerRegistration? = null

    private val appStartTime = System.currentTimeMillis()
    private val notifiedIds = mutableSetOf<String>()

    private var currentUser: User? = null
    private var currentConfig: GlobalConfig? = null

    init {
        viewModelScope.launch {
            preferencesManager.lastReadNotificationsTimestampFlow.collect {
                _lastReadNotificationsTimestamp.value = it
            }
        }
        startListeners()
    }

    fun retry() {
        startListeners()
    }

    /**
     * Initializes a standard global configuration document if it is missing in the Firestore DB
     * and the logged-in user has permissions to write it.
     */
    fun initializeDefaultGlobalConfig() {
        val user = currentUser ?: return
        if (user.role == "Admin" || user.role == "Treasurer") {
            val defaultConfig = GlobalConfig(
                totalBalance = 250000.0,
                totalExpectedBalance = 295000.0,
                activeLoans = 45000.0,
                numActiveLoans = 3,
                numPendingLoans = 1,
                totalContributions = 200000.0,
                pendingLoans = 5000.0,
                registeredMembers = 12,
                lastUpdated = System.currentTimeMillis()
            )
            firestore.collection("config")
                .document("global")
                .set(defaultConfig)
                .addOnSuccessListener {
                    // Handled automatically via snapshop listener
                }
                .addOnFailureListener {
                    // Fail silently, listener remains active
                }
        }
    }

    private fun startListeners() {
        _uiState.value = DashboardUiState.Loading
        
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _uiState.value = DashboardUiState.Error("Session expired or user not logged in. Please log in again.")
            return
        }

        // Clean up any stale listeners before registering new ones
        cleanupListeners()

        // 1. Real-time User Profile sync
        userListenerRegistration = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = DashboardUiState.Error("Failed to sync profile: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        currentUser = user
                        
                        // Async update local DataStore session to keep everything synchronized
                        viewModelScope.launch {
                            preferencesManager.saveSession(
                                uid = user.uid,
                                email = user.email,
                                name = user.fullName,
                                role = user.role
                            )
                        }

                        updateSuccessState()
                    } else {
                        _uiState.value = DashboardUiState.Error("User profile document in database is empty or invalid.")
                    }
                } else {
                    _uiState.value = DashboardUiState.Error("Profile not found in Firestore. Please register or verify database connection.")
                }
            }

        // 2. Real-time Live Global Summary sync
        configListenerRegistration = firestore.collection("config")
            .document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = DashboardUiState.Error("Failed to sync financial data: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val config = snapshot.toObject(GlobalConfig::class.java)
                    currentConfig = config ?: GlobalConfig()
                } else {
                    // Gracefully fallback to default zero-filled values instead of failing
                    currentConfig = GlobalConfig()
                }
                updateSuccessState()
            }

        // 3. Real-time User Activities sync
        activitiesListenerRegistration = firestore.collection("activities")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<com.example.models.UserActivity>()
                    for (doc in snapshot.documents) {
                        val act = doc.toObject(com.example.models.UserActivity::class.java)
                        if (act != null && !act.isTreasuryAction) {
                            list.add(act.copy(id = doc.id))
                        }
                    }
                    _userActivities.value = list.sortedByDescending { it.timestamp }
                }
            }

        // 4. Real-time Registered Members sync (for Treasurer selection)
        membersListenerRegistration = firestore.collection("users")
            .orderBy("fullName")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<User>()
                    for (doc in snapshot.documents) {
                        val u = doc.toObject(User::class.java)
                        if (u != null) {
                            list.add(u)
                        }
                    }
                    _members.value = list
                }
            }

        // 5. Real-time Treasury Activities sync
        treasuryActivitiesListenerRegistration = firestore.collection("activities")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<com.example.models.UserActivity>()
                    for (doc in snapshot.documents) {
                        val act = doc.toObject(com.example.models.UserActivity::class.java)
                        if (act != null && act.isTreasuryAction) {
                            list.add(act.copy(id = doc.id))
                        }
                    }
                    _treasuryActivities.value = list.sortedByDescending { it.timestamp }
                }
            }

        // 6. Real-time Pending Loans sync
        pendingLoansListenerRegistration = firestore.collection("loans")
            .whereIn("status", listOf("PENDING", "Pending", "pending"))
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<com.example.models.Loan>()
                    for (doc in snapshot.documents) {
                        val loan = doc.toObject(com.example.models.Loan::class.java)
                        if (loan != null) {
                            list.add(loan.copy(id = doc.id))
                        }
                    }
                    _pendingLoans.value = list
                    updateSuccessState()
                }
            }

        // 7. Real-time Active Loans sync
        activeLoansListenerRegistration = firestore.collection("loans")
            .whereIn("status", listOf("APPROVED", "Approved"))
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<com.example.models.Loan>()
                    for (doc in snapshot.documents) {
                        val loan = doc.toObject(com.example.models.Loan::class.java)
                        if (loan != null) {
                            list.add(loan.copy(id = doc.id))
                        }
                    }
                    _activeLoans.value = list
                    updateSuccessState()
                }
            }

        // 8. Real-time Notifications/Announcements sync
        notificationsListenerRegistration = firestore.collection("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    val list = mutableListOf<com.example.models.AppNotification>()
                    for (doc in snapshot.documents) {
                        val notif = doc.toObject(com.example.models.AppNotification::class.java)
                        if (notif != null) {
                            list.add(notif.copy(id = doc.id))
                            
                            // Check if this is a newly received notification while the app is active
                            if (notif.timestamp > appStartTime && !notifiedIds.contains(doc.id)) {
                                notifiedIds.add(doc.id)
                                // Trigger local status bar notification
                                try {
                                    com.example.notifications.LocalNotificationHelper.sendLocalNotification(
                                        context = preferencesManager.applicationContext,
                                        title = notif.title,
                                        body = notif.message
                                    )
                                } catch (e: Exception) {
                                    // Fail-safe
                                }
                            }
                        }
                    }
                    _notifications.value = list.sortedByDescending { it.timestamp }
                }
            }
    }

    private fun updateSuccessState() {
        val user = currentUser
        val config = currentConfig
        if (user != null && config != null) {
            val updatedConfig = config.copy(
                numPendingLoans = _pendingLoans.value.size,
                numActiveLoans = _activeLoans.value.size
            )
            _uiState.value = DashboardUiState.Success(user, updatedConfig)
        }
    }

    private fun cleanupListeners() {
        userListenerRegistration?.remove()
        userListenerRegistration = null
        configListenerRegistration?.remove()
        configListenerRegistration = null
        activitiesListenerRegistration?.remove()
        activitiesListenerRegistration = null
        treasuryActivitiesListenerRegistration?.remove()
        treasuryActivitiesListenerRegistration = null
        membersListenerRegistration?.remove()
        membersListenerRegistration = null
        pendingLoansListenerRegistration?.remove()
        pendingLoansListenerRegistration = null
        activeLoansListenerRegistration?.remove()
        activeLoansListenerRegistration = null
        notificationsListenerRegistration?.remove()
        notificationsListenerRegistration = null
    }

    fun applyLoan(amount: Double, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        
        firestore.collection("loans")
            .whereEqualTo("applicantId", user.uid)
            .whereIn("status", listOf("APPROVED", "Approved"))
            .get()
            .addOnSuccessListener { querySnapshot ->
                var hasActiveLoan = false
                for (doc in querySnapshot.documents) {
                    val repayments = doc.get("repayments") as? List<Map<String, Any>> ?: emptyList()
                    val totalRepayment = doc.getDouble("totalRepayment") ?: (doc.getDouble("amount") ?: 0.0) * 1.10
                    val totalPaid = repayments.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
                    val remaining = totalRepayment - totalPaid
                    if (remaining > 0.01) {
                        hasActiveLoan = true
                        break
                    }
                }

                if (hasActiveLoan) {
                    onFailure("You cannot request a new loan while you have an active outstanding loan.")
                } else {
                    val loanDocRef = firestore.collection("loans").document()
                    val now = Date()
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    val isoTimestamp = isoFormat.format(now)
                    val localDateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(now)
                    val localTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)

                    val interestAmount = amount * 0.1
                    val totalRepayment = amount * 1.1

                    val loanData = hashMapOf(
                        "id" to loanDocRef.id,
                        "applicantId" to user.uid,
                        "applicantName" to user.fullName,
                        "applicantIdNumber" to user.idNumber,
                        "applicantPhone" to user.phoneNumber,
                        "amount" to amount,
                        "interestAmount" to interestAmount,
                        "totalRepayment" to totalRepayment,
                        "status" to "PENDING",
                        "createdAt" to isoTimestamp,
                        "exactDate" to localDateStr,
                        "exactTime" to localTimeStr,
                        "userId" to user.uid,
                        "memberId" to user.uid,
                        "applicantUid" to user.uid,
                        "applicantFullName" to user.fullName,
                        "applicantPhoneNumber" to user.phoneNumber,
                        "principalAmount" to amount,
                        "loanAmount" to amount,
                        "interestRate" to 0.10,
                        "source" to "Member",
                        "isManual" to false,
                        "dateSubmitted" to now
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
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            onFailure(e.localizedMessage ?: "Failed to submit loan request.")
                        }
                }
            }
            .addOnFailureListener { e ->
                onFailure("Verification failed: ${e.localizedMessage}")
            }
    }

    fun makeContribution(
        amount: Double,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }

        viewModelScope.launch {
            // 1. Dispatch real M-Pesa STK Push via Daraja API
            val response = com.example.payments.DarajaService.initiateStkPush(amount, phoneNumber)
            if (!response.isSuccessful) {
                onFailure(response.message)
                return@launch
            }

            // 2. STK Push accepted by Safaricom, log the transaction and update database
            val transactionId = firestore.collection("mpesa_transactions").document().id
            val transactionRef = firestore.collection("mpesa_transactions").document(transactionId)

            val pendingTx = hashMapOf(
                "id" to transactionId,
                "userId" to user.uid,
                "amount" to amount,
                "phoneNumber" to phoneNumber,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis(),
                "merchantRequestId" to (response.merchantRequestId ?: ""),
                "checkoutRequestId" to (response.checkoutRequestId ?: "")
            )

            transactionRef.set(pendingTx)
                .addOnSuccessListener {
                    val configRef = firestore.collection("config").document("global")
                    val contribDocRef = firestore.collection("contributions").document()
                    val ledgerDocRef = firestore.collection("ledger").document()

                    val now = Date()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                    val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
                    val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

                    // Fallback to auto-generated ID if response doesn't provide CheckoutRequestID
                    val mpesaRef = if (!response.checkoutRequestId.isNullOrEmpty()) {
                        response.checkoutRequestId
                    } else {
                        "MP_" + (1..8).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
                    }

                    firestore.runTransaction { transaction ->
                        val configSnapshot = transaction.get(configRef)
                        val currentBal = configSnapshot.getDouble("totalBalance") ?: 0.0
                        val currentContrib = configSnapshot.getDouble("totalContributions") ?: 0.0

                        val newBal = currentBal + amount
                        val newContrib = currentContrib + amount

                        val contributionData = hashMapOf(
                            "id" to contribDocRef.id,
                            "userId" to user.uid,
                            "userName" to user.fullName,
                            "amount" to amount,
                            "date" to System.currentTimeMillis(),
                            "source" to "member",
                            "mpesaReceiptNumber" to mpesaRef,
                            "contributorUid" to user.uid,
                            "contributorFullName" to user.fullName,
                            "contributorPhoneNumber" to phoneNumber,
                            "paymentMethod" to "M-PESA",
                            "isManual" to false,
                            "dateContributed" to Date()
                        )
                        transaction.set(contribDocRef, contributionData)

                        transaction.update(
                            configRef,
                            mapOf(
                                "totalBalance" to newBal,
                                "totalContributions" to newContrib,
                                "lastUpdated" to System.currentTimeMillis()
                            )
                        )

                        transaction.update(transactionRef, "status", "completed")

                        val ledgerEntry = com.example.models.LedgerEntry(
                            ledgerId = ledgerDocRef.id,
                            transactionType = "Contribution",
                            relatedDocumentId = contribDocRef.id,
                            memberId = user.uid,
                            memberName = user.fullName,
                            performedBy = user.fullName,
                            amount = amount,
                            treasuryBalanceBefore = currentBal,
                            treasuryBalanceAfter = newBal,
                            paymentMethod = "M-PESA",
                            receiptNumber = mpesaRef,
                            source = "Member",
                            notes = "Self contribution of KES ${String.format("%,.2f", amount)} via Safaricom Daraja STK Push",
                            date = dateStr,
                            exactDate = exactDateStr,
                            exactTime = exactTimeStr,
                            firestoreServerTimestamp = null
                        )
                        transaction.set(ledgerDocRef, ledgerEntry)

                        val activityDocRef = firestore.collection("activities").document()
                        val userActivity = com.example.models.UserActivity(
                            id = activityDocRef.id,
                            userId = user.uid,
                            userName = user.fullName,
                            activityType = "Contribution Payment",
                            amount = amount,
                            details = "You paid contribution of KES ${String.format("%,.2f", amount)} via M-Pesa",
                            timestamp = System.currentTimeMillis(),
                            dateStr = exactDateStr,
                            isTreasuryAction = false,
                            serverTimestamp = null
                        )
                        transaction.set(activityDocRef, userActivity)
                    }.addOnSuccessListener {
                        onSuccess()
                    }.addOnFailureListener { e ->
                        onFailure(e.localizedMessage ?: "Failed to complete transaction.")
                    }
                }
                .addOnFailureListener { e ->
                    onFailure("Failed to create pending transaction log: ${e.localizedMessage}")
                }
        }
    }

    fun checkActiveLoan(onSuccess: (com.example.models.Loan?) -> Unit, onFailure: (String) -> Unit) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }

        firestore.collection("loans")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val loans = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(com.example.models.Loan::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }
                val activeLoan = loans.firstOrNull { 
                    val status = it.status
                    val isApproved = status != null && status.equals("Approved", ignoreCase = true)
                    isApproved && it.remainingBalance > 0.05 
                }

                if (activeLoan != null) {
                    onSuccess(activeLoan)
                } else {
                    firestore.collection("loans")
                        .whereEqualTo("applicantId", user.uid)
                        .get()
                        .addOnSuccessListener { querySnapshot2 ->
                            val loans2 = querySnapshot2.documents.mapNotNull { doc ->
                                try {
                                    doc.toObject(com.example.models.Loan::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            val activeLoan2 = loans2.firstOrNull { 
                                val status = it.status
                                val isApproved = status != null && status.equals("Approved", ignoreCase = true)
                                isApproved && it.remainingBalance > 0.05 
                            }
                            onSuccess(activeLoan2)
                        }
                        .addOnFailureListener { e ->
                            onFailure("Failed to retrieve loans: ${e.localizedMessage}")
                        }
                }
            }
            .addOnFailureListener { e ->
                onFailure("Failed to retrieve loans: ${e.localizedMessage}")
            }
    }

    fun repayLoan(
        loan: com.example.models.Loan,
        amount: Double,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }

        viewModelScope.launch {
            // 1. Dispatch real M-Pesa STK Push via Daraja API
            val response = com.example.payments.DarajaService.initiateStkPush(amount, phoneNumber)
            if (!response.isSuccessful) {
                onFailure(response.message)
                return@launch
            }

            // 2. STK Push accepted by Safaricom, log the transaction and update database
            val transactionId = firestore.collection("mpesa_transactions").document().id
            val transactionRef = firestore.collection("mpesa_transactions").document(transactionId)

            val pendingTx = hashMapOf(
                "id" to transactionId,
                "userId" to user.uid,
                "amount" to amount,
                "phoneNumber" to phoneNumber,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis(),
                "merchantRequestId" to (response.merchantRequestId ?: ""),
                "checkoutRequestId" to (response.checkoutRequestId ?: "")
            )

            transactionRef.set(pendingTx)
                .addOnSuccessListener {
                    val configRef = firestore.collection("config").document("global")
                    val loanRef = firestore.collection("loans").document(loan.id)
                    val ledgerDocRef = firestore.collection("ledger").document()

                    val now = Date()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                    val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
                    val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

                    val mpesaRef = if (!response.checkoutRequestId.isNullOrEmpty()) {
                        response.checkoutRequestId
                    } else {
                        "MP_" + (1..8).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
                    }

                    firestore.runTransaction { transaction ->
                        val configSnapshot = transaction.get(configRef)
                        val loanSnapshot = transaction.get(loanRef)

                        val totalBalance = configSnapshot.getDouble("totalBalance") ?: 0.0
                        val activeLoansVal = configSnapshot.getDouble("activeLoans") ?: 0.0

                        val currentLoan = loanSnapshot.toObject(com.example.models.Loan::class.java) ?: throw Exception("Loan not found.")
                        val remaining = currentLoan.remainingBalance

                        if (amount > remaining + 0.01) {
                            throw Exception("Repayment amount KES ${String.format("%,.2f", amount)} exceeds outstanding loan balance of KES ${String.format("%,.2f", remaining)}.")
                        }

                        // Generate Repayment entry
                        val repaymentId = firestore.collection("loans").document().id
                        val repayment = com.example.models.Repayment(
                            repaymentId = repaymentId,
                            loanId = loan.id,
                            amount = amount,
                            paymentMethod = "M-PESA",
                            receiptNumber = mpesaRef,
                            source = "Member",
                            recordedBy = user.fullName,
                            date = dateStr,
                            exactDate = exactDateStr,
                            exactTime = exactTimeStr
                        )

                        val updatedRepayments = currentLoan.repayments + repayment
                        val updatedTotalPaid = updatedRepayments.sumOf { it.amount }
                        val loanTotalRepayment = if (currentLoan.totalRepayment > 0.0) currentLoan.totalRepayment else (currentLoan.principalAmount * 1.10)
                        val newRemaining = (loanTotalRepayment - updatedTotalPaid).coerceAtLeast(0.0)

                        val loanUpdates = hashMapOf<String, Any>(
                            "repayments" to updatedRepayments
                        )

                        val isCleared = newRemaining <= 0.01
                        if (isCleared) {
                            loanUpdates["status"] = "Cleared"
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

                        // Update the mpesa transaction to completed
                        transaction.update(transactionRef, "status", "completed")

                        // 3. Write Immutable Ledger Entry
                        val ledgerEntry = com.example.models.LedgerEntry(
                            ledgerId = ledgerDocRef.id,
                            transactionType = if (isCleared) "Loan Clearance" else "Loan Repayment",
                            relatedDocumentId = loan.id,
                            memberId = user.uid,
                            memberName = user.fullName,
                            performedBy = user.fullName,
                            amount = amount,
                            treasuryBalanceBefore = totalBalance,
                            treasuryBalanceAfter = nextBalance,
                            paymentMethod = "M-PESA",
                            receiptNumber = mpesaRef,
                            source = "Member",
                            notes = "Loan repayment of KES ${String.format("%,.2f", amount)} via Safaricom Daraja STK Push",
                            date = dateStr,
                            exactDate = exactDateStr,
                            exactTime = exactTimeStr,
                            firestoreServerTimestamp = null
                        )
                        transaction.set(ledgerDocRef, ledgerEntry)

                        val activityDocRef = firestore.collection("activities").document()
                        val activityType = if (isCleared) "Loan Repayment Completion" else "Loan Repayment"
                        val activityDetails = if (isCleared) {
                            "You completed loan repayment of KES ${String.format("%,.2f", amount)}. Loan is fully cleared!"
                        } else {
                            "You paid loan instalment of KES ${String.format("%,.2f", amount)}"
                        }
                        val userActivity = com.example.models.UserActivity(
                            id = activityDocRef.id,
                            userId = user.uid,
                            userName = user.fullName,
                            activityType = activityType,
                            amount = amount,
                            details = activityDetails,
                            timestamp = System.currentTimeMillis(),
                            dateStr = exactDateStr,
                            isTreasuryAction = false,
                            serverTimestamp = null
                        )
                        transaction.set(activityDocRef, userActivity)
                    }.addOnSuccessListener {
                        addNotification(
                            title = "Loan Repayment",
                            message = "${user.fullName} repaid KES ${String.format("%,.2f", amount)} towards their loan.",
                            type = "loan_repayment",
                            senderName = user.fullName
                        )
                        onSuccess()
                    }.addOnFailureListener { e ->
                        onFailure(e.localizedMessage ?: "Failed to complete repayment transaction.")
                    }
                }
                .addOnFailureListener { e ->
                    onFailure("Failed to log transaction: ${e.localizedMessage}")
                }
        }
    }

    fun recordManualContribution(
        member: User,
        amount: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Treasurer") {
            onFailure("Only Treasurers can record manual contributions.")
            return
        }
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            onFailure("Please enter a valid amount greater than zero.")
            return
        }

        val configRef = firestore.collection("config").document("global")
        val contribDocRef = firestore.collection("contributions").document()
        val ledgerDocRef = firestore.collection("ledger").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val currentBal = configSnapshot.getDouble("totalBalance") ?: 0.0
            val currentContrib = configSnapshot.getDouble("totalContributions") ?: 0.0

            val newBal = currentBal + amount
            val newContrib = currentContrib + amount

            val contributionData = hashMapOf(
                "id" to contribDocRef.id,
                "userId" to member.uid,
                "userName" to member.fullName,
                "amount" to amount,
                "date" to System.currentTimeMillis(),
                "source" to "treasurer",
                "mpesaReceiptNumber" to "MANUAL_" + (1..8).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString(""),
                "contributorUid" to member.uid,
                "contributorFullName" to member.fullName,
                "contributorPhoneNumber" to member.phoneNumber,
                "paymentMethod" to "Manual Record",
                "isManual" to true,
                "dateContributed" to Date()
            )
            transaction.set(contribDocRef, contributionData)

            transaction.update(
                configRef,
                mapOf(
                    "totalBalance" to newBal,
                    "totalContributions" to newContrib,
                    "lastUpdated" to System.currentTimeMillis()
                )
            )

            val ledgerEntry = com.example.models.LedgerEntry(
                ledgerId = ledgerDocRef.id,
                transactionType = "Manual Contribution",
                relatedDocumentId = contribDocRef.id,
                memberId = member.uid,
                memberName = member.fullName,
                performedBy = user.fullName,
                amount = amount,
                treasuryBalanceBefore = currentBal,
                treasuryBalanceAfter = newBal,
                paymentMethod = "Manual Record",
                receiptNumber = "",
                source = "Treasurer",
                notes = "Recorded manual contribution of KES ${String.format("%,.2f", amount)} for ${member.fullName}",
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
                activityType = "Manual Contribution Record Added",
                amount = amount,
                details = "Treasurer ${user.fullName} manually recorded a contribution of KES ${String.format("%,.2f", amount)} for ${member.fullName}",
                timestamp = System.currentTimeMillis(),
                dateStr = exactDateStr,
                isTreasuryAction = true,
                serverTimestamp = null
            )
            transaction.set(treasuryActivityDocRef, treasuryActivity)
        }.addOnSuccessListener {
            addNotification(
                title = "Manual Contribution Recorded",
                message = "Treasurer ${user.fullName} recorded a contribution of KES ${String.format("%,.2f", amount)} for ${member.fullName}.",
                type = "manual_record",
                senderName = user.fullName
            )
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e.localizedMessage ?: "Failed to record manual contribution.")
        }
    }

    fun recordManualLoan(
        member: User,
        amount: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Treasurer") {
            onFailure("Only Treasurers can disburse manual loans.")
            return
        }
        if (amount <= 0 || amount.isNaN() || amount.isInfinite()) {
            onFailure("Please enter a valid loan amount greater than zero.")
            return
        }

        // Outstanding Loan Constraint: check if user already has an active loan
        firestore.collection("loans")
            .whereEqualTo("applicantId", member.uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                var hasActiveLoan = false
                for (doc in querySnapshot.documents) {
                    val repaymentsVal = doc.get("repayments") as? List<Map<String, Any>> ?: emptyList()
                    val totalRepVal = doc.getDouble("totalRepayment") ?: (doc.getDouble("amount") ?: 0.0) * 1.10
                    val totalPaid = repaymentsVal.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
                    val remaining = totalRepVal - totalPaid
                    val status = doc.getString("status") ?: ""
                    if (status.uppercase() == "APPROVED" && remaining > 0.01) {
                        hasActiveLoan = true
                        break
                    }
                }

                if (hasActiveLoan) {
                    onFailure("Member ${member.fullName} already has an active Approved loan.")
                    return@addOnSuccessListener
                }

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
                        "applicantId" to member.uid,
                        "applicantName" to member.fullName,
                        "amount" to amount,
                        "totalRepayment" to totalRepayment,
                        "status" to "APPROVED",
                        "denialReason" to "",
                        "source" to "treasurer",
                        "createdAt" to Date(),
                        "repayments" to emptyList<com.example.models.Repayment>(),

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

                    transaction.set(loanDocRef, loanData)

                    transaction.update(configRef, mapOf(
                        "totalBalance" to totalBalance - amount,
                        "activeLoans" to activeLoansVal + amount,
                        "lastUpdated" to System.currentTimeMillis()
                    ))

                    val ledgerEntry = com.example.models.LedgerEntry(
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
                    addNotification(
                        title = "Manual Loan Recorded",
                        message = "Treasurer ${user.fullName} manually recorded a loan of KES ${String.format("%,.2f", amount)} for ${member.fullName}.",
                        type = "manual_record",
                        senderName = user.fullName
                    )
                    onSuccess()
                }.addOnFailureListener { e ->
                    onFailure(e.localizedMessage ?: "Failed to record manual loan.")
                }
            }
            .addOnFailureListener { e ->
                onFailure("Verification failed: ${e.localizedMessage}")
            }
    }

    fun updateGlobalBalance(
        newBalance: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Treasurer") {
            onFailure("Only Treasurers can update the treasury balance.")
            return
        }
        if (newBalance < 0 || newBalance.isNaN() || newBalance.isInfinite()) {
            onFailure("Please enter a valid balance amount greater than or equal to zero.")
            return
        }

        val configRef = firestore.collection("config").document("global")
        val ledgerDocRef = firestore.collection("ledger").document()
        val activityDocRef = firestore.collection("activities").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val currentBal = configSnapshot.getDouble("totalBalance") ?: 0.0

            transaction.update(
                configRef,
                mapOf(
                    "totalBalance" to newBalance,
                    "lastUpdated" to System.currentTimeMillis()
                )
            )

            val ledgerEntry = com.example.models.LedgerEntry(
                ledgerId = ledgerDocRef.id,
                transactionType = "Manual Balance Update",
                relatedDocumentId = "",
                memberId = user.uid,
                memberName = user.fullName,
                performedBy = user.fullName,
                amount = newBalance - currentBal,
                treasuryBalanceBefore = currentBal,
                treasuryBalanceAfter = newBalance,
                paymentMethod = "Manual Record",
                receiptNumber = "",
                source = "Treasurer",
                notes = "Treasurer ${user.fullName} manually adjusted the available treasury cash from KES ${String.format("%,.2f", currentBal)} to KES ${String.format("%,.2f", newBalance)} (Difference: KES ${String.format("%,.2f", newBalance - currentBal)})",
                date = dateStr,
                exactDate = exactDateStr,
                exactTime = exactTimeStr,
                firestoreServerTimestamp = null
            )
            transaction.set(ledgerDocRef, ledgerEntry)

            val userActivity = com.example.models.UserActivity(
                id = activityDocRef.id,
                userId = user.uid,
                userName = user.fullName,
                activityType = "Manual Balance Update",
                amount = newBalance,
                details = "Treasurer ${user.fullName} manually updated the available treasury balance from KES ${String.format("%,.2f", currentBal)} to KES ${String.format("%,.2f", newBalance)}",
                timestamp = System.currentTimeMillis(),
                dateStr = exactDateStr,
                isTreasuryAction = true,
                serverTimestamp = null
            )
            transaction.set(activityDocRef, userActivity)
        }.addOnSuccessListener {
            addNotification(
                title = "Treasury Balance Updated",
                message = "Treasurer ${user.fullName} manually updated the available treasury balance.",
                type = "balance_update",
                senderName = user.fullName
            )
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e.localizedMessage ?: "Failed to update balance.")
        }
    }

    fun approvePendingLoan(
        loanId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Treasurer") {
            onFailure("Only Treasurers can approve loans.")
            return
        }

        val configRef = firestore.collection("config").document("global")
        val loanRef = firestore.collection("loans").document(loanId)
        val ledgerDocRef = firestore.collection("ledger").document()

        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        var finalApplicantName = "Member"
        var finalPrincipal = 0.0

        firestore.runTransaction { transaction ->
            val configSnapshot = transaction.get(configRef)
            val loanSnapshot = transaction.get(loanRef)

            val totalBalance = configSnapshot.getDouble("totalBalance") ?: 0.0
            val activeLoansVal = configSnapshot.getDouble("activeLoans") ?: 0.0

            val principal = loanSnapshot.getDouble("amount") ?: loanSnapshot.getDouble("principalAmount") ?: loanSnapshot.getDouble("loanAmount") ?: 0.0
            val applicantName = loanSnapshot.getString("applicantName") ?: loanSnapshot.getString("applicantFullName") ?: "Member"
            val applicantId = loanSnapshot.getString("applicantId") ?: loanSnapshot.getString("userId") ?: loanSnapshot.getString("memberId") ?: loanSnapshot.getString("applicantUid") ?: ""

            finalApplicantName = applicantName
            finalPrincipal = principal

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
                "decisionDate" to Date()
            ))

            // 2. Update Global Config
            transaction.update(configRef, mapOf(
                "totalBalance" to totalBalance - principal,
                "activeLoans" to activeLoansVal + principal,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // 3. Write Immutable Ledger Entry
            val ledgerEntry = com.example.models.LedgerEntry(
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
            val treasuryActivityDocRef = firestore.collection("activities").document()
            val treasuryActivity = com.example.models.UserActivity(
                id = treasuryActivityDocRef.id,
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
            transaction.set(treasuryActivityDocRef, treasuryActivity)
        }.addOnSuccessListener {
            addNotification(
                title = "Loan Approved",
                message = "Treasurer ${user.fullName} approved a loan of KES ${String.format("%,.2f", finalPrincipal)} for $finalApplicantName.",
                type = "loan_decision",
                senderName = user.fullName
            )
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e.localizedMessage ?: "Failed to approve loan.")
        }
    }

    fun denyPendingLoan(
        loanId: String,
        reason: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Treasurer") {
            onFailure("Only Treasurers can deny loans.")
            return
        }
        if (reason.trim().isEmpty()) {
            onFailure("Denial reason cannot be empty.")
            return
        }

        val now = Date()
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)
        val exactTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val loanRef = firestore.collection("loans").document(loanId)

        var finalApplicantName = "Member"
        var finalPrincipal = 0.0

        firestore.runTransaction { transaction ->
            val loanSnapshot = transaction.get(loanRef)
            val applicantId = loanSnapshot.getString("applicantId") ?: loanSnapshot.getString("userId") ?: loanSnapshot.getString("memberId") ?: loanSnapshot.getString("applicantUid") ?: ""
            val applicantName = loanSnapshot.getString("applicantName") ?: loanSnapshot.getString("applicantFullName") ?: "Member"
            val principal = loanSnapshot.getDouble("amount") ?: loanSnapshot.getDouble("principalAmount") ?: loanSnapshot.getDouble("loanAmount") ?: 0.0

            finalApplicantName = applicantName
            finalPrincipal = principal

            transaction.update(loanRef, mapOf(
                "status" to "DENIED",
                "denialReason" to reason.trim(),
                "deniedBy" to user.fullName,
                "deniedAt" to Date(),
                "deniedExactDate" to exactDateStr,
                "deniedExactTime" to exactTimeStr,
                "decisionDate" to Date()
            ))

            // 2. Treasurer's official treasury activities log
            val treasuryActivityDocRef = firestore.collection("activities").document()
            val treasuryActivity = com.example.models.UserActivity(
                id = treasuryActivityDocRef.id,
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
            transaction.set(treasuryActivityDocRef, treasuryActivity)
        }.addOnSuccessListener {
            addNotification(
                title = "Loan Denied",
                message = "Treasurer ${user.fullName} denied a loan of KES ${String.format("%,.2f", finalPrincipal)} for $finalApplicantName. Reason: ${reason.trim()}",
                type = "loan_decision",
                senderName = user.fullName
            )
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e.localizedMessage ?: "Failed to deny loan.")
        }
    }

    fun removeMember(member: com.example.models.User, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Admin") {
            onFailure("Permission denied. Only Administrators can remove members.")
            return
        }
        if (member.uid == user.uid) {
            onFailure("You cannot remove yourself from the system.")
            return
        }

        val now = Date()
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)

        firestore.collection("users").document(member.uid)
            .delete()
            .addOnSuccessListener {
                addNotification(
                    title = "Member Removed",
                    message = "Admin ${user.fullName} permanently removed member ${member.fullName} from the group.",
                    type = "member_removed",
                    senderName = user.fullName
                )
                val treasuryActivityDocRef = firestore.collection("activities").document()
                val treasuryActivity = com.example.models.UserActivity(
                    id = treasuryActivityDocRef.id,
                    userId = user.uid,
                    userName = user.fullName,
                    activityType = "Member Removed",
                    amount = 0.0,
                    details = "Admin ${user.fullName} permanently removed member ${member.fullName} (${member.email}) from the system.",
                    timestamp = System.currentTimeMillis(),
                    dateStr = exactDateStr,
                    isTreasuryAction = true,
                    serverTimestamp = null
                )
                firestore.collection("activities").document(treasuryActivityDocRef.id).set(treasuryActivity)
                    .addOnCompleteListener {
                        onSuccess()
                    }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to remove member.")
            }
    }

    fun changeMemberRole(member: com.example.models.User, newRole: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Admin") {
            onFailure("Permission denied. Only Administrators can change member roles.")
            return
        }
        if (member.uid == user.uid) {
            onFailure("You cannot change your own role to prevent lockout.")
            return
        }

        if (newRole == "Treasurer") {
            val hasExistingTreasurer = _members.value.any { it.role == "Treasurer" }
            if (hasExistingTreasurer) {
                onFailure("There is already an active Treasurer in the group. Please change the current Treasurer to a Member first before appointing a new Treasurer.")
                return
            }
        }

        val now = Date()
        val exactDateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(now)

        firestore.collection("users").document(member.uid)
            .update("role", newRole)
            .addOnSuccessListener {
                val treasuryActivityDocRef = firestore.collection("activities").document()
                val treasuryActivity = com.example.models.UserActivity(
                    id = treasuryActivityDocRef.id,
                    userId = user.uid,
                    userName = user.fullName,
                    activityType = "Role Update",
                    amount = 0.0,
                    details = "Admin ${user.fullName} changed ${member.fullName}'s role from ${member.role} to $newRole.",
                    timestamp = System.currentTimeMillis(),
                    dateStr = exactDateStr,
                    isTreasuryAction = true,
                    serverTimestamp = null
                )
                firestore.collection("activities").document(treasuryActivityDocRef.id).set(treasuryActivity)
                    .addOnCompleteListener {
                        onSuccess()
                    }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to change role.")
            }
    }

    fun addNotification(title: String, message: String, type: String, senderName: String = "System") {
        val docRef = firestore.collection("notifications").document()
        val notif = com.example.models.AppNotification(
            id = docRef.id,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            senderName = senderName,
            type = type
        )
        docRef.set(notif)
    }

    fun sendAnnouncement(
        title: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }
        if (user.role != "Admin") {
            onFailure("Permission denied. Only Admins can make group announcements.")
            return
        }
        if (title.trim().isEmpty() || message.trim().isEmpty()) {
            onFailure("Title and message cannot be empty.")
            return
        }

        val docRef = firestore.collection("notifications").document()
        val notif = com.example.models.AppNotification(
            id = docRef.id,
            title = title.trim(),
            message = message.trim(),
            timestamp = System.currentTimeMillis(),
            senderName = user.fullName,
            type = "announcement"
        )

        docRef.set(notif)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to post announcement.")
            }
    }

    fun updateProfile(
        newEmail: String,
        newPhone: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = currentUser ?: run {
            onFailure("User profile is not loaded.")
            return
        }

        val uid = user.uid
        val currentTime = System.currentTimeMillis()
        val daysInMs = 30L * 24L * 60L * 60L * 1000L

        if (user.lastProfileUpdate > 0L && (currentTime - user.lastProfileUpdate) < daysInMs) {
            val elapsed = currentTime - user.lastProfileUpdate
            val daysLeft = 30 - (elapsed / (24L * 60L * 60L * 1000L)).toInt()
            onFailure("You can only update your profile details once every 30 days. Please try again in $daysLeft day(s).")
            return
        }

        // Update in Firebase Auth first if email has changed
        val authUser = firebaseAuth.currentUser
        if (authUser != null && newEmail != user.email) {
            authUser.updateEmail(newEmail)
                .addOnSuccessListener {
                    // Auth email updated successfully, now update Firestore
                    saveProfileToFirestore(uid, user, newEmail, newPhone, currentTime, onSuccess, onFailure)
                }
                .addOnFailureListener { e ->
                    onFailure(e.localizedMessage ?: "Failed to update authentication email. Please re-authenticate and try again.")
                }
        } else {
            // No email change or authUser is null, just update Firestore
            saveProfileToFirestore(uid, user, newEmail, newPhone, currentTime, onSuccess, onFailure)
        }
    }

    private fun saveProfileToFirestore(
        uid: String,
        user: User,
        newEmail: String,
        newPhone: String,
        currentTime: Long,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updates = hashMapOf<String, Any>(
            "email" to newEmail,
            "phoneNumber" to newPhone,
            "lastProfileUpdate" to currentTime
        )

        firestore.collection("users")
            .document(uid)
            .update(updates)
            .addOnSuccessListener {
                // Log activity
                val activityDocRef = firestore.collection("activities").document()
                val exactDateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date(currentTime))
                val activity = com.example.models.UserActivity(
                    id = activityDocRef.id,
                    userId = uid,
                    userName = user.fullName,
                    activityType = "PROFILE_UPDATE",
                    amount = 0.0,
                    details = "Updated email/phone to $newEmail and $newPhone",
                    timestamp = currentTime,
                    dateStr = exactDateStr
                )
                activityDocRef.set(activity)
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to save profile changes to database.")
            }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
