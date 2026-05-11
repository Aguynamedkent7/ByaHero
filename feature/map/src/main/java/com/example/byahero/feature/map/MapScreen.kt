package com.example.byahero.feature.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    val simulatedJeepneys by viewModel.simulatedJeepneys.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val isSharingLocation by viewModel.isSharingLocation.collectAsState()
    val driverBearing by viewModel.driverBearing.collectAsState()
    val coroutineScope = rememberCoroutineScope()

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

    var lastManualInteractionTime by remember { mutableLongStateOf(0L) }
    var isAutoFollowEnabled by remember { mutableStateOf(true) }
    var previewedJeepId by remember { mutableStateOf<String?>(null) }
    var lastNavigatedJeepId by remember { mutableStateOf<String?>(null) }

    // Detect manual movement
    LaunchedEffect(cameraPositionState.isMoving, navigationState) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            if (navigationState !is NavigationState.DriverNavigating) {
                isAutoFollowEnabled = false
                lastManualInteractionTime = System.currentTimeMillis()
            }
        }
    }

    // Timer to re-enable auto-follow
    LaunchedEffect(isAutoFollowEnabled, lastManualInteractionTime) {
        if (!isAutoFollowEnabled) {
            kotlinx.coroutines.delay(10000)
            isAutoFollowEnabled = true
        }
    }

    // Auto-dismiss error
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(10000)
            viewModel.clearError()
        }
    }

    // Camera Initial Zoom Logic
    LaunchedEffect(navigationState) {
        val state = navigationState
        
        if (state is NavigationState.Navigating) {
            val jeep = state.selectedJeep
            if (jeep != null) {
                if (lastNavigatedJeepId != jeep.id) {
                    lastNavigatedJeepId = jeep.id
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(state.destination, 15f))
                } 
            }
        } else {
            lastNavigatedJeepId = null
        }
    }

    // High-frequency Camera Follow Logic
    LaunchedEffect(isAutoFollowEnabled, navigationState) {
        if (!isAutoFollowEnabled && navigationState !is NavigationState.DriverNavigating) return@LaunchedEffect

        androidx.compose.runtime.snapshotFlow { 
            Triple(navigationState, userLocation, driverBearing)
        }.collect { triple ->
            val state = triple.first
            val loc = triple.second
            val bearing = triple.third
            
            val isLocValid = loc != null && loc.latitude != 0.0 && loc.longitude != 0.0

            if (state is NavigationState.DriverNavigating) {
                if (isLocValid) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(loc!!)
                                .zoom(18f)
                                .bearing(bearing)
                                .tilt(60f)
                                .build()
                        )
                    )
                }
            } else if (state is NavigationState.Navigating) {
                val jeep = state.selectedJeep
                if (jeep != null && lastNavigatedJeepId == jeep.id) {
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLng(jeep.currentLocation)
                    )
                }
            } else if (isLocValid) {
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(loc!!, 16f)
                )
            }
        }
    }

    LaunchedEffect(navigationState) {
        when (navigationState) {
            is NavigationState.Searching, 
            is NavigationState.SelectingRoute, 
            is NavigationState.SelectingJeep,
            is NavigationState.DriverNavigating -> {
                scaffoldState.bottomSheetState.expand()
                isAutoFollowEnabled = true
            }
            is NavigationState.Idle, is NavigationState.DriverIdle -> {
                previewedJeepId = null
                scaffoldState.bottomSheetState.partialExpand()
            }
            else -> {}
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (userRole == "driver") 200.dp else 140.dp,
        sheetContent = {
            Box(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 650.dp)) {
                if (userRole == "driver") {
                    DriverBottomSheetContent(
                        navigationState = navigationState,
                        onSelectRouteClick = { viewModel.selectDriverRoute() },
                        onRouteSelected = { route -> viewModel.startDriverTrip(route) },
                        onEndTrip = { viewModel.endDriverTrip() }
                    )
                } else {
                    CommuterBottomSheetContent(
                        navigationState = navigationState,
                        isSharingLocation = isSharingLocation,
                        onToggleSharing = { viewModel.toggleLocationSharing() },
                        onCancelNavigation = { viewModel.cancelNavigation() },
                        onBoardJeep = { viewModel.confirmBoarded() },
                        onSearchClick = { 
                            coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
                            viewModel.startSearching() 
                        },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onPredictionSelected = { viewModel.selectPrediction(it) },
                        onRouteSelected = { option ->
                            val dest = (navigationState as? NavigationState.SelectingRoute)?.destination ?: cameraPositionState.position.target
                            viewModel.selectRoute(option, viewModel.userLocation.value ?: LatLng(8.4847, 124.6566), dest)
                        },
                        onJeepSelected = { jeep ->
                            if (navigationState is NavigationState.SelectingJeep) {
                                previewedJeepId = null
                                viewModel.selectJeep(jeep, navigationState as NavigationState.SelectingJeep)
                            }
                        },
                        onJeepPreview = { jeep ->
                            isAutoFollowEnabled = false
                            lastManualInteractionTime = System.currentTimeMillis()
                            previewedJeepId = jeep.id
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(jeep.currentLocation, 16f))
                            }
                        }
                    )
                }
            }
        },
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 16.dp,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = innerPadding,
                properties = MapProperties(
                    isMyLocationEnabled = userLocation != null && navigationState !is NavigationState.DriverNavigating
                ),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false)
            ) {
                when (val state = navigationState) {
                    is NavigationState.Idle, is NavigationState.DriverIdle -> {
                        routes.forEach { route ->
                            Polyline(points = route.pathCoordinates.map {
                                if (it[0] > 90.0) LatLng(it[1], it[0]) else LatLng(it[0], it[1])
                            }, color = Color.LightGray, width = 12f)
                        }
                    }
                    is NavigationState.DriverNavigating -> {
                        Polyline(points = state.route.pathCoordinates.map {
                            if (it[0] > 90.0) LatLng(it[1], it[0]) else LatLng(it[0], it[1])
                        }, color = MaterialTheme.colorScheme.primary, width = 16f)
                        
                        state.passengers.forEachIndexed { index, pos ->
                            MarkerComposable(
                                state = rememberMarkerState(position = pos),
                                title = "Passenger ${index + 1}",
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color.Cyan, CircleShape)
                                        .border(2.dp, Color.White, CircleShape)
                                )
                            }
                        }

                        // Driver's own Jeepney Icon
                        userLocation?.let { loc ->
                            val driverMarkerState = rememberMarkerState(position = loc)
                            LaunchedEffect(loc) {
                                driverMarkerState.position = loc
                            }
                            MarkerComposable(
                                state = driverMarkerState,
                                title = "Your Jeepney",
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                            ) {
                                JeepneyIcon(
                                    backgroundColor = Color(0xFF00E676),
                                    icon = Icons.Default.DirectionsBus,
                                    isHighlighted = true,
                                    jeepId = "YOU"
                                )
                            }
                        }
                    }
                    is NavigationState.Navigating -> {
                        if (state.walkToPickupPath != null) Polyline(points = state.walkToPickupPath, color = Color.DarkGray, width = 12f, pattern = listOf(Dot(), Gap(20f)))
                        Polyline(points = state.ridePath, color = Color.Blue, width = 16f)
                        if (state.walkToDestinationPath != null) Polyline(points = state.walkToDestinationPath, color = Color.DarkGray, width = 12f, pattern = listOf(Dot(), Gap(20f)))
                        
                        // New: Show path from Jeep to Pickup
                        if (state.jeepToPickupPath != null) {
                            Polyline(points = state.jeepToPickupPath, color = Color.Magenta, width = 14f, pattern = listOf(Dot(), Gap(10f)))
                        }

                        Marker(MarkerState(position = state.walkToPickupPath?.last() ?: state.ridePath.first()), title = "Board here")
                        Marker(MarkerState(position = state.walkToDestinationPath?.first() ?: state.ridePath.last()), title = "Drop off here")
                    }
                    else -> {}
                }
                
                // Show simulated jeepneys if not a driver actively driving
                if (navigationState !is NavigationState.DriverNavigating) {
                    simulatedJeepneys.forEach { jeep ->
                        val isSelected = (navigationState as? NavigationState.Navigating)?.selectedJeep?.id == jeep.id
                        val isPreviewed = previewedJeepId == jeep.id
                        
                        // Composite key forces a new Marker to be created when state changes
                        val compositeKey = "${jeep.id}_${isSelected}_${isPreviewed}"
                        
                        key(compositeKey) {
                            val markerColor = when {
                                isSelected -> Color(0xFF00E676) // Bright Green (Confirmed)
                                isPreviewed -> Color(0xFFFF9100) // Bright Orange (Previewed)
                                else -> Color(0xFF9E9E9E) // Gray (Others)
                            }
                            
                            val markerIcon = when {
                                isSelected -> Icons.Default.Check
                                isPreviewed -> Icons.Default.Visibility
                                else -> Icons.Default.DirectionsBus
                            }

                            val markerState = rememberMarkerState(key = compositeKey, position = jeep.currentLocation)
                            LaunchedEffect(jeep.currentLocation) {
                                markerState.position = jeep.currentLocation
                            }

                            MarkerComposable(
                                state = markerState,
                                title = "Jeepney ${jeep.id}",
                                alpha = if (isSelected || isPreviewed) 1.0f else 0.6f,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                            ) {
                                JeepneyIcon(
                                    backgroundColor = markerColor,
                                    icon = markerIcon,
                                    isHighlighted = isSelected || isPreviewed,
                                    jeepId = jeep.id.takeLast(2) // Show last 2 chars of ID
                                )
                            }
                        }
                    }
                }
            }
            if (!isAutoFollowEnabled && (navigationState is NavigationState.Navigating || navigationState is NavigationState.DriverNavigating)) {
                Button(
                    onClick = { isAutoFollowEnabled = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 370.dp, end = 16.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.LocationOn, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Follow Jeep")
                }
            }
            // Floating Current Location Button
            if (navigationState !is NavigationState.DriverNavigating) {
                FloatingActionButton(
                    onClick = { isAutoFollowEnabled = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (navigationState is NavigationState.Idle) 160.dp else 420.dp, end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "My Location")
                }
            }

            if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            
            // Top Overlays
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopNavigationOverlay(
                    userLocation = userLocation,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToSettings = onNavigateToSettings
                )
                
                error?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JeepneyIcon(backgroundColor: Color, icon: ImageVector, isHighlighted: Boolean, jeepId: String) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(
                width = if (isHighlighted) 3.dp else 0.dp,
                color = if (isHighlighted) Color.White else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = jeepId,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TopNavigationOverlay(
    userLocation: LatLng?,
    modifier: Modifier = Modifier,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToProfile) { Icon(Icons.Default.Person, contentDescription = "Profile") }
                IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }
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
    onSearchQueryChange: (String) -> Unit,
    onPredictionSelected: (com.example.byahero.core.data.repository.PlacePrediction) -> Unit,
    onRouteSelected: (NavigationOption) -> Unit,
    onJeepSelected: (JeepneyInstance) -> Unit,
    onJeepPreview: (JeepneyInstance) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (navigationState) {
            is NavigationState.Idle -> {
                Text("Good day, Commuter!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSearchClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Where to?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickActionItem(Icons.Default.Home, "Home")
                    QuickActionItem(Icons.Default.Place, "Work")
                    QuickActionItem(Icons.Default.Favorite, "Saved")
                }
                Spacer(Modifier.height(16.dp))
            }
            is NavigationState.Searching -> {
                OutlinedTextField(
                    value = navigationState.query,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search destination...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { IconButton(onClick = { onCancelNavigation() }) { Icon(Icons.Default.Close, null) } },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    items(navigationState.predictions.size) { index ->
                        val prediction = navigationState.predictions[index]
                        Card(Modifier.fillMaxWidth().clickable { onPredictionSelected(prediction) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(prediction.primaryText, style = MaterialTheme.typography.titleMedium)
                                Text(prediction.secondaryText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
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
                            Modifier.fillMaxWidth().clickable { onJeepPreview(jeep) },
                            colors = if (isRecommended) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(40.dp).background(if (isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LocationOn, null, tint = Color.White)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Jeepney ${jeep.id}", style = MaterialTheme.typography.titleMedium)
                                    Text("${if (jeep.etaMinutes < 1) "Less than 1" else jeep.etaMinutes} mins away", style = MaterialTheme.typography.bodyMedium)
                                }
                                Button(onClick = { onJeepSelected(jeep) }) {
                                    Text("Select")
                                }
                            }
                        }
                    }
                }
            }
            is NavigationState.Navigating -> {
                // Toast-like notification for journey updates
                androidx.compose.animation.AnimatedVisibility(
                    visible = navigationState.isJeepNear || navigationState.journeyState != JourneyState.Onboard,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = when {
                                navigationState.isJeepNear && navigationState.journeyState == JourneyState.WalkingToPickup -> "Jeepney is approaching!"
                                navigationState.journeyState == JourneyState.WaitingForJeep -> "Jeepney is nearby, get ready!"
                                navigationState.journeyState == JourneyState.ApproachingDropoff -> "Approaching Dropoff!"
                                else -> "Walk to boarding point"
                            },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
            else -> {}
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

@Composable
fun DriverBottomSheetContent(
    navigationState: NavigationState,
    onSelectRouteClick: () -> Unit,
    onRouteSelected: (com.example.byahero.core.data.model.Route) -> Unit,
    onEndTrip: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        when (navigationState) {
            is NavigationState.DriverIdle -> {
                Text("Driver Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSelectRouteClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Select Route to Start")
                }
            }
            is NavigationState.DriverSelectingRoute -> {
                Text("Select your route", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(navigationState.availableRoutes.size) { index ->
                        val route = navigationState.availableRoutes[index]
                        Card(Modifier.fillMaxWidth().clickable { onRouteSelected(route) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text("${route.name} (${route.code})", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            is NavigationState.DriverNavigating -> {
                Text("Driving Route: ${navigationState.route.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Passengers waiting: ${navigationState.passengers.size}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onEndTrip,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End Trip")
                }
            }
            else -> {}
        }
    }
}
