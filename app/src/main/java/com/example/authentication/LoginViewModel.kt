package com.example.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.common.ServiceLocator
import com.example.settings.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val resetEmailSent: Boolean = false,
    val resetEmailError: String? = null
)

class LoginViewModel(
    private val authRepository: com.example.repository.AuthRepository = ServiceLocator.provideAuthRepository(),
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null, resetEmailError = null, resetEmailSent = false) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleRememberMe() {
        _uiState.update { it.copy(rememberMe = !it.rememberMe) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, resetEmailError = null, resetEmailSent = false) }
    }

    fun login(onSuccess: () -> Unit) {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter your email address.") }
            return
        }

        if (password.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter your password.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            authRepository.loginUser(email, password)
                .onSuccess { user ->
                    if (user.status != "Active") {
                        _uiState.update { it.copy(isLoading = false, error = "This account is currently inactive. Please contact an Administrator.") }
                    } else {
                        // Save session details to DataStore Preferences
                        preferencesManager.saveSession(
                            uid = user.uid,
                            email = user.email,
                            name = user.fullName,
                            role = user.role
                        )
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        onSuccess()
                    }
                }
                .onFailure { exception ->
                    val errorMsg = exception.localizedMessage ?: "Invalid email or password. Please try again."
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isEmpty()) {
            _uiState.update { it.copy(resetEmailError = "Please enter your email address to reset password.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, resetEmailError = null, resetEmailSent = false) }

        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, resetEmailSent = true) }
                }
                .onFailure { exception ->
                    val errorMsg = exception.localizedMessage ?: "Failed to send reset email. Verify your email address is correct."
                    _uiState.update { it.copy(isLoading = false, resetEmailError = errorMsg) }
                }
        }
    }
}
