package io.github.ffelixq.medswidget.ui

import io.github.ffelixq.medswidget.domain.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state reflects signed-out and Firebase configuration state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository(isConfigured = false)
            val viewModel = AuthViewModel(repository)

            runCurrent()

            assertNull(viewModel.state.value.session)
            assertFalse(viewModel.state.value.isLoading)
            assertFalse(viewModel.state.value.firebaseConfigured)
        }

    @Test
    fun `repository authentication transitions are reflected in state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)
            val session =
                AuthSession(
                    uid = "user-a",
                    displayName = "User A",
                    email = "a@example.com",
                    providers = setOf("password"),
                )

            repository.session.value = session
            advanceUntilIdle()
            assertEquals(session, viewModel.state.value.session)

            repository.session.value = null
            advanceUntilIdle()
            assertNull(viewModel.state.value.session)
        }

    @Test
    fun `email sign-in exposes loading then signed-in state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            val gate = CompletableDeferred<Unit>()
            repository.operationGate = gate
            val viewModel = AuthViewModel(repository)

            viewModel.signIn("person@example.com", "correct horse battery staple")
            runCurrent()

            assertTrue(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.errorMessage)

            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(
                "person@example.com" to "correct horse battery staple",
                repository.emailSignIns.single(),
            )
            assertEquals(
                "email-user",
                viewModel.state.value.session
                    ?.uid,
            )
        }

    @Test
    fun `email registration and Google authentication call their distinct repository paths`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)

            viewModel.signUp("new@example.com", "password-value", "New Person")
            advanceUntilIdle()
            assertEquals(
                Triple("new@example.com", "password-value", "New Person"),
                repository.emailSignUps.single(),
            )
            assertEquals(
                "new-user",
                viewModel.state.value.session
                    ?.uid,
            )

            viewModel.signInWithGoogleToken("google-id-token")
            advanceUntilIdle()
            assertEquals(listOf("google-id-token"), repository.googleTokens)
            assertEquals(
                "google-user",
                viewModel.state.value.session
                    ?.uid,
            )
        }

    @Test
    fun `friendly repository failure is surfaced and can be cleared`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            repository.nextFailure = IllegalStateException("No network connection")
            val viewModel = AuthViewModel(repository)

            viewModel.signIn("person@example.com", "password-value")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("No network connection", viewModel.state.value.errorMessage)

            viewModel.clearMessage()
            assertNull(viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.infoMessage)
        }

    @Test
    fun `password reset reports success only after repository completion`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)

            viewModel.resetPassword("person@example.com")
            advanceUntilIdle()

            assertEquals(listOf("person@example.com"), repository.passwordResetEmails)
            assertEquals("Password reset email requested.", viewModel.state.value.infoMessage)
            assertNull(viewModel.state.value.errorMessage)
        }

    @Test
    fun `screen-reported errors replace informational messages`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)
            viewModel.resetPassword("person@example.com")
            advanceUntilIdle()

            viewModel.reportError("Credential flow was cancelled")

            assertEquals("Credential flow was cancelled", viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.infoMessage)
        }
}
