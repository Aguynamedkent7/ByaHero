package com.example.byahero.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsToggle(
                        icon = Icons.Default.Notifications,
                        label = "Enable Notifications",
                        subtitle = "Allow journey updates",
                        checked = uiState.isNotificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )

                    if (uiState.isNotificationsEnabled) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        
                        SettingsToggle(
                            icon = Icons.Default.DirectionsBus,
                            label = "Jeepney Proximity",
                            subtitle = "Notify when jeep is near",
                            checked = uiState.notifyJeepneyNear,
                            onCheckedChange = { viewModel.setNotifyJeepneyNear(it) }
                        )
                        SettingsToggle(
                            icon = Icons.Default.DirectionsBus,
                            label = "Jeepney Distance",
                            subtitle = "Continuous distance updates",
                            checked = uiState.notifyJeepneyDistance,
                            onCheckedChange = { viewModel.setNotifyJeepneyDistance(it) }
                        )
                        SettingsToggle(
                            icon = Icons.Default.Place,
                            label = "Drop-off Distance",
                            subtitle = "Notify when near destination",
                            checked = uiState.notifyStopDistance,
                            onCheckedChange = { viewModel.setNotifyStopDistance(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Display",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                SettingsToggle(
                    icon = Icons.Default.DarkMode,
                    label = "Dark Mode",
                    subtitle = "Toggle application theme",
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }
        }
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
