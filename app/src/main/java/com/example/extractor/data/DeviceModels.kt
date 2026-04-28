package com.example.extractor.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.bluetooth.BluetoothManager
import android.media.MediaDrm
import android.provider.Settings
import android.telephony.TelephonyManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import java.util.UUID

data class SimCardInfo(
    val slotIndex: Int,          // Slot index (0, 1, etc.)
    val operatorName: String,    // e.g., "Vodafone", "Orange"
    val operatorCode: String,    // MNC+MCC code
    val countryIso: String,      // Country code e.g., "US"
    val displayName: String,     // Display name from carrier
    val subscriptionId: Int = -1 // Subscription ID
)

data class BuildInfo(
    val board: String = Build.BOARD,
    val brand: String = Build.BRAND,
    val device: String = Build.DEVICE,
    val display: String = Build.DISPLAY,
    val fingerprint: String = Build.FINGERPRINT,
    val hardware: String = Build.HARDWARE,
    val host: String = Build.HOST,
    val id: String = Build.ID,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val product: String = Build.PRODUCT,
    val tags: String = Build.TAGS,
    val time: Long = Build.TIME,
    val type: String = Build.TYPE,
    val user: String = Build.USER,
    val bootloader: String = Build.BOOTLOADER,
    val supportedAbis: String = Build.SUPPORTED_ABIS.joinToString(", "),
    val supported32BitAbis: String = Build.SUPPORTED_32_BIT_ABIS.joinToString(", "),
    val supported64BitAbis: String = Build.SUPPORTED_64_BIT_ABIS.joinToString(", "),
    val radioVersion: String = Build.getRadioVersion() ?: "N/A",
    val skuDevice: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SKU else "N/A (API 31+)",
    val odmSku: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.ODM_SKU else "N/A (API 31+)",
    val socManufacturer: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else "N/A (API 31+)",
    val socModel: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "N/A (API 31+)",
)

data class VersionInfo(
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val release: String = Build.VERSION.RELEASE,
    val codename: String = Build.VERSION.CODENAME,
    val incremental: String = Build.VERSION.INCREMENTAL,
    val baseOs: String = Build.VERSION.BASE_OS.ifEmpty { "N/A" },
    val securityPatch: String = Build.VERSION.SECURITY_PATCH,
    val previewSdkInt: Int = Build.VERSION.PREVIEW_SDK_INT,
    val releaseOrCodename: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Build.VERSION.RELEASE_OR_CODENAME else "N/A (API 30+)",
    val mediaPerformanceClass: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else -1,
    val releaseOrPreviewDisplay: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else "N/A (API 33+)",
)

data class HardwareInfo(
    val totalRam: String,
    val phoneType: String,
    val totalInternalStorage: String,
    val cpuCores: Int,
    val kernelArch: String,
    val androidId: String,
    val mediaDrmId: String,
    val imei: String,
    val bluetoothAddress: String,
    // SIM / network related
    val simOperator: String,
    val simOperatorName: String,
    val simCountryIso: String,
    val simSerial: String,
    val networkOperator: String,
    val networkOperatorName: String,
    val networkType: String,
    val isRoaming: String,
    val connectionType: String,
    // Multi-SIM support
    val simCards: List<SimCardInfo> = emptyList(),
)

/**
 * Fetch hardware-related properties (kept separated from UI). Renamed from previous HardwareInfo(context)
 */
