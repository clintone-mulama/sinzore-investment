package com.example.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.common.components.ConfirmationDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Clean domain model representing unified application level errors.
 */
data class AppError(
    val title: String = "Something went wrong",
    val message: String,
    val code: String? = null,
    val onRetry: (() -> Unit)? = null
)

/**
 * Centralized, thread-safe manager for system-wide exceptions and API errors.
 */
object GlobalErrorManager {
    private val _errorFlow = MutableStateFlow<AppError?>(null)
    val errorFlow: StateFlow<AppError?> = _errorFlow.asStateFlow()

    /**
     * Publishes a new application error to be handled by the UI.
     */
    fun publishError(error: AppError) {
        _errorFlow.value = error
    }

    /**
     * Publishes a general error message.
     */
    fun publishError(message: String, title: String = "Error occurred", code: String? = null, onRetry: (() -> Unit)? = null) {
        _errorFlow.value = AppError(title = title, message = message, code = code, onRetry = onRetry)
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _errorFlow.value = null
    }
}

/**
 * A beautiful Compose layout wrapper that automatically listens to and intercepts published errors,
 * prompting the user with a standardized Material 3 dialog.
 */
@Composable
fun GlobalErrorHandlerWrapper(
    content: @Composable () -> Unit
) {
    val activeError by GlobalErrorManager.errorFlow.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        activeError?.let { error ->
            ConfirmationDialog(
                title = error.title,
                message = if (error.code != null) "${error.message}\n(Code: ${error.code})" else error.message,
                confirmButtonText = if (error.onRetry != null) "Retry" else "Dismiss",
                dismissButtonText = if (error.onRetry != null) "Cancel" else "",
                onConfirm = {
                    val retryAction = error.onRetry
                    GlobalErrorManager.clearError()
                    retryAction?.invoke()
                },
                onDismiss = {
                    GlobalErrorManager.clearError()
                }
            )
        }
    }
}
