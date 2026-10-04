package com.valpr.bikecompanion.companion.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Arbitrates incoming heart rate data from multiple potential sources
 * (e.g. Wear OS watch, BLE HR chest strap).
 *
 * Responsibilities:
 * 1. Priority selection among available sources.
 * 2. Freshness watchdog: drops to 0 and invokes [onClearHeartRate] when the
 *    active source data is older than [staleThresholdMs] (12s).
 * 3. Disconnect cleanup: instantly releases session heart rate when active source disconnects.
 */
class HeartRateArbiter(
    val sources: List<HeartRateSource> = emptyList(),
    private val onUpdateHeartRate: (Int) -> Unit = {},
    private val onClearHeartRate: () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    private val staleThresholdMs: Long = HR_STALE_THRESHOLD_MS
) {
    companion object {
        const val HR_STALE_THRESHOLD_MS = 12_000L
        const val WATCHDOG_INTERVAL_MS = 1_000L
    }

    private val _activeSource = MutableStateFlow<HeartRateSource?>(null)
    val activeSource: StateFlow<HeartRateSource?> = _activeSource.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _hrStatus = MutableStateFlow(HrStatus.DISCONNECTED)
    val hrStatus: StateFlow<HrStatus> = _hrStatus.asStateFlow()

    private var watchdogJob: Job? = null

    init {
        for (source in sources) {
            scope.launch {
                source.hrSample.collect { sample ->
                    onSampleReceived(source, sample)
                }
            }
            scope.launch {
                source.hrStatus.collect { status ->
                    if (status == HrStatus.DISCONNECTED && _activeSource.value == source) {
                        watchdogJob?.cancel()
                    }
                    evaluateActiveSource()
                }
            }
        }
    }

    private fun onSampleReceived(source: HeartRateSource, sample: HrSample?) {
        if (sample == null || sample.bpm <= 0) return

        val currentActive = _activeSource.value
        if (currentActive == null || currentActive == source || !isSourceFresh(currentActive)) {
            _activeSource.value = source
            _currentBpm.value = sample.bpm
            _hrStatus.value = HrStatus.CONNECTED
            onUpdateHeartRate(sample.bpm)
            resetWatchdog()
        }
    }

    private fun isSourceFresh(source: HeartRateSource): Boolean {
        val sample = source.hrSample.value ?: return false
        return (clock() - sample.timestampEpochMs) < staleThresholdMs
    }

    fun evaluateActiveSource() {
        val now = clock()
        val active = _activeSource.value
        if (active != null) {
            val sample = active.hrSample.value
            val isStale = sample == null || (now - sample.timestampEpochMs) >= staleThresholdMs
            val isDisconnected = active.hrStatus.value == HrStatus.DISCONNECTED

            if (isStale || isDisconnected) {
                // Try fallback to another fresh source
                val replacement = sources.firstOrNull { it != active && isSourceFresh(it) }
                if (replacement != null) {
                    _activeSource.value = replacement
                    val repSample = replacement.hrSample.value!!
                    _currentBpm.value = repSample.bpm
                    _hrStatus.value = HrStatus.CONNECTED
                    onUpdateHeartRate(repSample.bpm)
                    resetWatchdog()
                    return
                }

                // No fresh source
                val wasLive = _currentBpm.value > 0 || _hrStatus.value == HrStatus.CONNECTED
                _currentBpm.value = 0
                _hrStatus.value = if (isDisconnected) HrStatus.DISCONNECTED else HrStatus.STALE
                if (wasLive) {
                    onClearHeartRate()
                }
            }
        } else {
            val fresh = sources.firstOrNull { isSourceFresh(it) }
            if (fresh != null) {
                val sample = fresh.hrSample.value!!
                _activeSource.value = fresh
                _currentBpm.value = sample.bpm
                _hrStatus.value = HrStatus.CONNECTED
                onUpdateHeartRate(sample.bpm)
                resetWatchdog()
            } else {
                val anyConnecting = sources.any { it.hrStatus.value == HrStatus.CONNECTING }
                _hrStatus.value = if (anyConnecting) HrStatus.CONNECTING else HrStatus.DISCONNECTED
            }
        }
    }

    private fun resetWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(staleThresholdMs)
            evaluateActiveSource()
        }
    }

    fun onDestroy() {
        watchdogJob?.cancel()
    }
}
