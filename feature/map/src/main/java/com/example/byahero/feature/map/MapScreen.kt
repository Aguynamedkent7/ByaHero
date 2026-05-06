package com.example.byahero.feature.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    // Observe state from ViewModel
    val jeepneyLocation by viewModel.simulatedJeepneyLocation.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val isSharingLocation by viewModel.isSharingLocation.collectAsState()

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                viewModel.startLocationTracking()
            }
        }
    )

    // Check permissions and start tracking
    LaunchedEffect(Unit) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted && coarseLocationGranted) {
            viewModel.startLocationTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // USTP CDO Coordinates as default
    val ustpLocation = LatLng(8.4847, 124.6566)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ustpLocation, 15f)
    }

    // Helper to convert [lat, lng] or [lng, lat] to LatLng
    fun List<Double>.toLatLng(): LatLng {
        return if (this.size >= 2) {
            if (this[0] > 90.0) LatLng(this[1], this[0]) else LatLng(this[0], this[1])
        } else LatLng(0.0, 0.0)
    }

    // Move camera when routes are loaded or navigation starts
    LaunchedEffect(routes, navigationState) {
        if (navigationState is NavigationState.Idle && routes.isNotEmpty()) {
            val firstRoute = routes.first()
            val firstPoint = firstRoute.pathCoordinates.firstOrNull()?.toLatLng()
            if (firstPoint != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(firstPoint, 14f)
                )
            }
        } else if (navigationState is NavigationState.Navigating) {
            val dest = (navigationState as NavigationState.Navigating).destination
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(dest, 14f)
            )
        }
    }

    val autocompleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    place.latLng?.let { destLatLng ->
                        viewModel.onDestinationSelected(
                            destination = destLatLng,
                            fallbackOrigin = cameraPositionState.position.target
                        )
                    }
                }
            }
        }
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 350.dp,
        sheetContent = {
            CommuterBottomSheetContent(
                isSharingLocation = isSharingLocation,
                onToggleSharing = { viewModel.toggleLocationSharing() },
                onSearchClick = {
                    val intent = Autocomplete.IntentBuilder(
                        AutocompleteActivityMode.OVERLAY,
                        listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
                    ).build(context)
                    autocompleteLauncher.launch(intent)
                }
            )
        },
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = innerPadding,
                properties = MapProperties(isMyLocationEnabled = userLocation != null),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true)
            ) {
                when (val state = navigationState) {
                    is NavigationState.Idle -> {
                        // Draw all routes from Supabase when idle
                        routes.forEach { route ->
                            Polyline(
                                points = route.pathCoordinates.map { it.toLatLng() },
                                color = Color.LightGray,
                                width = 12f
                            )
                        }
                    }
                    is NavigationState.Navigating -> {
                        // Draw the walking path to pickup
                        if (state.walkToPickupPath != null) {
                            Polyline(
                                points = state.walkToPickupPath,
                                color = Color.DarkGray,
                                width = 12f,
                                pattern = listOf(Dot(), Gap(20f))
                            )
                        }

                        // Draw the specific route segment
                        Polyline(
                            points = state.ridePath,
                            color = Color.Blue,
                            width = 16f
                        )
                        
                        // Draw the walking path to destination
                        if (state.walkToDestinationPath != null) {
                            Polyline(
                                points = state.walkToDestinationPath,
                                color = Color.DarkGray,
                                width = 12f,
                                pattern = listOf(Dot(), Gap(20f))
                            )
                        }
                        
                        // Draw Origin and Destination Markers
                        Marker(
                            state = MarkerState(position = state.origin),
                            title = "Origin",
                            snippet = "Walk to pickup"
                        )
                        Marker(
                            state = MarkerState(position = state.destination),
                            title = "Destination",
                            snippet = "Walk from dropoff"
                        )
                    }
                }

                // Draw the simulated moving Jeepney
                Marker(
                    state = MarkerState(position = jeepneyLocation),
                    title = "Jeepney",
                    snippet = "On Route"
                )
            }

            // Error/Loading Overlays
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            
            error?.let {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 400.dp, start = 16.dp, end = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Top Navigation Overlay
            TopNavigationOverlay(
                userLocation = userLocation,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun TopNavigationOverlay(userLocation: LatLng?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger Menu
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            IconButton(onClick = { /* TODO: Open Drawer */ }) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Current Location Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (userLocation != null) "Current Location" else "USTP CDO, Lapasan",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CommuterBottomSheetContent(
    isSharingLocation: Boolean,
    onToggleSharing: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // "Where to?" Fake Input Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onSearchClick() }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Where to?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Location Sharing Toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Location with Drivers",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Allows drivers to see you on the map",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSharingLocation,
                    onCheckedChange = { onToggleSharing() }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionItem(icon = Icons.Default.Home, label = "Home")
            QuickActionItem(icon = Icons.Default.Place, label = "Work")
            QuickActionItem(icon = Icons.Default.Favorite, label = "Saved")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        // Nearby Jeepneys
        Text(
            text = "Nearby Jeepneys",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            items(5) { index ->
                JeepneyCard(index + 1)
            }
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { /* TODO */ }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun JeepneyCard(id: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Jeepney Icon/Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("L$id", color = Color.White, fontWeight = FontWeight.Bold) // "L" for Lapasan
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Lapasan Jeepney #$id",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "2 mins away • 5 seats available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
