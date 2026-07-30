package io.github.ffelixq.medswidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ffelixq.medswidget.data.AuthRepository
import io.github.ffelixq.medswidget.domain.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: AuthSession? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val firebaseConfigured: Boolean = true,
)

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            AuthUiState(
                session = repository.session.value,
                isLoading = false,
                firebaseConfigured = repository.isConfigured,
            ),
        )
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                mutableState.value = mutableState.value.copy(session = session, isLoading = false)
            }
        }
    }

    fun signIn(
        email: String,
        password: String,
    ) = launchAuth {
        repository.signInWithEmail(email, password)
    }

    fun signUp(
        email: String,
        password: String,
        displayName: String,
    ) = launchAuth {
        repository.signUpWithEmail(email, password, displayName)
    }

    fun signInWithGoogleToken(token: String) =
        launchAuth {
            repository.signInWithGoogleIdToken(token)
        }

    fun resetPassword(email: String) =
        launchAuth(successMessage = "Password reset email requested.") {
            repository.sendPasswordReset(email)
        }

    fun reportError(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, infoMessage = null)
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(errorMessage = null, infoMessage = null)
    }

    private fun launchAuth(
        successMessage: String? = null,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            runCatching { action() }
                .onSuccess {
                    mutableState.value =
                        mutableState.value.copy(isLoading = false, infoMessage = successMessage)
                }.onFailure { error ->
                    mutableState.value =
                        mutableState.value.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Something went wrong. Try again.",
                        )
                }
        }
    }
}
