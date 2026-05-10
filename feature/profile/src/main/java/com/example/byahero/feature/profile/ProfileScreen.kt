package com.example.byahero.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
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
            verticalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current
            val imageResId = context.resources.getIdentifier("logo", "drawable", context.packageName)
            if (imageResId != 0) {
                 Image(
                     painter = painterResource(id = imageResId),
                     contentDescription = "App Logo",
                     modifier = Modifier.size(120.dp)
                 )
                 Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Profile Information", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Username: Commuter123")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* Handle Edit Profile */ }) {
                Text("Edit Profile")
            }
        }
    }
}
