package com.example.extractor

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.extractor.ui.theme.ExtractorTheme
import com.example.extractor.ui.viewmodel.MainViewModel
import com.example.extractor.ui.viewmodel.SimCollectorViewModel

class HomeActivity : ComponentActivity() {
    private lateinit var mainVm: MainViewModel
    private lateinit var simCollectorVm: SimCollectorViewModel

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val grantedRead = results[Manifest.permission.READ_PHONE_STATE] ?: false
            val grantedCall = results[Manifest.permission.CALL_PHONE] ?: false
            if (grantedRead || grantedCall) {
                mainVm.refresh()
                simCollectorVm.refresh()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mainVm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application as Application)
        ).get(MainViewModel::class.java)
        simCollectorVm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application as Application)
        ).get(SimCollectorViewModel::class.java)

        val needRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        val needCall = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        if (needRead || needCall) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
        } else {
            mainVm.refresh()
            simCollectorVm.refresh()
        }

        setContent {
            ExtractorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(HomeRoute) {
                            HomeScreen(
                                onOpenDataExtractor = { navController.navigate(DataExtractorRoute) },
                                onOpenSimCollector = { navController.navigate(SimCollectorRoute) }
                            )
                        }
                        composable(DataExtractorRoute) {
                            DeviceInfoScreen(vm = mainVm, modifier = Modifier.fillMaxSize())
                        }
                        composable(SimCollectorRoute) {
                            SimCollectorScreen(vm = simCollectorVm, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val HomeRoute = "home"
        private const val DataExtractorRoute = "data_extractor"
        private const val SimCollectorRoute = "sim_collector"
    }
}

@Composable
private fun HomeScreen(
    onOpenDataExtractor: () -> Unit,
    onOpenSimCollector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Screen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onOpenDataExtractor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Data Extractor")
        }

        Button(
            onClick = onOpenSimCollector,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text(text = "SIM Number Collector")
        }
    }
}
