package com.example.byahero.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.byahero.core.data.repository.SavedPlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showEditUsernameDialog by remember { mutableStateOf(false) }
    var showEditPasswordDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    if (uiState.updateSuccess) {
        LaunchedEffect(Unit) {
            viewModel.resetUpdateSuccess()
            showEditUsernameDialog = false
            showEditPasswordDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.userProfile?.let { profile ->
                Spacer(modifier = Modifier.height(32.dp))
                
                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = profile.fullName ?: "User",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = profile.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = profile.role?.replaceFirstChar { it.uppercase() } ?: "Commuter",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Action List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ProfileMenuItem(
                        icon = Icons.Default.Edit,
                        title = "Edit Username",
                        subtitle = profile.username ?: "Set your username",
                        onClick = {
                            newUsername = profile.username ?: ""
                            showEditUsernameDialog = true
                        }
                    )
                    
                    ProfileMenuItem(
                        icon = Icons.Default.Lock,
                        title = "Change Password",
                        subtitle = "Update your security credentials",
                        onClick = {
                            newPassword = ""
                            showEditPasswordDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        "Saved Places",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Home Location
                    HomeWorkItem(
                        icon = Icons.Default.Home,
                        label = "Home",
                        location = uiState.homeLocation,
                        onDeleteClick = { viewModel.deleteHomeLocation() }
                    )

                    // Work Location
                    HomeWorkItem(
                        icon = Icons.Default.Place,
                        label = "Work",
                        location = uiState.workLocation,
                        onDeleteClick = { viewModel.deleteWorkLocation() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.savedPlaces.isEmpty()) {
                        Text(
                            "No saved places yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                        )
                    } else {
                        uiState.savedPlaces.forEach { place ->
                            SavedPlaceItem(
                                place = place,
                                onDeleteClick = { viewModel.deleteSavedPlace(place.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Logout Button
                Button(
                    onClick = { 
                        viewModel.signOut()
                        onNavigateToLogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (showEditUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showEditUsernameDialog = false },
            title = { Text("Edit Username") },
            text = {
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("New Username") },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updateUsername(newUsername) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditUsernameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showEditPasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updatePassword(newPassword) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 8.dp),
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
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SavedPlaceItem(
    place: SavedPlace,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HomeWorkItem(
    icon: ImageVector,
    label: String,
    location: String?,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp),
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
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (location != null) "Location set" else "Not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (location != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (location != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
