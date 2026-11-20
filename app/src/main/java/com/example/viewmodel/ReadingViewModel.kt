package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthorizedReader
import com.example.data.AuthorizedReaders
import com.example.data.ComputedReaderStats
import com.example.data.LocalPreferencesStore
import com.example.data.ReaderProfile
import com.example.data.ReadingCircle
import com.example.data.ReadingRepository
import com.example.data.ReadingSession
import com.example.data.StreakCalculator
import com.example.data.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReadingUiState {
    data object Loading : ReadingUiState
    data class Error(val message: String) : ReadingUiState
    data class NeedsMobileId(
        val errorMessage: String? = null,
        val isSubmitting: Boolean = false
    ) : ReadingUiState

    data class Ready(
        val activeReader: AuthorizedReader,
        val partnerReader: AuthorizedReader,
        val myStats: ComputedReaderStats,
        val partnerStats: ComputedReaderStats,
        val myProfile: ReaderProfile?,
        val partnerProfile: ReaderProfile?,
        val circle: ReadingCircle?,
        val allSessions: List<ReadingSession>,
        val myTodaySessions: List<ReadingSession>,
        val partnerTodaySessions: List<ReadingSession>,
        val reminderEnabled: Boolean,
        val reminderHour: Int,
        val reminderMinute: Int,
        val isSavingSession: Boolean = false
    ) : ReadingUiState
}

data class TimerState(
    val running: Boolean = false,
    val elapsedSeconds: Long = 0L
)

private sealed interface BootstrapState {
    data object Initializing : BootstrapState
    data object Ready : BootstrapState
    data class Failed(val message: String) : BootstrapState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModel(
    private val repository: ReadingRepository,
    private val localPrefs: LocalPreferencesStore,
    private val currentUserId: String,
    private val onScheduleReminder: (enabled: Boolean, hour: Int, minute: Int) -> Unit
) : ViewModel() {

    private val bootstrapState = MutableStateFlow<BootstrapState>(BootstrapState.Initializing)
    private val _switchingReader = MutableStateFlow(false)
    private val _mobileInputError = MutableStateFlow<String?>(null)
    private val _isSubmittingMobile = MutableStateFlow(false)
    private val _isSavingSession = MutableStateFlow(false)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    private var timerJob: Job? = null

    private var lastSyncedMemberCount = 0

    init {
        initializeCircle()
    }

    fun retryInitialization() {
        initializeCircle()
    }

    private fun initializeCircle() {
        viewModelScope.launch {
            bootstrapState.value = BootstrapState.Initializing
            val result = repository.ensureCircleMembership()
            bootstrapState.value = result.fold(
                onSuccess = { BootstrapState.Ready },
                onFailure = { err ->
                    BootstrapState.Failed(
                        err.localizedMessage ?: "Could not connect to reading circle."
                    )
                }
            )
        }
    }

    private fun <T> observeSafe(
        flowProvider: () -> kotlinx.coroutines.flow.Flow<T>,
        fallbackLabel: String
    ): kotlinx.coroutines.flow.Flow<Result<T>> {
        return flowProvider()
            .map { Result.success(it) }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Stream error ($fallbackLabel)", root)
                emit(Result.failure(root))
            }
    }

    private data class CloudSnapshot(
        val userAccount: UserAccount?,
        val circle: ReadingCircle?,
        val narsinhProfile: ReaderProfile?,
        val vishwarajProfile: ReaderProfile?,
        val sessions: List<ReadingSession>
    )

    private val cloudFlow: kotlinx.coroutines.flow.Flow<Result<CloudSnapshot>> =
        bootstrapState.flatMapLatest { boot ->
            when (boot) {
                BootstrapState.Initializing -> flowOf(Result.failure(InitializingSentinel))
                is BootstrapState.Failed -> flowOf(Result.failure(IllegalStateException(boot.message)))
                BootstrapState.Ready -> {
                    combine(
                        observeSafe({ repository.observeUserAccount(currentUserId) }, "userAccount"),
                        observeSafe({ repository.observeCircle(currentUserId) }, "circle"),
                        observeSafe({ repository.observeReaderProfile(currentUserId, AuthorizedReaders.NARSINH.readerId) }, "narsinh"),
                        observeSafe({ repository.observeReaderProfile(currentUserId, AuthorizedReaders.VISHWARAJ.readerId) }, "vishwaraj"),
                        observeSafe({ repository.observeSessions(currentUserId) }, "sessions")
                    ) { userRes, circleRes, narsinhRes, vishwarajRes, sessionsRes ->
                        val firstFailure = listOf(userRes, circleRes, narsinhRes, vishwarajRes, sessionsRes)
                            .firstOrNull { it.isFailure }
                            ?.exceptionOrNull()
                        if (firstFailure != null) {
                            Result.failure(firstFailure)
                        } else {
                            Result.success(
                                CloudSnapshot(
                                    userAccount = userRes.getOrNull(),
                                    circle = circleRes.getOrNull(),
                                    narsinhProfile = narsinhRes.getOrNull(),
                                    vishwarajProfile = vishwarajRes.getOrNull(),
                                    sessions = sessionsRes.getOrDefault(emptyList())
                                )
                            )
                        }
                    }
                }
            }
        }

