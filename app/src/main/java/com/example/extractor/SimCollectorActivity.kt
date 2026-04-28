package com.example.extractor

import android.os.Bundle
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.extractor.ui.theme.ExtractorTheme
import com.example.extractor.ui.viewmodel.MainViewModel

class SimCollectorActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val grantedRead = results[Manifest.permission.READ_PHONE_STATE] ?: false
            val grantedSend = results[Manifest.permission.SEND_SMS] ?: false
            if (grantedRead || grantedSend) {
                vm.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application as Application)
        ).get(MainViewModel::class.java)

        // Request READ_PHONE_STATE and SEND_SMS permissions to detect SIMs and send verification SMS
        val needRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        val needSend = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
        if (needRead || needSend) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS))
        } else {
            vm.refresh()
        }

        setContent {
            ExtractorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SimCollectorScreen(
                        vm = vm,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }

    @Composable
    fun SimCollectorScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
        val hardwareInfo = vm.hardwareInfo
        val simCards = hardwareInfo.simCards

        if (simCards.isEmpty()) {
            // No SIM cards detected
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "No SIM Cards Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Display SIM cards for selection
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Select SIM Card",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Display each SIM as a clickable card
                items(simCards.size) { index ->
                    val sim = simCards[index]
                    val isSelected = vm.selectedSimIndex == index

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.selectSim(index) },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 8.dp else 4.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SIM ${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓ Selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            SimDetailRow("Operator Name", sim.operatorName)
                            SimDetailRow("Operator Code", sim.operatorCode)
                            SimDetailRow("Country ISO", sim.countryIso)
                            SimDetailRow("Display Name", sim.displayName)
                        }
                    }
                }

                // Action Button
                item {
                    val vs = vm.verificationState
                    Button(
                        onClick = { vm.applySimTask("EXECUTE") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        enabled = vm.selectedSimIndex >= 0 && !vs.isLoading
                    ) {
                        if (vs.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(text = if (vs.isLoading) "Verifying..." else "Verify Mobile Number")
                    }
                }

                // Verification status card
                item {
                    val vs = vm.verificationState
                    if (vs.status.isNotEmpty()) {
                        val (bgColor, label) = when (vs.status) {
                            "verified" -> Color(0xFFE8F5E9) to "Verified"
                            "pending"  -> Color(0xFFFFFDE7) to "Pending..."
                            "timeout"  -> Color(0xFFFFF3E0) to "Timed Out"
                            "error"    -> Color(0xFFFFEBEE) to "Error"
                            else       -> Color(0xFFF5F5F5) to vs.status
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Verification Status: $label",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (vs.status == "verified" && vs.phoneNumber.isNotEmpty()) {
                                    Text(
                                        text = "Phone: ${vs.phoneNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                if (vs.hashCode.isNotEmpty()) {
                                    Text(
                                        text = "Hash: ${vs.hashCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                if (vs.errorMessage.isNotEmpty()) {
                                    Text(
                                        text = vs.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD32F2F),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SimDetailRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}



