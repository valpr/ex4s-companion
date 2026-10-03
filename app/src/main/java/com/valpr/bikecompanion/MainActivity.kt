package com.valpr.bikecompanion

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.valpr.bikecompanion.data.BleConnectionState
import com.valpr.bikecompanion.ui.theme.BikeCompanionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val bikeApp: BikeApplication
        get() = application as BikeApplication

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private var autoEnterPipAllowed: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "BLE permissions granted. Searching for bike...", Toast.LENGTH_SHORT).show()
            startAutoScan()
        } else {
            Toast.makeText(
                this,
                "Bluetooth and Location permissions are required to connect to the bike",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _isInPipMode.value = isInPictureInPictureMode
        }

        checkAndRequestPermissions()

        setContent {
            val inPip by isInPipMode.collectAsState()
            BikeCompanionTheme {
                com.valpr.bikecompanion.ui.navigation.MainNavigation(
                    onRequestPermissions = { checkAndRequestPermissions() },
                    isInPipMode = inPip,
                    onEnterPip = { enterPipMode() },
                    onAutoEnterPipChanged = { updatePipParams(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read the adapter in case a state-change broadcast was missed
        // while backgrounded, so the Bluetooth-off banner is immediate.
        bikeApp.bleManager.refreshBluetoothState()
        if (hasScanPermissions()) {
            startAutoScan()
        }
    }

    private fun startAutoScan() {
        if (bikeApp.bleManager.connectionState.value is BleConnectionState.Disconnected) {
            bikeApp.bleManager.startScan()
        }
    }

    private fun hasScanPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun updatePipParams(shouldAutoEnter: Boolean) {
        autoEnterPipAllowed = shouldAutoEnter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                setPictureInPictureParams(buildPipParams(autoEnter = shouldAutoEnter))
            } catch (_: Exception) {
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(buildPipParams(autoEnter = false))
            } catch (_: Exception) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            if (!isInPictureInPictureMode && autoEnterPipAllowed) {
                try {
                    enterPictureInPictureMode(buildPipParams(autoEnter = true))
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode && autoEnterPipAllowed) {
            updatePipParams(true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
