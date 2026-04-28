package com.example.extractor

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.extractor.ui.theme.ExtractorTheme
import com.example.extractor.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vm = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application as Application)
        ).get(MainViewModel::class.java)

        setContent {
            ExtractorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DeviceInfoScreen(
                        vm = vm, modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceInfoScreen(
    vm: MainViewModel, modifier: Modifier = Modifier
) {
    val buildInfo = vm.buildInfo
    val versionInfo = vm.versionInfo
    val hardwareInfo = vm.hardwareInfo

    val buildTime = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(buildInfo.time))
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Device Build Info",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Button",
                    modifier = Modifier.clickable { vm.refresh() }
                )
            }
        }

        item {
            InfoCard(title = "Build.VERSION") {
                InfoRow("SDK_INT", "${versionInfo.sdkInt}", changeable = true)
                InfoRow("RELEASE", versionInfo.release, changeable = true)
                InfoRow("CODENAME", versionInfo.codename, changeable = true)
                InfoRow("INCREMENTAL", versionInfo.incremental, changeable = true)
                InfoRow("BASE_OS", versionInfo.baseOs, changeable = true)
                InfoRow("SECURITY_PATCH", versionInfo.securityPatch, changeable = true)
                InfoRow("PREVIEW_SDK_INT", "${versionInfo.previewSdkInt}", changeable = true)
                InfoRow("RELEASE_OR_CODENAME", versionInfo.releaseOrCodename, changeable = true)
                InfoRow(
                    "MEDIA_PERFORMANCE_CLASS",
                    if (versionInfo.mediaPerformanceClass == -1) "N/A (API 31+)"
                    else "${versionInfo.mediaPerformanceClass}",
                    changeable = false
                )
                InfoRow(
                    "RELEASE_OR_PREVIEW_DISPLAY",
                    versionInfo.releaseOrPreviewDisplay,
                    changeable = true
                )
            }
        }

        item {
            InfoCard(title = "Build — Identity") {
                InfoRow("MANUFACTURER", buildInfo.manufacturer, changeable = false)
                InfoRow("BRAND", buildInfo.brand, changeable = false)
                InfoRow("MODEL", buildInfo.model, changeable = false)
                InfoRow("DEVICE", buildInfo.device, changeable = false)
                InfoRow("PRODUCT", buildInfo.product, changeable = false)
                InfoRow("DISPLAY", buildInfo.display, changeable = true)
                InfoRow("FINGERPRINT", buildInfo.fingerprint, changeable = true)
                InfoRow("ID", buildInfo.id, changeable = true)
            }
        }

        item {
            InfoCard(title = "Build — Hardware") {
                InfoRow("BOARD", buildInfo.board, changeable = false)
                InfoRow("HARDWARE", buildInfo.hardware, changeable = false)
                InfoRow("BOOTLOADER", buildInfo.bootloader, changeable = true)
                InfoRow("RADIO (baseband)", buildInfo.radioVersion, changeable = true)
                InfoRow("SOC_MANUFACTURER", buildInfo.socManufacturer, changeable = false)
                InfoRow("SOC_MODEL", buildInfo.socModel, changeable = false)
                InfoRow("SKU", buildInfo.skuDevice, changeable = false)
                InfoRow("ODM_SKU", buildInfo.odmSku, changeable = false)
            }
        }

        item {
            InfoCard(title = "Build — ABIs") {
                InfoRow(
                    "SUPPORTED_ABIS", buildInfo.supportedAbis.ifEmpty { "N/A" }, changeable = false
                )
                InfoRow(
                    "SUPPORTED_32_BIT_ABIS",
                    buildInfo.supported32BitAbis.ifEmpty { "N/A" },
                    changeable = false
                )
                InfoRow(
                    "SUPPORTED_64_BIT_ABIS",
                    buildInfo.supported64BitAbis.ifEmpty { "N/A" },
                    changeable = false
                )
            }
        }

        item {
            InfoCard(title = "Build — Metadata") {
                InfoRow("TYPE", buildInfo.type, changeable = true)
                InfoRow("TAGS", buildInfo.tags, changeable = true)
                InfoRow("HOST", buildInfo.host, changeable = true)
                InfoRow("USER", buildInfo.user, changeable = true)
                InfoRow("TIME", buildTime, changeable = true)
            }
        }

        item {
            InfoCard(title = "Device — Hardware") {
                InfoRow("TOTAL RAM", hardwareInfo.totalRam, changeable = false)
                InfoRow("PHONE TYPE", hardwareInfo.phoneType, changeable = false)
                InfoRow("INTERNAL STORAGE", hardwareInfo.totalInternalStorage, changeable = false)
                InfoRow("CPU CORES", "${hardwareInfo.cpuCores}", changeable = false)
                InfoRow("KERNEL ARCH", hardwareInfo.kernelArch, changeable = false)
                InfoRow("ANDROID ID", hardwareInfo.androidId, changeable = false)
                InfoRow("MEDIA DRM ID", hardwareInfo.mediaDrmId, changeable = false)
                InfoRow("IMEI", hardwareInfo.imei, changeable = false)
                InfoRow("BLUETOOTH ADDRESS", hardwareInfo.bluetoothAddress, changeable = false)
            }
        }

        item {
            InfoCard(title = "SIM & Network") {
                InfoRow("SIM OPERATOR", hardwareInfo.simOperator, changeable = false)
                InfoRow("SIM OPERATOR NAME", hardwareInfo.simOperatorName, changeable = false)
                InfoRow("SIM COUNTRY ISO", hardwareInfo.simCountryIso, changeable = false)
                InfoRow("SIM SERIAL", hardwareInfo.simSerial, changeable = false)
                InfoRow("NETWORK OPERATOR", hardwareInfo.networkOperator, changeable = false)
                InfoRow(
                    "NETWORK OPERATOR NAME", hardwareInfo.networkOperatorName, changeable = false
                )
                InfoRow("NETWORK TYPE", hardwareInfo.networkType, changeable = false)
                InfoRow("IS ROAMING", hardwareInfo.isRoaming, changeable = false)
                InfoRow("CONNECTION TYPE", hardwareInfo.connectionType, changeable = false)
            }
        }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, changeable: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.8f)
        )
        Text(
            text = if (changeable) "Changeable" else "Not Changeable",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (changeable) Color(0xFF4CAF50) else Color(0xFFFF7043),
            modifier = Modifier.weight(1.0f)
        )
    }
}
