package io.github.ffelixq.medswidget.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.ffelixq.medswidget.AppGraph
import io.github.ffelixq.medswidget.MedsApplication
import io.github.ffelixq.medswidget.ui.theme.MedsWidgetTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph: AppGraph by lazy { MedsApplication.graph(this) }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(this) }

    private val authViewModel by viewModels<AuthViewModel> {
        CreatorFactory { AuthViewModel(graph.repositories.auth) }
    }
    private val mainViewModel by viewModels<MainViewModel> {
        CreatorFactory { MainViewModel(graph) }
    }
    private val historyViewModel by viewModels<HistoryViewModel> {
        CreatorFactory { HistoryViewModel(graph) }
    }
    private val settingsViewModel by viewModels<SettingsViewModel> {
        CreatorFactory {
            SettingsViewModel(
                graph = graph,
                reinitializeApplicationGraph =
                    (application as MedsApplication)::reinitializeAfterAccountDeletion,
                awaitOutstandingWrites = graph::awaitOutstandingWrites,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val authState by authViewModel.state.collectAsStateWithLifecycle()
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(authState.session?.uid) {
                if (authState.session == null) {
                    runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
                }
            }
            LaunchedEffect(authState.session?.uid, authState.session?.displayName) {
                authState.session
                    ?.displayName
                    ?.takeIf(String::isNotBlank)
                    ?.let(settingsViewModel::ensureDisplayName)
            }
            LaunchedEffect(settingsState.accountDeletionCompleted) {
                if (settingsState.accountDeletionCompleted) {
                    startActivity(
                        Intent(this@MainActivity, MainActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                        ),
                    )
                    finish()
                }
            }
            MedsWidgetTheme(settingsState.settings.themePreference) {
                if (settingsState.isDeletingAccount) {
                    AccountDeletionProgressScreen()
                } else if (authState.session == null) {
                    AuthScreen(
                        state = authState,
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        onReset = authViewModel::resetPassword,
                        onGoogle = {
                            requestGoogleCredential(
                                onToken = authViewModel::signInWithGoogleToken,
                                onError = authViewModel::reportError,
                            )
                        },
                    )
                } else if (authState.session?.displayName.isNullOrBlank()) {
                    DisplayNameSetupScreen(
                        isLoading = settingsState.isBusy,
                        errorMessage = settingsState.errorMessage,
                        onSave = settingsViewModel::updateDisplayName,
                    )
                } else {
                    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
                    AppNavigation(
                        mainState = mainState,
                        settingsState = settingsState,
                        mainViewModel = mainViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel,
                        onDeleteGoogle = {
                            requestGoogleCredential(
                                onToken = { token -> settingsViewModel.deleteAccount(null, token) },
                                onError = settingsViewModel::reportError,
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshTemporalState()
    }

    // google-services creates this resource only for configured builds. A direct R reference
    // would make the intentionally config-free CI build fail at compile time.
    @SuppressLint("DiscouragedApi")
    private fun requestGoogleCredential(
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val clientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientIdResource == 0) {
            onError("Google sign-in is not configured in this build.")
            return
        }
        val clientId = getString(clientIdResource)
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        lifecycleScope.launch {
            try {
                val credential = credentialManager.getCredential(this@MainActivity, request).credential
                if (
                    credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    onToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    onError("Google did not return a usable sign-in credential.")
                }
            } catch (_: GetCredentialCancellationException) {
                onError("Google sign-in was cancelled.")
            } catch (_: NoCredentialException) {
                onError("No Google account is available on this device.")
            } catch (_: Exception) {
                onError("Google sign-in could not be completed. Check your connection and try again.")
            }
        }
    }
}

private object Routes {
    const val MAIN = "main"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ADD = "medicine/new"
    const val EDIT = "medicine/{medicineId}"
}

@Suppress("FunctionNaming")
@androidx.compose.runtime.Composable
private fun AppNavigation(
    mainState: MainUiState,
    settingsState: SettingsUiState,
    mainViewModel: MainViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onDeleteGoogle: () -> Unit,
) {
    val navigation = rememberNavController()
    NavHost(navController = navigation, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                state = mainState,
                onCheck = mainViewModel::check,
                onUndo = mainViewModel::undo,
                onStartCountdown = mainViewModel::startCountdown,
                onCancelCountdown = mainViewModel::cancelCountdown,
                onRestartCountdown = mainViewModel::restartCountdown,
                onAdd = { navigation.navigate(Routes.ADD) },
                onEdit = { navigation.navigate("medicine/${it.id}") },
                onHistory = { navigation.navigate(Routes.HISTORY) },
                onSettings = { navigation.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            val historyState by historyViewModel.state.collectAsStateWithLifecycle()
            HistoryScreen(historyState, onBack = navigation::popBackStack)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = settingsState,
                onBack = navigation::popBackStack,
                onResetTime = settingsViewModel::updateResetTime,
                onTheme = settingsViewModel::updateTheme,
                onDisplayName = settingsViewModel::updateDisplayName,
                onSignOut = settingsViewModel::signOut,
                onDeletePasswordAccount = { settingsViewModel.deleteAccount(it) },
                onDeleteGoogleAccount = onDeleteGoogle,
            )
        }
        composable(Routes.ADD) {
            MedicineScreen(
                medicine = null,
                onBack = navigation::popBackStack,
                onSave = mainViewModel::saveMedicine,
                onArchive = mainViewModel::archiveMedicine,
                onDelete = mainViewModel::deleteMedicine,
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(navArgument("medicineId") { type = NavType.StringType }),
        ) { entry ->
            val medicine = mainState.medicines.firstOrNull { it.id == entry.arguments?.getString("medicineId") }
            MedicineScreen(
                medicine = medicine,
                onBack = navigation::popBackStack,
                onSave = mainViewModel::saveMedicine,
                onArchive = mainViewModel::archiveMedicine,
                onDelete = mainViewModel::deleteMedicine,
                activeCountdowns =
                    mainState.rows
                        .filter { it.medicineId == medicine?.id }
                        .mapNotNull { it.countdown },
            )
        }
    }
}

private class CreatorFactory<VM : ViewModel>(
    private val creator: () -> VM,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