    val uiState: StateFlow<ReadingUiState> = combine(
        cloudFlow,
        _switchingReader,
        _mobileInputError,
        _isSubmittingMobile,
        _isSavingSession
    ) { cloudResult, switching, mobileErr, submittingMobile, savingSession ->
        val err = cloudResult.exceptionOrNull()
        if (err === InitializingSentinel) {
            return@combine ReadingUiState.Loading
        }
        if (err != null) {
            return@combine ReadingUiState.Error(err.message ?: "Unable to load reading data.")
        }

        val data = cloudResult.getOrNull() ?: return@combine ReadingUiState.Loading
        val account = data.userAccount
        val activeReader = AuthorizedReaders.findByReaderId(account?.activeReaderId)

        if (activeReader == null || switching) {
            return@combine ReadingUiState.NeedsMobileId(
                errorMessage = mobileErr,
                isSubmitting = submittingMobile
            )
        }

        // Backfill newly joined partner UID onto current user's old sessions if needed
        val circleMembers = data.circle?.memberUids.orEmpty()
        if (circleMembers.size > 1 && circleMembers.size != lastSyncedMemberCount) {
            lastSyncedMemberCount = circleMembers.size
            viewModelScope.launch {
                repository.syncSessionMemberUids(data.sessions, circleMembers)
            }
        }

        val partnerReader = AuthorizedReaders.partnerOf(activeReader.readerId)
        val myProfile = if (activeReader.readerId == AuthorizedReaders.NARSINH.readerId) {
            data.narsinhProfile
        } else {
            data.vishwarajProfile
        }
        val partnerProfile = if (partnerReader.readerId == AuthorizedReaders.NARSINH.readerId) {
            data.narsinhProfile
        } else {
            data.vishwarajProfile
        }

        val myStats = StreakCalculator.computeStats(activeReader, myProfile)
        val partnerStats = StreakCalculator.computeStats(partnerReader, partnerProfile)
        val todayKey = myStats.todayDateKey

        val myTodaySessions = data.sessions.filter {
            it.readerId == activeReader.readerId && it.dateKey == todayKey
        }
        val partnerTodaySessions = data.sessions.filter {
            it.readerId == partnerReader.readerId && it.dateKey == todayKey
        }

        val reminderEnabled = account?.reminderEnabled ?: true
        val reminderHour = account?.reminderHour ?: 20
        val reminderMinute = account?.reminderMinute ?: 0

        // Sync local cache for offline daily reminder receiver
        viewModelScope.launch {
            localPrefs.syncReminderSnapshot(
                readerName = activeReader.shortName,
                todayDateKey = todayKey,
                todayMinutes = myStats.todayMinutes,
                currentStreak = myStats.currentStreak,
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour,
                reminderMinute = reminderMinute
            )
        }
        onScheduleReminder(reminderEnabled, reminderHour, reminderMinute)

        ReadingUiState.Ready(
            activeReader = activeReader,
            partnerReader = partnerReader,
            myStats = myStats,
            partnerStats = partnerStats,
            myProfile = myProfile,
            partnerProfile = partnerProfile,
            circle = data.circle,
            allSessions = data.sessions,
            myTodaySessions = myTodaySessions,
            partnerTodaySessions = partnerTodaySessions,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            isSavingSession = savingSession
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ReadingUiState.Loading
    )

    // --- MOBILE NUMBER VERIFICATION (STRICT 2-ID GATE) ---

    fun verifyAndLoginWithMobile(rawNumber: String) {
        val matched = AuthorizedReaders.findByMobileNumber(rawNumber)
        if (matched == null) {
            _mobileInputError.value =
                "Unauthorized number. Only 7203070830 (Narsinh Chad) and 7096295484 (Vishwaraj Jadeja) are allowed."
            return
        }

        viewModelScope.launch {
            _mobileInputError.value = null
            _isSubmittingMobile.value = true
            val currentReady = uiState.value as? ReadingUiState.Ready
            val res = repository.saveUserAccount(
                readerId = matched.readerId,
                reminderEnabled = currentReady?.reminderEnabled ?: true,
                reminderHour = currentReady?.reminderHour ?: 20,
                reminderMinute = currentReady?.reminderMinute ?: 0
            )
            _isSubmittingMobile.value = false
            res.fold(
                onSuccess = {
                    _switchingReader.value = false
                    _snackbarMessage.value = "Welcome back, ${matched.displayName}!"
                },
                onFailure = { e ->
                    _mobileInputError.value = e.localizedMessage ?: "Failed to save reader identity."
                }
            )
        }
    }

    fun clearMobileError() {
        _mobileInputError.value = null
    }

    fun openSwitchMobileId() {
        _mobileInputError.value = null
        _switchingReader.value = true
    }

    fun cancelSwitchMobileId() {
        _switchingReader.value = false
    }

    // --- LOG & DELETE READING SESSIONS ---

    fun logReadingSession(minutes: Int, bookTitle: String?, note: String?) {
        val state = uiState.value as? ReadingUiState.Ready ?: return
        if (minutes !in 1..720) {
            _snackbarMessage.value = "Enter between 1 and 720 minutes."
            return
        }
        viewModelScope.launch {
            _isSavingSession.value = true
            val wasGoalMet = state.myStats.goalMetToday
            val result = repository.logReadingSession(
                reader = state.activeReader,
                minutes = minutes,
                bookTitle = bookTitle,
                note = note,
                circleMemberUids = state.circle?.memberUids.orEmpty(),
                existingProfile = state.myProfile
            )
            _isSavingSession.value = false
            result.fold(
                onSuccess = { updatedStats ->
                    _snackbarMessage.value = when {
                        !wasGoalMet && updatedStats.goalMetToday ->
                            "Daily 30m goal crushed! Streak is now ${updatedStats.currentStreak} day${if (updatedStats.currentStreak == 1L) "" else "s"}."
                        else ->
                            "+${minutes} min added • Today's total: ${updatedStats.todayMinutes} min"
                    }
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Failed to save reading session."
                }
            )
        }
    }

    fun deleteSession(session: ReadingSession) {
        val state = uiState.value as? ReadingUiState.Ready ?: return
        val reader = AuthorizedReaders.findByReaderId(session.readerId) ?: return
        val profile = if (reader.readerId == state.activeReader.readerId) {
            state.myProfile
        } else {
            state.partnerProfile
        }
        viewModelScope.launch {
            val res = repository.deleteReadingSession(
                session = session,
                reader = reader,
                existingProfile = profile
            )
            res.fold(
                onSuccess = {
                    _snackbarMessage.value = "Removed ${session.minutes}m session."
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Could not delete session."
                }
            )
        }
    }

    // --- REMINDER SETTINGS ---

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            val res = repository.updateReminderPreferences(enabled, hour, minute)
            res.fold(
                onSuccess = {
                    onScheduleReminder(enabled, hour, minute)
                    val formatted = StreakCalculator.formatClockTime(hour, minute)
                    _snackbarMessage.value = if (enabled) {
                        "Daily reminder set for $formatted if under 30 min."
                    } else {
                        "Daily reminders paused."
                    }
                },
                onFailure = { e ->
                    _snackbarMessage.value = e.localizedMessage ?: "Could not update reminder settings."
                }
            )
        }
    }

    // --- LIVE READING STOPWATCH ---

    fun toggleTimer() {
        if (_timerState.value.running) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _timerState.update { it.copy(running = true) }
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _timerState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerState.update { it.copy(running = false) }
    }

    fun resetTimer() {
        pauseTimer()
        _timerState.value = TimerState()
    }

    fun finishAndLogTimer(bookTitle: String?) {
        val elapsed = _timerState.value.elapsedSeconds
        pauseTimer()
        val minutes = ((elapsed + 59L) / 60L).coerceAtLeast(1L).toInt()
        _timerState.value = TimerState()
        logReadingSession(
            minutes = minutes,
            bookTitle = bookTitle,
            note = "Timed reading session"
        )
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private object InitializingSentinel : Exception()

    private companion object {
        const val TAG = "ReadingVM"
    }
}
