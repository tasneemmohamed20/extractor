package com.example.extractor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.extractor.data.BuildInfo
import com.example.extractor.data.VersionInfo
import com.example.extractor.data.HardwareInfo
import com.example.extractor.data.fetchHardwareInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    var buildInfo by mutableStateOf(BuildInfo())
        private set

    var versionInfo by mutableStateOf(VersionInfo())
        private set

    var hardwareInfo by mutableStateOf(HardwareInfo(
        totalRam = "N/A",
        phoneType = "N/A",
        totalInternalStorage = "N/A",
        cpuCores = 0,
        kernelArch = "N/A",
        androidId = "N/A",
        mediaDrmId = "N/A",
        imei = "N/A",
        bluetoothAddress = "N/A",
        simOperator = "N/A",
        simOperatorName = "N/A",
        simCountryIso = "N/A",
        simSerial = "N/A",
        networkOperator = "N/A",
        networkOperatorName = "N/A",
        networkType = "N/A",
        isRoaming = "N/A",
        connectionType = "N/A",
        simCards = emptyList()
    ))
        private set

    init {
        hardwareInfo = fetchHardwareInfo(getApplication())
    }

    /**
     * Refresh device info (re-fetch hardware-specific info). Runs on viewModelScope.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                hardwareInfo = fetchHardwareInfo(getApplication())
            } catch (_: Exception) {
                // swallow any exception; hardwareInfo remains unchanged or previously set
            }
        }
    }
}

