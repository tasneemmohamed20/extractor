package com.example.extractor.ui.viewmodel

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
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

data class UssdVerificationState(
    val isLoading: Boolean = false,
    val statusMessage: String = ""
)

enum class EgyptianMobileOperator(
    val operatorName: String,
    val shortCode: String
) {
    VODAFONE("Vodafone", "*878#"),
    ORANGE("Orange", "*119#"),
    ETISALAT("Etisalat", "*947#"),
    WE("WE", "*688#");

    companion object {
        fun fromOperatorName(value: String): EgyptianMobileOperator? {
            val normalizedValue = value.trim()
            if (normalizedValue.isEmpty()) return null

            return entries.firstOrNull {
                normalizedValue.contains(it.operatorName, ignoreCase = true)
            }
        }
    }
}

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

    // Track selected SIM index
    var selectedSimIndex by mutableStateOf(-1)
        private set

    var ussdVerificationState by mutableStateOf(UssdVerificationState())
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

    /**
     * Select a SIM card by index
     */
    fun selectSim(index: Int) {
        if (index in 0 until hardwareInfo.simCards.size) {
            selectedSimIndex = index
        }
    }

    fun verifySelectedSimNumber() {
        if (selectedSimIndex < 0 || selectedSimIndex >= hardwareInfo.simCards.size) {
            ussdVerificationState = UssdVerificationState(
                isLoading = false,
                statusMessage = "Select a valid SIM first."
            )
            return
        }

        val sim = hardwareInfo.simCards[selectedSimIndex]
        val operator = EgyptianMobileOperator.fromOperatorName(sim.operatorName)
            ?: EgyptianMobileOperator.fromOperatorName(sim.displayName)

        if (operator == null) {
            ussdVerificationState = UssdVerificationState(
                isLoading = false,
                statusMessage = "Unsupported operator for SIM ${sim.slotIndex + 1}: ${sim.operatorName}"
            )
            return
        }

        val context = getApplication<Application>().applicationContext
        ussdVerificationState = UssdVerificationState(
            isLoading = true,
            statusMessage = "Running ${operator.shortCode} on SIM ${sim.slotIndex + 1}..."
        )

        context.callUssdOnSimSlot(operator.shortCode, sim.slotIndex) { result ->
            ussdVerificationState = UssdVerificationState(
                isLoading = false,
                statusMessage = result
            )
        }
    }
}

private fun Context.callUssdOnSimSlot(
    ussdCode: String,
    simSlotIndex: Int,
    onResult: (String) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onResult("USSD API requires Android 8.0 or higher.")
        return
    }

    val subscriptionManager =
        getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: run {
                onResult("Subscription manager not available")
                return
            }
    val telephonyManager =
        getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: run {
            onResult("Telephony manager not available")
            return
        }

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        onResult("Permission not granted. Please allow phone call permission.")
        return
    }

    try {
        val activeSubs = subscriptionManager.activeSubscriptionInfoList
        if (activeSubs.isNullOrEmpty()) {
            onResult("No active SIMs detected.")
            return
        }

        val simInfo = activeSubs.firstOrNull { it.simSlotIndex == simSlotIndex }
        if (simInfo == null) {
            onResult("SIM ${simSlotIndex + 1} is not active.")
            return
        }

        val ussdCallback = object : TelephonyManager.UssdResponseCallback() {
            override fun onReceiveUssdResponse(
                telephonyManager: TelephonyManager,
                request: String,
                response: CharSequence
            ) {
                onResult("USSD result (SIM ${simSlotIndex + 1}): ${response.toString()}")
            }

            override fun onReceiveUssdResponseFailed(
                telephonyManager: TelephonyManager,
                request: String,
                failureCode: Int
            ) {
                onResult("USSD failed on SIM ${simSlotIndex + 1}. Error code: $failureCode")
            }
        }

        telephonyManager
            .createForSubscriptionId(simInfo.subscriptionId)
            .sendUssdRequest(ussdCode, ussdCallback, Handler(Looper.getMainLooper()))
    } catch (e: SecurityException) {
        onResult("Permission error: ${e.message}")
    } catch (e: Exception) {
        onResult("Error: ${e.message}")
    }
}

fun Context.callUssdOnSim1(ussdCode: String, onResult: (String) -> Unit) {
    callUssdOnSimSlot(ussdCode = ussdCode, simSlotIndex = 0, onResult = onResult)
}

fun Context.callUssdOnSim2(ussdCode: String, onResult: (String) -> Unit) {
    callUssdOnSimSlot(ussdCode = ussdCode, simSlotIndex = 1, onResult = onResult)
}