fun fetchHardwareInfo(context: Context): HardwareInfo {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val stat = StatFs(Environment.getDataDirectory().path)
    val totalStorage = stat.blockCountLong * stat.blockSizeLong

    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val phoneType = when (telephonyManager.phoneType) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "None"
        else -> "Unknown"
    }

    val imei = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephonyManager.imei ?: "N/A"
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.deviceId ?: "N/A"
        }
    } catch (_: SecurityException) {
        "Restricted (API 29+)"
    }

    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "N/A"
    val mediaDrmId = getWidevineDeviceId()

    val bluetoothAddress = try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        adapter?.address ?: "N/A (no adapter)"
    } catch (_: SecurityException) {
        "Restricted (API 31+)"
    }

    // SIM / network info (best-effort; may be restricted on newer Android versions)
    val simOperator = try {
        telephonyManager.simOperator ?: "N/A"
    } catch (_: SecurityException) {
        "Restricted"
    }

    val simOperatorName = try {
        // getSimOperatorName() maps to simOperatorName property
        telephonyManager.simOperatorName ?: "N/A"
    } catch (_: SecurityException) {
        "Restricted"
    }

    val simCountryIso = try {
        telephonyManager.simCountryIso ?: "N/A"
    } catch (_: SecurityException) {
        "Restricted"
    }

    val simSerial = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephonyManager.simSerialNumber ?: "N/A"
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.simSerialNumber ?: "N/A"
        }
    } catch (_: SecurityException) {
        "Restricted"
    }

    val networkOperator = try {
        telephonyManager.networkOperator ?: "N/A"
    } catch (_: SecurityException) {
        "Restricted"
    }

    val networkOperatorName = try {
        telephonyManager.networkOperatorName ?: "N/A"
    } catch (_: SecurityException) {
        "Restricted"
    }

    val networkType = try {
        telephonyManager.dataNetworkType.toString()
    } catch (_: SecurityException) {
        "Restricted"
    }

    val isRoaming = try {
        telephonyManager.isNetworkRoaming.toString()
    } catch (_: SecurityException) {
        "Restricted"
    }

    val connectionType = try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            "None"
        } else {
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (caps == null) {
                "Unknown"
            } else {
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    else -> "Other"
                }
            }
        }
    } catch (_: Exception) {
        "N/A"
    }

    // Fetch multi-SIM card info using SubscriptionManager
    val simCards = try {
        val subscriptionManager = context.getSystemService("telephony_subscription_service") as? SubscriptionManager
        try {
            val activeSubscriptionInfoList = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            @Suppress("DEPRECATION")
            activeSubscriptionInfoList.mapIndexed { index, info ->
                val carrierName = info.carrierName?.toString().orEmpty()
                val displayName = info.displayName?.toString().orEmpty()
                val resolvedOperatorName = carrierName.ifBlank { displayName.ifBlank { "N/A" } }
                val resolvedDisplayName = displayName.ifBlank { carrierName.ifBlank { "Unknown Carrier" } }
                SimCardInfo(
                    slotIndex = index,
                    operatorName = resolvedOperatorName,
                    operatorCode = info.mcc.toString() + info.mnc.toString(),
                    countryIso = info.countryIso?.uppercase() ?: "N/A",
                    displayName = resolvedDisplayName,
                    subscriptionId = info.subscriptionId
                )
            }
        } catch (_: SecurityException) {
            // Permission denied; fallback to single SIM from TelephonyManager
            if (simOperatorName != "Restricted" && simOperatorName.isNotEmpty()) {
                listOf(
                    SimCardInfo(
                        slotIndex = 0,
                        operatorName = simOperatorName,
                        operatorCode = simOperator,
                        countryIso = simCountryIso,
                        displayName = simOperatorName
                    )
                )
            } else {
                emptyList()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    // If still no SIMs detected, try creating one from TelephonyManager data
    val finalSimCards = if (simCards.isEmpty() && simOperatorName != "Restricted" && simOperatorName != "N/A") {
        listOf(SimCardInfo(
            slotIndex = 0,
            operatorName = simOperatorName,
            operatorCode = simOperator,
            countryIso = simCountryIso,
            displayName = simOperatorName
        ))
    } else {
        simCards
    }

    return HardwareInfo(
        totalRam = formatBytes(memInfo.totalMem),
        phoneType = phoneType,
        totalInternalStorage = formatBytes(totalStorage),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        kernelArch = System.getProperty("os.arch") ?: "N/A",
        androidId = androidId,
        mediaDrmId = mediaDrmId,
        imei = imei,
        bluetoothAddress = bluetoothAddress,
        simOperator = simOperator,
        simOperatorName = simOperatorName,
        simCountryIso = simCountryIso,
        simSerial = simSerial,
        networkOperator = networkOperator,
        networkOperatorName = networkOperatorName,
        networkType = networkType,
        isRoaming = isRoaming,
        connectionType = connectionType,
        simCards = finalSimCards,
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) "%.2f GB".format(gb)
    else "%.0f MB".format(bytes / (1024.0 * 1024.0))

}

private fun getWidevineDeviceId(): String {
    return try {
        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        val mediaDrm = MediaDrm(widevineUuid)
        try {
            val deviceId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            deviceId.joinToString(separator = "") { byte -> "%02X".format(byte) }.ifEmpty { "N/A" }
        } finally {
            mediaDrm.release()
        }
    } catch (_: Exception) {
        "N/A"
    }
}


