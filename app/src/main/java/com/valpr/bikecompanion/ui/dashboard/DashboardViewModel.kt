package com.valpr.bikecompanion.ui.dashboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.data.UserProfile
import com.valpr.bikecompanion.workout.CachedWorkoutHeader
import com.valpr.bikecompanion.workout.TagCount
import com.valpr.bikecompanion.workout.Workout
import com.valpr.bikecompanion.workout.WorkoutDurationBracket
import com.valpr.bikecompanion.workout.WorkoutFilterSortHelper
import com.valpr.bikecompanion.workout.WorkoutImportUseCase
import com.valpr.bikecompanion.workout.WorkoutSessionState
import com.valpr.bikecompanion.workout.WorkoutSortOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

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

    private val favoriteFilenamesFlow = userProfileRepo.userProfileFlow
        .map { it.favoriteWorkoutFilenames }
        .distinctUntilChanged()

    private val _cachedWorkouts = MutableStateFlow<List<CachedWorkoutHeader>>(emptyList())
    val cachedWorkouts: StateFlow<List<CachedWorkoutHeader>> = _cachedWorkouts.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = _selectedTagFilter.asStateFlow()

    private val _selectedSortOption = MutableStateFlow(WorkoutSortOption.RECENTLY_MODIFIED)
    val selectedSortOption: StateFlow<WorkoutSortOption> = _selectedSortOption.asStateFlow()

    private val _selectedDurationBracket = MutableStateFlow(WorkoutDurationBracket.ALL)
    val selectedDurationBracket: StateFlow<WorkoutDurationBracket> = _selectedDurationBracket.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _historyHeaders =
        MutableStateFlow<List<com.valpr.bikecompanion.history.RideHeader>>(emptyList())
    val historyHeaders: StateFlow<List<com.valpr.bikecompanion.history.RideHeader>> =
        _historyHeaders.asStateFlow()

    private val _completedFilenames = MutableStateFlow<Set<String>>(emptySet())
    val completedFilenames: StateFlow<Set<String>> = _completedFilenames.asStateFlow()

    val completionCountMap: StateFlow<Map<String, Int>> = _historyHeaders.map { headers ->
        val map = mutableMapOf<String, Int>()
        for (h in headers) {
            val file = h.sourceWorkoutFilename?.lowercase() ?: continue
            map[file] = (map[file] ?: 0) + 1
        }
        map
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    val displayedWorkouts: StateFlow<List<CachedWorkoutHeader>> = combine(
        _cachedWorkouts,
        _selectedTagFilter,
        _selectedDurationBracket,
        _searchQuery,
        _selectedSortOption
    ) { workouts, tagFilter, duration, query, sortOption ->
        FilterParams(workouts, tagFilter, duration, query, sortOption)
    }.combine(favoriteFilenamesFlow) { params, favs ->
        Pair(params, favs)
    }.combine(_historyHeaders) { (params, favs), headers ->
        val recentMap = mutableMapOf<String, Long>()
        for (h in headers) {
            val file = h.sourceWorkoutFilename?.lowercase() ?: continue
            val current = recentMap[file] ?: 0L
            if (h.startTimeEpochMs > current) {
                recentMap[file] = h.startTimeEpochMs
            }
        }
        WorkoutFilterSortHelper.filterAndSort(
            workouts = params.workouts,
            tagFilter = params.tagFilter,
            durationBracket = params.durationBracket,
            searchQuery = params.searchQuery,
            sortOption = params.sortOption,
            favoriteFilenames = favs,
            recentRidesMap = recentMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val availableTagCounts: StateFlow<List<TagCount>> = _cachedWorkouts.map { workouts ->
        WorkoutFilterSortHelper.extractTagCounts(workouts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
        viewModelScope.launch(ioDispatcher) {
            val list = workoutRepository.getCachedWorkouts()
            _cachedWorkouts.value = list
        }
    }

    fun loadHistory() {
        viewModelScope.launch(ioDispatcher) {
            val headers = historyRepository.listHeaders()
            _historyHeaders.value = headers
            _completedFilenames.value = headers.mapNotNull {
                it.sourceWorkoutFilename?.lowercase()
            }.toSet()
        }
    }

    fun selectWorkoutForPreview(filename: String) {
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
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
        viewModelScope.launch(ioDispatcher) {
            workoutRepository.deleteWorkout(filename)
            userProfileRepo.removeFavoriteWorkout(filename)
            loadWorkouts()
            if (_selectedWorkoutFilename.value == filename) {
                _selectedWorkoutPreview.value = null
                _selectedWorkoutFilename.value = null
            }
        }
    }

    fun updateFtp(ftp: Int) {
        viewModelScope.launch(ioDispatcher) {
            userProfileRepo.updateFtp(ftp)
        }
    }

    fun setBeginnerPathDismissed(dismissed: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userProfileRepo.updateBeginnerPathDismissed(dismissed)
        }
    }

    fun setBeginnerPathCollapsed(collapsed: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userProfileRepo.updateBeginnerPathCollapsed(collapsed)
        }
    }

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = tag
    }

    fun setSortOption(option: WorkoutSortOption) {
        _selectedSortOption.value = option
    }

    fun setDurationBracket(bracket: WorkoutDurationBracket) {
        _selectedDurationBracket.value = bracket
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoriteWorkout(filename: String) {
        viewModelScope.launch(ioDispatcher) {
            userProfileRepo.toggleFavoriteWorkout(filename)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private data class FilterParams(
        val workouts: List<CachedWorkoutHeader>,
        val tagFilter: String?,
        val durationBracket: WorkoutDurationBracket,
        val searchQuery: String,
        val sortOption: WorkoutSortOption
    )

    companion object {
        /** Pure SAF filename resolution (JVM-testable): DISPLAY_NAME wins, .zwo enforced. */
        fun resolveImportFilename(displayName: String?, fallbackSegment: String?): Result<String> = WorkoutImportUseCase.resolveImportFilename(displayName, fallbackSegment)
    }
}
