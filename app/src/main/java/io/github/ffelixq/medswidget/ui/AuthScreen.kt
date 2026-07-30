package io.github.ffelixq.medswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ffelixq.medswidget.domain.DISPLAY_NAME_MAX_LENGTH

@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun AuthScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onReset: (String) -> Unit,
    onGoogle: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp)
                .testTag("auth_screen"),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("Meds Widget", style = MaterialTheme.typography.headlineLarge)
        Text(
            "A simple medicine tracker with home-screen widgets.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))

        if (mode == AuthMode.SIGN_UP) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(80) },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("display_name"),
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trimStart() },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("email"),
        )
        if (mode != AuthMode.RESET) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText =
                    if (mode == AuthMode.SIGN_UP) {
                        { Text("At least 6 characters") }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth().testTag("password"),
            )
        }

        val message = localError ?: state.errorMessage
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.infoMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.firebaseConfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This build does not contain Firebase configuration.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            enabled = !state.isLoading && state.firebaseConfigured,
            onClick = {
                val validationError = validateAuth(mode, email, password, displayName)
                if (validationError != null) {
                    localError = validationError
                    return@Button
                }
                localError = null
                when (mode) {
                    AuthMode.SIGN_IN -> onSignIn(email.trim(), password)
                    AuthMode.SIGN_UP -> onSignUp(email.trim(), password, displayName.trim())
                    AuthMode.RESET -> onReset(email.trim())
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_submit"),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
            } else {
                Text(
                    when (mode) {
                        AuthMode.SIGN_IN -> "Sign in"
                        AuthMode.SIGN_UP -> "Create account"
                        AuthMode.RESET -> "Send reset email"
                    },
                )
            }
        }

        if (mode != AuthMode.RESET) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onGoogle,
                enabled = !state.isLoading && state.firebaseConfigured,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Continue with Google")
            }
        }

        Spacer(Modifier.height(12.dp))
        when (mode) {
            AuthMode.SIGN_IN -> {
                TextButton(
                    onClick = { mode = AuthMode.SIGN_UP },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Create an email account")
                }
                TextButton(
                    onClick = { mode = AuthMode.RESET },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Forgot password?")
                }
            }

            AuthMode.SIGN_UP, AuthMode.RESET -> {
                TextButton(
                    onClick = { mode = AuthMode.SIGN_IN },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Back to sign in")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Meds Widget is a tracking utility. It does not provide medical or dosing advice.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
fun DisplayNameSetupScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSave: (String) -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp)
                .testTag("display_name_setup"),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("What should we call you?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Your display name is shown only inside your account settings.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(DISPLAY_NAME_MAX_LENGTH) },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("required_display_name"),
        )
        (localError ?: errorMessage)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !isLoading,
            onClick = {
                val normalized = displayName.trim()
                if (normalized.isEmpty()) {
                    localError = "Enter a display name."
                } else {
                    localError = null
                    onSave(normalized)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_required_display_name"),
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
            } else {
                Text("Continue")
            }
        }
    }
}

private fun validateAuth(
    mode: AuthMode,
    email: String,
    password: String,
    displayName: String,
): String? =
    when {
        !email.contains("@") || email.length < 3 -> "Enter a valid email address."
        mode != AuthMode.RESET && password.length < 6 -> "Password must contain at least 6 characters."
        mode == AuthMode.SIGN_UP && displayName.trim().isEmpty() -> "Enter a display name."
        else -> null
    }
