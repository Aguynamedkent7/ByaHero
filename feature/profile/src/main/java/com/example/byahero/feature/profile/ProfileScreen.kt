package com.example.byahero.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.byahero.core.ui.R

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
                title = { Text("Profile") },
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
            
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            uiState.userProfile?.let { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Personal Information", style = MaterialTheme.typography.titleLarge)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("Username: ${profile.username ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
                        Text("Email: ${profile.email ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
                        Text("Full Name: ${profile.fullName ?: "N/A"}", style = MaterialTheme.typography.bodyLarge)
                        Text("Role: ${profile.role?.replaceFirstChar { it.uppercase() } ?: "Commuter"}", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { 
                        newUsername = profile.username ?: ""
                        showEditUsernameDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Username")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { 
                        newPassword = ""
                        showEditPasswordDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Password")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                TextButton(
                    onClick = { 
                        viewModel.signOut()
                        onNavigateToLogin()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
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
                TextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("New Username") }
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updateUsername(newUsername) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUsernameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showEditPasswordDialog = false },
            title = { Text("Edit Password") },
            text = {
                TextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updatePassword(newPassword) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
