package com.valpr.bikecompanion.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valpr.bikecompanion.BikeApplication
import com.valpr.bikecompanion.history.CompletedRide
import com.valpr.bikecompanion.history.HistoryStats
import com.valpr.bikecompanion.history.RideHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RideHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BikeApplication
    private fun historyRepository() = app.workoutHistoryRepository

    private val _headers = MutableStateFlow<List<RideHeader>>(emptyList())
    val headers: StateFlow<List<RideHeader>> = _headers.asStateFlow()

    private val _selectedRide = MutableStateFlow<CompletedRide?>(null)
    val selectedRide: StateFlow<CompletedRide?> = _selectedRide.asStateFlow()

    private val _isLoadingRide = MutableStateFlow(false)
    val isLoadingRide: StateFlow<Boolean> = _isLoadingRide.asStateFlow()

    private val _pendingDelete = MutableStateFlow<RideHeader?>(null)
    val pendingDelete: StateFlow<RideHeader?> = _pendingDelete.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _headers.value = historyRepository().listHeaders()
        }
    }

    fun selectRide(id: String) {
        // Clear first so the detail screen shows loading, never a stale ride.
        _selectedRide.value = null
        _isLoadingRide.value = true
        viewModelScope.launch(Dispatchers.IO) {
            _selectedRide.value = historyRepository().loadRide(id)
            _isLoadingRide.value = false
        }
    }

    fun clearSelection() {
        _selectedRide.value = null
    }

    fun requestDelete(header: RideHeader) {
        _pendingDelete.value = header
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val header = _pendingDelete.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            historyRepository().delete(header.id)
            _headers.value = historyRepository().listHeaders()
            if (_selectedRide.value?.id == header.id) {
                _selectedRide.value = null
            }
        }
        _pendingDelete.value = null
    }

    fun shareRide(ride: CompletedRide) {
        try {
            com.valpr.bikecompanion.history.TcxShareHelper.shareRide(app.applicationContext, ride)
        } catch (_: Exception) {
            // Share sheet unavailable; detail screen stays usable.
        }
    }

    companion object {
        /** Pure date-line formatter for list rows (JVM-testable). */
        fun formatRideDate(startTimeEpochMs: Long): String {
            if (startTimeEpochMs <= 0L) {
                return "Unknown date"
            }
            val instant = java.time.Instant.ofEpochMilli(startTimeEpochMs)
            val zoned = instant.atZone(java.time.ZoneId.systemDefault())
            return zoned.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm"))
        }

        fun formatDuration(totalSeconds: Int): String = HistoryStats.formatDuration(
            totalSeconds.coerceAtLeast(0)
        )
    }
}
