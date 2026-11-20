package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOutUser
import com.example.data.LocalPreferencesStore
import com.example.data.ReadingRepository
import com.example.notifications.ReadingReminderScheduler
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogSessionSheet
import com.example.ui.screens.MobileLoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WeeklyCompareScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.PageStreakTheme
import com.example.viewmodel.ReadingUiState
import com.example.viewmodel.ReadingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class AppTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("today", R.string.nav_today, Icons.Filled.LocalFireDepartment, Icons.Outlined.LocalFireDepartment),
    WEEKLY("weekly", R.string.nav_weekly, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReadingReminderScheduler.createNotificationChannel(applicationContext)

        setContent {
            val localPrefs = remember(applicationContext) { LocalPreferencesStore(applicationContext) }
            val themeMode by localPrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            PageStreakTheme(themeMode = themeMode) {
                AppNavigation(
                    themeMode = themeMode,
                    onThemeModeChange = { next ->
                        scope.launch { localPrefs.setThemeMode(next) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedPageStreakApp(
                currentUserId = uid,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onSignedOut = { currentUser = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedPageStreakApp(
    currentUserId: String,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: ReadingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ReadingViewModel(
                    repository = ReadingRepository(db),
                    localPrefs = LocalPreferencesStore(app),
                    currentUserId = currentUserId,
                    onScheduleReminder = { enabled, hour, minute ->
                        ReadingReminderScheduler.scheduleDailyReminder(app, enabled, hour, minute)
                    }
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TODAY) }
    var showLogSheet by rememberSaveable { mutableStateOf(false) }
    var hadPreviousReader by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    val performSignOut: () -> Unit = {
        signOutUser(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignedOut
        )
    }

    when (val state = uiState) {
        ReadingUiState.Loading -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Syncing reading circle...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is ReadingUiState.Error -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Connection Interrupted",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.retryInitialization() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("retry_connection_button")
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }

        is ReadingUiState.NeedsMobileId -> {
            if (hadPreviousReader) {
                BackHandler {
                    viewModel.cancelSwitchMobileId()
                }
            }
            MobileLoginScreen(
                errorMessage = state.errorMessage,
                isSubmitting = state.isSubmitting,
                canCancelSwitch = hadPreviousReader,
                onSubmitMobileNumber = { number ->
                    viewModel.verifyAndLoginWithMobile(number)
                },
                onClearError = { viewModel.clearMobileError() },
                onCancelSwitch = { viewModel.cancelSwitchMobileId() },
                onSignOut = performSignOut
            )
        }

        is ReadingUiState.Ready -> {
            hadPreviousReader = true

            if (selectedTab != AppTab.TODAY) {
                BackHandler {
                    selectedTab = AppTab.TODAY
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = {
                        if (selectedTab == AppTab.TODAY) {
                            ExtendedFloatingActionButton(
                                onClick = { showLogSheet = true },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.cd_add_session)
                                    )
                                },
                                text = { Text(stringResource(R.string.action_log_reading)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("log_reading_fab")
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavigationRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                AppTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag("nav_${tab.routeId}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(Modifier.height(12.dp))
                                AppTab.entries.forEach { tab ->
                                    val selected = selectedTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag("rail_${tab.routeId}")
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (selectedTab) {
                                AppTab.TODAY -> DashboardScreen(
                                    state = state,
                                    currentUserId = currentUserId,
                                    timerState = timerState,
                                    onQuickLog = { mins ->
                                        viewModel.logReadingSession(
                                            minutes = mins,
                                            bookTitle = state.myStats.lastBookTitle.takeIf { it.isNotBlank() },
                                            note = null
                                        )
                                    },
                                    onOpenLogSheet = { showLogSheet = true },
                                    onDeleteSession = { viewModel.deleteSession(it) },
                                    onToggleTimer = { viewModel.toggleTimer() },
                                    onResetTimer = { viewModel.resetTimer() },
                                    onSaveTimerSession = {
                                        viewModel.finishAndLogTimer(
                                            state.myStats.lastBookTitle.takeIf { it.isNotBlank() }
                                        )
                                    },
                                    onViewPartnerWeekly = { selectedTab = AppTab.WEEKLY }
                                )

                                AppTab.WEEKLY -> WeeklyCompareScreen(state = state)

                                AppTab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    themeMode = themeMode,
                                    onThemeModeChange = onThemeModeChange,
                                    onUpdateReminder = { enabled, hour, minute ->
                                        viewModel.updateReminderSettings(enabled, hour, minute)
                                    },
                                    onSendTestNotification = {
                                        val posted = ReadingReminderScheduler.postBehindOnGoalNotification(
                                            context = context,
                                            readerName = state.activeReader.shortName,
                                            todayMinutes = state.myStats.todayMinutes,
                                            currentStreak = state.myStats.currentStreak,
                                            isTestPreview = true
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (posted) "Streak reminder notification sent!"
                                                else "Enable notifications in Android settings to preview reminders."
                                            )
                                        }
                                    },
                                    onSwitchMobileId = { viewModel.openSwitchMobileId() },
                                    onSignOut = performSignOut
                                )
                            }
                        }
                    }
                }

                if (showLogSheet) {
                    LogSessionSheet(
                        readerShortName = state.activeReader.shortName,
                        currentTodayMinutes = state.myStats.todayMinutes,
                        defaultBookTitle = state.myStats.lastBookTitle,
                        isSaving = state.isSavingSession,
                        onDismiss = { showLogSheet = false },
                        onConfirmLog = { mins, book, note ->
                            viewModel.logReadingSession(mins, book, note)
                        }
                    )
                }
            }
        }
    }
}
