package com.example.contributions
 
import androidx.lifecycle.ViewModel
import com.example.models.Contribution
import com.example.models.GlobalConfig
import com.example.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ContributionsUiState {
    object Loading : ContributionsUiState
    data class Success(
        val currentUser: User,
        val config: GlobalConfig,
        val contributions: List<Contribution>
    ) : ContributionsUiState
    data class Error(val message: String) : ContributionsUiState
}

class ContributionsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ContributionsUiState>(ContributionsUiState.Loading)
    val uiState: StateFlow<ContributionsUiState> = _uiState.asStateFlow()

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
    private var contributionsListenerRegistration: ListenerRegistration? = null

    private var currentUser: User? = null
    private var currentConfig: GlobalConfig = GlobalConfig()
    private var currentContributions: List<Contribution> = emptyList()

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
        _uiState.value = ContributionsUiState.Loading
        clearMessages()

        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            _uiState.value = ContributionsUiState.Error("Session expired. Please log in again.")
            return
        }

        cleanupListeners()

        // 1. Sync User Profile
        userListenerRegistration = firestore.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = ContributionsUiState.Error("Profile sync failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        currentUser = user
                        updateState()
                    } else {
                        _uiState.value = ContributionsUiState.Error("User profile could not be read.")
                    }
                } else {
                    _uiState.value = ContributionsUiState.Error("User profile does not exist.")
                }
            }

        // 2. Sync Live Global Config (Treasury Balance)
        configListenerRegistration = firestore.collection("config")
            .document("global")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = ContributionsUiState.Error("Failed to sync treasury config: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val config = snapshot.toObject(GlobalConfig::class.java)
                    currentConfig = config ?: GlobalConfig()
                } else {
                    currentConfig = GlobalConfig()
                }
                updateState()
            }

        // 3. Sync Contributions Ledger (Real-Time)
        contributionsListenerRegistration = firestore.collection("contributions")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = ContributionsUiState.Error("Contributions sync failed: ${error.localizedMessage}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = mutableListOf<Contribution>()
                    for (doc in snapshot.documents) {
                        val contribution = doc.toObject(Contribution::class.java)
                        if (contribution != null) {
                            list.add(contribution.copy(id = doc.id))
                        }
                    }
                    currentContributions = list
                    updateState()
                } else {
                    currentContributions = emptyList()
                    updateState()
                }
            }
    }

    private fun updateState() {
        val user = currentUser
        if (user != null) {
            _uiState.value = ContributionsUiState.Success(
                currentUser = user,
                config = currentConfig,
                contributions = currentContributions
            )
        }
    }

    private fun cleanupListeners() {
        userListenerRegistration?.remove()
        userListenerRegistration = null
        configListenerRegistration?.remove()
        configListenerRegistration = null
        contributionsListenerRegistration?.remove()
        contributionsListenerRegistration = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
