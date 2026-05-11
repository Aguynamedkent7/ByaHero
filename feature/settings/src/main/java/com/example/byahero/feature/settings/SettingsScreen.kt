package com.example.byahero.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.byahero.core.ui.R

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("<")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("App Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsToggle(
                label = "Enable Notifications",
                checked = uiState.isNotificationsEnabled,
                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
            )

            if (uiState.isNotificationsEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    SettingsToggle(
                        label = "Notify when Jeepney is near",
                        checked = uiState.notifyJeepneyNear,
                        onCheckedChange = { viewModel.setNotifyJeepneyNear(it) }
                    )
                    SettingsToggle(
                        label = "Notify Jeepney distance",
                        checked = uiState.notifyJeepneyDistance,
                        onCheckedChange = { viewModel.setNotifyJeepneyDistance(it) }
                    )
                    SettingsToggle(
                        label = "Notify stop distance",
                        checked = uiState.notifyStopDistance,
                        onCheckedChange = { viewModel.setNotifyStopDistance(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsToggle(
                label = "Dark Mode",
                checked = uiState.isDarkMode,
                onCheckedChange = { viewModel.setDarkMode(it) }
            )
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
