package com.example.extractor.ui.viewmodel

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStreamReader
import org.json.JSONObject

data class VerificationState(
    val isLoading: Boolean = false,
    val hashCode: String = "",
    val status: String = "",  // "pending", "verified", "error", "timeout"
    val phoneNumber: String = "",
    val errorMessage: String = ""
)


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

    var verificationState by mutableStateOf(VerificationState())
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

    /**
     * Apply a task on the selected SIM (e.g., send SMS, make call, etc.)
     * This can be expanded based on your actual use case.
     */
    fun applySimTask(taskType: String) {
        if (selectedSimIndex >= 0 && selectedSimIndex < hardwareInfo.simCards.size) {
            val selectedSim = hardwareInfo.simCards[selectedSimIndex]
            // Perform action based on taskType
            when (taskType) {
                "INFO" -> {
                    // Log or handle SIM info retrieval
                }
                "SMS" -> {
                    // Send SMS via selected SIM
                }
                "CALL" -> {
                    // Make call via selected SIM
                }
                "EXECUTE" -> {
                    startVerificationOnSelectedSim()
                }
                else -> {
                    // Default action
                }
            }
        }
    }

    /**
     * Start verification flow for the selected SIM:
     * 1. Call server /generate-hash to get a hash
     * 2. Send SMS from device with message `AuthRequest:<hash>` using the selected SIM subscriptionId
     * 3. Poll server /check-status/:hash until verified or timeout
     */
    fun startVerificationOnSelectedSim() {
        if (selectedSimIndex < 0 || selectedSimIndex >= hardwareInfo.simCards.size) {
            Log.w(TAG, "startVerification: no valid SIM selected (index=$selectedSimIndex)")
            return
        }

        val sim = hardwareInfo.simCards[selectedSimIndex]
        Log.d(TAG, "startVerification: SIM slot=${sim.slotIndex} operator=${sim.operatorName} subId=${sim.subscriptionId}")

        viewModelScope.launch {
            verificationState = verificationState.copy(isLoading = true, status = "pending", errorMessage = "")

            val baseUrl = SERVER_BASE_URL
            Log.d(TAG, "baseUrl=$baseUrl")

            try {
                // Step 1: fetch hash from server
                Log.d(TAG, "Step 1: GET $baseUrl/generate-hash")
                val (generateCode, generateBody) = withContext(Dispatchers.IO) {
                    val c = (URL("$baseUrl/generate-hash").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    val code = c.responseCode
                    val body = if (code == 200) InputStreamReader(c.inputStream).use { it.readText() } else ""
                    Log.d(TAG, "Step 1 response: code=$code body=$body")
                    code to body
                }

                if (generateCode != 200) {
                    Log.e(TAG, "Step 1 failed: HTTP $generateCode")
                    verificationState = verificationState.copy(isLoading = false, status = "error", errorMessage = "Server returned $generateCode")
                    return@launch
                }

                val hash = JSONObject(generateBody).optString("hashCode")
                Log.d(TAG, "Step 1 success: hash=$hash")
                verificationState = verificationState.copy(hashCode = hash)

                // Step 2: send real SMS to Twilio number — Twilio will POST to /webhook-sms
                val context = getApplication<Application>().applicationContext
                val hasSmsPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasSmsPermission) {
                    Log.e(TAG, "Step 2 failed: SEND_SMS permission not granted")
                    verificationState = verificationState.copy(isLoading = false, status = "error", errorMessage = "SEND_SMS permission missing")
                    return@launch
                }

                Log.d(TAG, "Step 2: sending SMS to $TWILIO_PHONE_NUMBER via subId=${sim.subscriptionId}  msg=AuthRequest:$hash")
                try {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                            SmsManager.getSmsManagerForSubscriptionId(sim.subscriptionId)
                        } else {
                            SmsManager.getDefault()
                        }
                        smsManager.sendTextMessage(TWILIO_PHONE_NUMBER, null, "AuthRequest:$hash", null, null)
                    }
                    Log.d(TAG, "Step 2 success: SMS dispatched, waiting for Twilio webhook")
                } catch (e: Exception) {
                    Log.e(TAG, "Step 2 failed: ${e.javaClass.simpleName}: ${e.message}", e)
                    verificationState = verificationState.copy(isLoading = false, status = "error", errorMessage = "Failed to send SMS: ${e.message}")
                    return@launch
                }

                // Step 3: poll /check-status until verified or timeout
                val checkUrl = "$baseUrl/check-status/$hash"
                var verified = false
                repeat(20) { attempt ->
                    if (verified) return@repeat
                    delay(3000)
                    Log.d(TAG, "Step 3: poll attempt ${attempt + 1}/20  GET $checkUrl")
                    try {
                        val (statusCode, statusBody) = withContext(Dispatchers.IO) {
                            val c = (URL(checkUrl).openConnection() as HttpURLConnection).apply {
                                requestMethod = "GET"
                                connectTimeout = 5000
                                readTimeout = 5000
                            }
                            val code = c.responseCode
                            val body = if (code == 200) InputStreamReader(c.inputStream).use { it.readText() } else ""
                            Log.d(TAG, "Step 3 poll response: code=$code body=$body")
                            code to body
                        }
                        if (statusCode == 200 && statusBody.isNotEmpty()) {
                            val j = JSONObject(statusBody)
                            val status = j.optString("status")
                            val phone = j.optString("phoneNumber")
                            Log.d(TAG, "Step 3: status=$status phone=$phone")
                            if (status == "verified") {
                                verified = true
                                verificationState = verificationState.copy(isLoading = false, status = "verified", phoneNumber = phone)
                            } else {
                                verificationState = verificationState.copy(status = status)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Step 3 poll error (attempt ${attempt + 1}): ${e.javaClass.simpleName}: ${e.message}")
                    }
                }

                if (!verified) {
                    Log.w(TAG, "Step 3: timed out after 20 attempts")
                    verificationState = verificationState.copy(isLoading = false, status = "timeout", errorMessage = "Verification timed out")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed: ${e.javaClass.simpleName}: ${e.message}", e)
                verificationState = verificationState.copy(isLoading = false, status = "error", errorMessage = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    companion object {
        private const val TAG = "SimVerification"

        /** Paste your Cloudflare tunnel URL here — run: cloudflared tunnel --url http://localhost:3000 */
        private const val SERVER_BASE_URL = "https://your-tunnel.trycloudflare.com"

        /** Your Twilio phone number in E.164 format, e.g. "+12015551234" */
        private const val TWILIO_PHONE_NUMBER = "+1XXXXXXXXXX"
    }
}


