package com.example.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.common.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val fullName: String = "",
    val idNumber: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class RegisterViewModel(
    private val authRepository: com.example.repository.AuthRepository = ServiceLocator.provideAuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
    private val phoneRegex = "^\\+?[0-9]{9,15}\$".toRegex()
    private val nationalIdRegex = "^[0-9]{5,12}\$".toRegex()

    fun onFullNameChange(fullName: String) {
        _uiState.update { it.copy(fullName = fullName, error = null) }
    }

    fun onIdNumberChange(idNumber: String) {
        _uiState.update { it.copy(idNumber = idNumber, error = null) }
    }

    fun onPhoneNumberChange(phoneNumber: String) {
        _uiState.update { it.copy(phoneNumber = phoneNumber, error = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, error = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null) }
    }

    fun register(onSuccess: () -> Unit) {
        val fullName = _uiState.value.fullName.trim()
        val idNumber = _uiState.value.idNumber.trim()
        val phoneNumber = _uiState.value.phoneNumber.trim()
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val confirmPassword = _uiState.value.confirmPassword

        // 1. Validation Checks
        if (fullName.isEmpty()) {
            _uiState.update { it.copy(error = "Full Name is required.") }
            return
        }

        if (idNumber.isEmpty()) {
            _uiState.update { it.copy(error = "National ID Number is required.") }
            return
        }

        if (!idNumber.matches(nationalIdRegex)) {
            _uiState.update { it.copy(error = "Please enter a valid National ID Number (5 to 12 digits).") }
            return
        }

        if (phoneNumber.isEmpty()) {
            _uiState.update { it.copy(error = "Phone Number is required.") }
            return
        }

        if (!phoneNumber.matches(phoneRegex)) {
            _uiState.update { it.copy(error = "Please enter a valid phone number (9 to 15 digits).") }
            return
        }

        if (email.isEmpty()) {
            _uiState.update { it.copy(error = "Email address is required.") }
            return
        }

        if (!email.matches(emailRegex)) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        if (password.isEmpty()) {
            _uiState.update { it.copy(error = "Password is required.") }
            return
        }

        if (password.length < 8) {
            _uiState.update { it.copy(error = "Password must be at least 8 characters.") }
            return
        }

        if (confirmPassword != password) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        // 2. Perform Account Registration
        viewModelScope.launch {
            authRepository.registerUser(
                fullName = fullName,
                idNumber = idNumber,
                phoneNumber = phoneNumber,
                email = email,
                password = password
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onSuccess()
            }.onFailure { exception ->
                val errorMsg = exception.localizedMessage ?: "Registration failed. Please try again."
                _uiState.update { it.copy(isLoading = false, error = errorMsg) }
            }
        }
    }
}
