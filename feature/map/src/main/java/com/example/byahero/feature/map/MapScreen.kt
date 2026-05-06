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
import androidx.compose.material.icons.filled.*
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
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    val simulatedJeepneys by viewModel.simulatedJeepneys.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val isSharingLocation by viewModel.isSharingLocation.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.entries.all { it.value }) viewModel.startLocationTracking()
        }
    )

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startLocationTracking()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(8.4847, 124.6566), 15f)
    }

    LaunchedEffect(navigationState) {
        if (navigationState is NavigationState.Navigating) {
            val dest = (navigationState as NavigationState.Navigating).destination
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(dest, 14f))
        }
    }

    val autocompleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    val place = Autocomplete.getPlaceFromIntent(intent)
                    place.latLng?.let { dest ->
                        viewModel.onDestinationSelected(dest, cameraPositionState.position.target)
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
                navigationState = navigationState,
                isSharingLocation = isSharingLocation,
                onToggleSharing = { viewModel.toggleLocationSharing() },
                onCancelNavigation = { viewModel.cancelNavigation() },
                onBoardJeep = { viewModel.confirmBoarded() },
                onSearchClick = {
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)).build(context)
                    autocompleteLauncher.launch(intent)
                },
                onRouteSelected = { option ->
                    val dest = (navigationState as? NavigationState.SelectingRoute)?.destination ?: cameraPositionState.position.target
                    viewModel.selectRoute(option, viewModel.userLocation.value ?: LatLng(8.4847, 124.6566), dest)
                },
                onJeepSelected = { jeep ->
                    if (navigationState is NavigationState.SelectingJeep) {
                        viewModel.selectJeep(jeep, navigationState as NavigationState.SelectingJeep)
                    }
                }
            )
        },
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = innerPadding,
                properties = MapProperties(isMyLocationEnabled = userLocation != null),
                uiSettings = MapUiSettings(myLocationButtonEnabled = true)
            ) {
                when (val state = navigationState) {
                    is NavigationState.Idle -> routes.forEach { route ->
                        Polyline(points = route.pathCoordinates.map {
                            if (it[0] > 90.0) LatLng(it[1], it[0]) else LatLng(it[0], it[1])
                        }, color = Color.LightGray, width = 12f)
                    }
                    is NavigationState.Navigating -> {
                        if (state.walkToPickupPath != null) Polyline(points = state.walkToPickupPath, color = Color.DarkGray, width = 12f, pattern = listOf(Dot(), Gap(20f)))
                        Polyline(points = state.ridePath, color = Color.Blue, width = 16f)
                        if (state.walkToDestinationPath != null) Polyline(points = state.walkToDestinationPath, color = Color.DarkGray, width = 12f, pattern = listOf(Dot(), Gap(20f)))
                        Marker(MarkerState(position = state.walkToPickupPath?.last() ?: state.ridePath.first()), title = "Board here")
                        Marker(MarkerState(position = state.walkToDestinationPath?.first() ?: state.ridePath.last()), title = "Drop off here")
                    }
                    else -> {}
                }
                simulatedJeepneys.forEach { jeep ->
                    val isSelected = (navigationState as? NavigationState.Navigating)?.selectedJeep?.id == jeep.id
                    Marker(state = MarkerState(position = jeep.currentLocation), title = "Jeepney ${jeep.id}", snippet = if (isSelected) "Your Ride" else "On Route", alpha = if (isSelected) 1.0f else 0.7f)
                }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            error?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) }
            TopNavigationOverlay(userLocation, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp))
        }
    }
}

@Composable
fun TopNavigationOverlay(userLocation: LatLng?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) { IconButton(onClick = {}) { Icon(Icons.Default.Menu, null) } }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (userLocation != null) "Current Location" else "USTP CDO, Lapasan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CommuterBottomSheetContent(
    navigationState: NavigationState,
    isSharingLocation: Boolean,
    onToggleSharing: () -> Unit,
    onCancelNavigation: () -> Unit,
    onBoardJeep: () -> Unit,
    onSearchClick: () -> Unit,
    onRouteSelected: (NavigationOption) -> Unit,
    onJeepSelected: (JeepneyInstance) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (navigationState) {
            is NavigationState.Idle -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSearchClick() }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null)
                        Text("Where to?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Switch(isSharingLocation, { onToggleSharing() })
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickActionItem(Icons.Default.Home, "Home")
                    QuickActionItem(Icons.Default.Place, "Work")
                    QuickActionItem(Icons.Default.Favorite, "Saved")
                }
            }
            is NavigationState.SelectingRoute -> {
                Text("Select a Jeepney Route", style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(navigationState.options.size) { index ->
                        val option = navigationState.options[index]
                        Card(Modifier.fillMaxWidth().clickable { onRouteSelected(option) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${option.routeName} (${option.routeCode})", style = MaterialTheme.typography.titleMedium)
                                Text("Walk: ${option.walkDistanceText}", style = MaterialTheme.typography.labelMedium)
                                Text("Ride: ${option.rideDistanceText}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            is NavigationState.SelectingJeep -> {
                Text("Select an Oncoming Jeepney", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Route: ${navigationState.option.routeName}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(navigationState.jeeps.size) { index ->
                        val jeep = navigationState.jeeps[index]
                        val isRecommended = index == 0
                        Card(
                            Modifier.fillMaxWidth().clickable { onJeepSelected(jeep) },
                            colors = if (isRecommended) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(if (isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.White)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Jeepney ${jeep.id}", style = MaterialTheme.typography.titleMedium)
                                    Text("${jeep.etaMinutes} mins away", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (isRecommended) {
                                    SuggestionChip(onClick = {}, label = { Text("Fastest") }, enabled = false)
                                }
                            }
                        }
                    }
                }
            }
            is NavigationState.Navigating -> {
                androidx.compose.animation.AnimatedVisibility(navigationState.journeyState != JourneyState.Onboard) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(
                            text = when (navigationState.journeyState) {
                                JourneyState.WalkingToPickup -> "Walk to boarding point"
                                JourneyState.WaitingForJeep -> "Jeepney is nearby!"
                                JourneyState.ApproachingDropoff -> "Approaching Dropoff!"
                                else -> ""
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (navigationState.journeyState == JourneyState.WaitingForJeep) {
                    Button(onClick = onBoardJeep, modifier = Modifier.fillMaxWidth()) { Text("I've Boarded") }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("From: Current Location", style = MaterialTheme.typography.bodyMedium)
                        navigationState.selectedJeep?.let { Text("Your Ride: Jeepney ${it.id}", fontWeight = FontWeight.Bold) }
                        Text("Walk: ${navigationState.walkDistanceText}", style = MaterialTheme.typography.labelMedium)
                        Text("Ride: ${navigationState.rideDistanceText}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(onClick = onCancelNavigation, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Cancel Navigation")
                }
            }
        }
    }
}

@Composable
fun QuickActionItem(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Icon(icon, label, modifier = Modifier.padding(12.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
