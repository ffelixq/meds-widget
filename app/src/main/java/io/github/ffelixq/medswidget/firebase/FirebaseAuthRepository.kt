package io.github.ffelixq.medswidget.firebase

import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import io.github.ffelixq.medswidget.data.AuthRepository
import io.github.ffelixq.medswidget.domain.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

@Suppress("TooManyFunctions")
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
) : AuthRepository {
    private val mutableSession = MutableStateFlow(auth.currentUser?.toSession())
    override val session: StateFlow<AuthSession?> = mutableSession.asStateFlow()
    override val isConfigured: Boolean = true

    private val listener =
        FirebaseAuth.AuthStateListener { currentAuth ->
            mutableSession.value = currentAuth.currentUser?.toSession()
        }

    init {
        auth.addAuthStateListener(listener)
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ) {
        firebaseCall {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
    ) {
        firebaseCall {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val normalizedName = displayName.trim()
            if (normalizedName.isNotEmpty()) {
                result.user
                    ?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(normalizedName).build())
                    ?.await()
                auth.currentUser?.reload()?.await()
                mutableSession.value = auth.currentUser?.toSession()
            }
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        firebaseCall {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseCall {
            auth.sendPasswordResetEmail(email.trim()).await()
        }
    }

    override suspend fun updateDisplayName(displayName: String) {
        val user = auth.currentUser ?: throw AuthFriendlyException("Sign in again to update your name.")
        firebaseCall {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()).await()
            user.reload().await()
            mutableSession.value = auth.currentUser?.toSession()
        }
    }

    override suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw AuthFriendlyException("Sign in again before deleting this account.")
        val email =
            user.email
                ?: throw AuthFriendlyException(
                    "This account uses Google sign-in. Sign in again with Google first.",
                )
        firebaseCall {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        }
    }

    override suspend fun reauthenticateWithGoogleIdToken(idToken: String) {
        val user = auth.currentUser ?: throw AuthFriendlyException("Sign in again before deleting this account.")
        firebaseCall {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        }
    }

    override suspend fun deleteAuthenticationAccount() {
        val user = auth.currentUser ?: return
        firebaseCall {
            user.delete().await()
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        mutableSession.value = null
    }

    fun close() {
        auth.removeAuthStateListener(listener)
    }

    private suspend fun <T> firebaseCall(block: suspend () -> T): T =
        try {
            block()
        } catch (error: FirebaseAuthException) {
            throw AuthFriendlyException(FirebaseErrorMessages.forAuthCode(error.errorCode), error)
        } catch (error: FirebaseException) {
            throw AuthFriendlyException(
                "Authentication could not be completed. Check your connection and try again.",
                error,
            )
        }
}

class AuthFriendlyException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun FirebaseUser.toSession(): AuthSession =
    AuthSession(
        uid = uid,
        displayName = displayName.orEmpty(),
        email = email,
        isAnonymous = isAnonymous,
        providers = providerData.mapNotNull { it.providerId }.toSet(),
    )

object FirebaseErrorMessages {
    fun forAuthCode(code: String): String =
        when (code) {
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "The email or password is incorrect."
            "ERROR_USER_NOT_FOUND" -> "No account was found for that email."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses that email."
            "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a little before trying again."
            "ERROR_NETWORK_REQUEST_FAILED" -> "You appear to be offline. Reconnect and try again."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "For security, sign in again before continuing."
            else -> "Authentication could not be completed. Try again."
        }
}
