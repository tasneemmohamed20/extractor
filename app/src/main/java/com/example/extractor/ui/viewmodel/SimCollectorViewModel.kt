package com.example.extractor.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.extractor.data.SimCardInfo
import com.example.extractor.data.fetchHardwareInfo
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

class SimCollectorViewModel(application: Application) : AndroidViewModel(application) {
    var simCards by mutableStateOf<List<SimCardInfo>>(emptyList())
        private set

    var selectedSimIndex by mutableStateOf(-1)
        private set

    var ussdVerificationState by mutableStateOf(UssdVerificationState())
        private set

    init {
        simCards = fetchHardwareInfo(getApplication()).simCards
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                simCards = fetchHardwareInfo(getApplication()).simCards
            } catch (_: Exception) {
                // keep last known state if refresh fails
            }
        }
    }

    fun selectSim(index: Int) {
        if (index in 0 until simCards.size) {
            selectedSimIndex = index
        }
    }

    fun verifySelectedSimNumber() {
        val sim = getSelectedSimOrSetError() ?: return
        val operator = EgyptianMobileOperator.fromOperatorName(sim.operatorName)
            ?: EgyptianMobileOperator.fromOperatorName(sim.displayName)

        if (operator == null) {
            ussdVerificationState = UssdVerificationState(
                isLoading = false,
                statusMessage = "Unsupported operator for SIM ${sim.slotIndex + 1}: ${sim.operatorName}"
            )
            return
        }

        runUssd(sim.slotIndex, operator.shortCode)
    }

    private fun getSelectedSimOrSetError() = simCards.getOrNull(selectedSimIndex).also {
        if (it == null) {
            ussdVerificationState = UssdVerificationState(
                isLoading = false,
                statusMessage = "Select a valid SIM first."
            )
        }
    }

    private fun runUssd(simSlotIndex: Int, ussdCode: String) {
        val context = getApplication<Application>().applicationContext
        ussdVerificationState = UssdVerificationState(
            isLoading = true,
            statusMessage = "Running $ussdCode on SIM ${simSlotIndex + 1}..."
        )

        context.callUssdOnSimSlot(ussdCode, simSlotIndex) { result ->
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

    if (
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
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
