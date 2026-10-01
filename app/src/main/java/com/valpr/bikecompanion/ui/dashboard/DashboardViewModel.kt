package com.valpr.bikecompanion.ui.dashboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutImportUseCase
import com.valpr.bikecompanion.workout.WorkoutSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeApplication
    val bleManager = app.bleManager
    val workoutRepository = app.workoutRepository
    val sessionManager = app.workoutSessionManager
    val sessionState: StateFlow<WorkoutSessionState> = sessionManager.sessionState
    val historyRepository = app.workoutHistoryRepository
    val phoneWearableManager = app.phoneWearableManager
    val watchState = phoneWearableManager.watchState
    val userProfileRepo = app.userProfileRepository

    val userProfile: StateFlow<UserProfile> = userProfileRepo.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    private val _cachedWorkouts = MutableStateFlow<List<CachedWorkoutHeader>>(emptyList())
    val cachedWorkouts: StateFlow<List<CachedWorkoutHeader>> = _cachedWorkouts.asStateFlow()

    private val _historyHeaders =
        MutableStateFlow<List<com.valpr.bikecompanion.history.RideHeader>>(emptyList())
    val historyHeaders: StateFlow<List<com.valpr.bikecompanion.history.RideHeader>> =
        _historyHeaders.asStateFlow()

    private val _completedFilenames = MutableStateFlow<Set<String>>(emptySet())
    val completedFilenames: StateFlow<Set<String>> = _completedFilenames.asStateFlow()

    private val _selectedWorkoutPreview = MutableStateFlow<Workout?>(null)
    val selectedWorkoutPreview: StateFlow<Workout?> = _selectedWorkoutPreview.asStateFlow()

    private val _selectedWorkoutFilename = MutableStateFlow<String?>(null)
    val selectedWorkoutFilename: StateFlow<String?> = _selectedWorkoutFilename.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val importUseCase = WorkoutImportUseCase(workoutRepository)

    init {
        loadWorkouts()
        loadHistory()
        refreshWatchConnection()
    }

    fun refreshWatchConnection() {
        phoneWearableManager.refreshConnectedNodes()
    }

    fun testWatchConnection() {
        phoneWearableManager.sendPing()
    }

    fun loadWorkouts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = workoutRepository.getCachedWorkouts()
            _cachedWorkouts.value = list
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val headers = historyRepository.listHeaders()
            _historyHeaders.value = headers
            _completedFilenames.value = headers.mapNotNull {
                it.sourceWorkoutFilename?.lowercase()
            }.toSet()
        }
    }

    fun selectWorkoutForPreview(filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = workoutRepository.loadWorkout(filename)
            if (result.isSuccess) {
                _selectedWorkoutPreview.value = result.getOrNull()
                _selectedWorkoutFilename.value = filename
            } else {
                _selectedWorkoutPreview.value = null
                _selectedWorkoutFilename.value = null
                _errorMessage.value = "Failed to load workout: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearWorkoutPreview() {
        _selectedWorkoutPreview.value = null
        _selectedWorkoutFilename.value = null
    }

    fun importWorkoutFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = app.contentResolver
                // Resolve the user-visible display name (SAF lastPathSegment is opaque
                // for Drive/odd providers); null falls back to lastPathSegment inside the use case.
                val displayName = WorkoutImportUseCase.queryDisplayName(contentResolver, uri)
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { /* transient providers may not support it */ }
                val result = importUseCase.import(
                    displayName,
                    uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ) { contentResolver.openInputStream(uri) }
                if (result.isSuccess) {
                    loadWorkouts()
                    _selectedWorkoutPreview.value = result.getOrNull()?.workout
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error reading file: ${e.message}"
            }
        }
    }

    fun deleteWorkout(filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workoutRepository.deleteWorkout(filename)
            loadWorkouts()
            if (_selectedWorkoutFilename.value == filename) {
                _selectedWorkoutPreview.value = null
                _selectedWorkoutFilename.value = null
            }
        }
    }

    fun updateFtp(ftp: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepo.updateFtp(ftp)
        }
    }

    fun setBeginnerPathDismissed(dismissed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepo.updateBeginnerPathDismissed(dismissed)
        }
    }

    fun setBeginnerPathCollapsed(collapsed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepo.updateBeginnerPathCollapsed(collapsed)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        /** Pure SAF filename resolution (JVM-testable): DISPLAY_NAME wins, .zwo enforced. */
        fun resolveImportFilename(displayName: String?, fallbackSegment: String?): Result<String> = WorkoutImportUseCase.resolveImportFilename(displayName, fallbackSegment)
    }
}
