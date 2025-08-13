package com.app.basic_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import com.app.basic_app.ui.theme.Basic_appTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Settings
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.telephony.SmsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Basic_appTheme {
                MapScreen()
            }
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationHelper = remember { LocationHelper(context) }
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }
    
    val cameraPositionState = rememberCameraPositionState()
    var currentMarker by remember { mutableStateOf<MarkerState?>(null) }
    var isSelectingLocation by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSpeedLimit by remember { mutableStateOf(false) }
    var speedLimit by remember { mutableStateOf<Int?>(null) }
    var showSpeedLimitDialog by remember { mutableStateOf(false) }
    val geoapifyService = remember { GeoapifyService() }
    var isLoadingSpeedLimit by remember { mutableStateOf(false) }
    var speedLimitError by remember { mutableStateOf<String?>(null) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var isSearchingHospitals by remember { mutableStateOf(false) }
    var nearbyHospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }
    var showHospitalsList by remember { mutableStateOf(false) }
    val hospitalService = remember { HospitalService() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Update state variables - remove place ID and contact info states, add emergency states
    var isSearchingEmergency by remember { mutableStateOf(false) }
    var hospitalContactInfo by remember { mutableStateOf<String?>(null) }
    
    // Add SMS permission state
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Add SMS permission launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (!isGranted) {
            snackbarMessage = "SMS permission is required to send emergency alerts"
        }
    }

    // Add these state variables
    var showNumberInputDialog by remember { mutableStateOf(false) }
    var inputPhoneNumber by remember { mutableStateOf("") }
    var isPhoneNumberValid by remember { mutableStateOf(true) }

    // Add state for first-time emergency dialog
    var showEmergencyInfoDialog by remember { mutableStateOf(false) }

    // Add Bluetooth service and permission states
    val bluetoothService = remember { BluetoothService() }
    var hasBluetoothPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
    }

    // Collect Bluetooth connection state
    val bluetoothState by bluetoothService.connectionState.collectAsState()

    // Add this state collection for Bluetooth messages
    val bluetoothMessage by bluetoothService.receivedMessage.collectAsState()

    // Add this state variable at the top of MapScreen
    var speedLimitUpdateJob by remember { mutableStateOf<Job?>(null) }

    // Add these state variables
    var useFakeLocation by remember { mutableStateOf(false) }
    var showFakeLocationDialog by remember { mutableStateOf(false) }
    var fakeLocation by remember { mutableStateOf<LatLng?>(null) }
    var isSelectingFakeLocation by remember { mutableStateOf(false) }

    // Add these state variables at the top of MapScreen
    var showSettingsDialog by remember { mutableStateOf(false) }
    var sendSmsToHospital by remember { mutableStateOf(false) }
    var sendSmsToContact by remember { mutableStateOf(false) }
    var contactNumber by remember { mutableStateOf("") }

    // Add this state variable at the top of MapScreen
    var emergencyNumber by remember { mutableStateOf<String?>(null) }

    // Add this variable at the top of MapScreen with other state variables
    var lastSmsTime by remember { mutableStateOf(0L) }
    val SMS_COOLDOWN_PERIOD = 10000L // 10 seconds cooldown

    // Add these variables at the top of MapScreen
    val EMERGENCY_COOLDOWN = 5000L // 5 seconds cooldown
    var lastEmergencyTime by remember { mutableStateOf(0L) }

    // Add this state variable at the top with other state declarations
    var additionalContactNumber by remember { mutableStateOf("") }

    // Add state for Manual Override dialog
    var showManualOverrideDialog by remember { mutableStateOf(false) }
    // Add state for Manual Override active status
    var isManualOverrideActive by remember { mutableStateOf(false) }
    // Add state for reason dialog and input
    var showOverrideReasonDialog by remember { mutableStateOf(false) }
    var overrideReason by remember { mutableStateOf("") }
    var lastOverrideLogId by remember { mutableStateOf<String?>(null) }
    // Add state for override confirmation dialog
    var showOverrideConfirmationDialog by remember { mutableStateOf(false) }

    // Add this state variable at the top of MapScreen with other state variables
    var manualOverrideJob by remember { mutableStateOf<Job?>(null) }

    // Add state for crash detected dialog
    var crashDetectedDialog by remember { mutableStateOf(false) }

    // Add state for accident risk prediction
    var currentVehicleSpeed by remember { mutableStateOf(0) }
    var accidentRisk by remember { mutableStateOf(0.0) }
    val accidentRiskService = remember { AccidentRiskService() }
    
    // Add state for message tracking
    var lastReceivedMessage by remember { mutableStateOf<String?>(null) }
    var messageCount by remember { mutableStateOf(0) }

    // Dedicated crash detection handler function
    suspend fun handleCrashDetection(latLng: LatLng) {
        if (!hasSmsPermission) {
            snackbarMessage = "SMS permission required for crash alerts"
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }

        // Check if emergency contacts are configured
        val hasEmergencyContacts = contactNumber.isNotBlank() || additionalContactNumber.isNotBlank()
        
        if (!hasEmergencyContacts) {
            snackbarMessage = "No emergency contacts configured! Please add contacts in Settings."
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val mapsLink = "https://www.google.com/maps?q=${latLng.latitude},${latLng.longitude}"
                
                val crashMessage = "CRASH DETECTED\n" +
                                "Date & Time: $currentTime\n" +
                                "Latitude: ${latLng.latitude}\n" +
                                "Longitude: ${latLng.longitude}\n" +
                                "Location: $mapsLink\n" +
                                "IMMEDIATE ASSISTANCE REQUIRED!"
                
                val smsManager = SmsManager.getDefault()
                val messageParts = smsManager.divideMessage(crashMessage)
                
                var messagesSent = 0
                val contactList = mutableListOf<String>()
                
                // Send to primary emergency contact
                if (contactNumber.isNotBlank()) {
                    try {
                        smsManager.sendMultipartTextMessage(
                            contactNumber,
                            null,
                            messageParts,
                            null,
                            null
                        )
                        messagesSent++
                        contactList.add("primary contact")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Send to additional emergency contact
                if (additionalContactNumber.isNotBlank()) {
                    try {
                        smsManager.sendMultipartTextMessage(
                            additionalContactNumber,
                            null,
                            messageParts,
                            null,
                            null
                        )
                        messagesSent++
                        contactList.add("additional contact")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    snackbarMessage = if (messagesSent > 0) {
                        "Crash alert sent to ${contactList.joinToString(" and ")} ($messagesSent messages)"
                    } else {
                        "Failed to send crash alerts. Check contact numbers."
                    }
                }
                
                // Also try to get nearby hospitals for additional context
                try {
                    val hospitals = hospitalService.getNearbyHospitals(latLng).take(1)
                    if (hospitals.isNotEmpty()) {
                        val hospital = hospitals[0]
                        withContext(Dispatchers.Main) {
                            snackbarMessage += "\nNearest hospital: ${hospital.name}"
                        }
                    }
                } catch (e: Exception) {
                    // Hospital lookup failed, but crash alert already sent
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Error sending crash alerts: ${e.message}"
                }
            }
        }
    }

    // Update the function without the private modifier
    suspend fun getCurrentLocationOrFake(
        locationHelper: LocationHelper,
        useFakeLocation: Boolean,
        fakeLocation: LatLng?
    ): LatLng {
        return if (useFakeLocation && fakeLocation != null) {
            fakeLocation
        } else {
            val location = locationHelper.getCurrentLocation()
            LatLng(location.latitude, location.longitude)
        }
    }

    // Function to update accident risk
    fun updateAccidentRisk(latLng: LatLng) {
        try {
            accidentRisk = accidentRiskService.predictAccidentRisk(
                currentSpeed = currentVehicleSpeed,
                speedLimit = speedLimit,
                location = latLng,
                weather = AccidentRiskService.WeatherCondition.CLEAR, // Default to clear
                roadType = AccidentRiskService.RoadType.HIGHWAY // Default to highway
            )
        } catch (e: Exception) {
            // If prediction fails, keep previous risk value
        }
    }

    // Update the periodic speed limit updates function
    fun startPeriodicSpeedLimitUpdates() {
        speedLimitUpdateJob?.cancel()
        speedLimitUpdateJob = scope.launch {
            while (true) {
                if (hasLocationPermission) {
                    try {
                        val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                        currentMarker = MarkerState(position = latLng)
                        val limit = geoapifyService.getSpeedLimit(latLng)
                        if (limit != null) {
                            speedLimit = limit
                            if (!isManualOverrideActive) {
                                bluetoothService.sendSpeedLimit(limit)
                            }
                        }
                        // Update accident risk with current location
                        updateAccidentRisk(latLng)
                    } catch (e: Exception) {
                        snackbarMessage = "Error updating speed limit"
                    }
                }
                delay(2000)
            }
        }
    }

    // Add cleanup in a DisposableEffect
    DisposableEffect(Unit) {
        onDispose {
            speedLimitUpdateJob?.cancel()
        }
    }

    // Function to fetch speed limit with proper state management
    fun fetchSpeedLimit(latLng: LatLng) {
        scope.launch {
            isLoadingSpeedLimit = true
            speedLimit = null
            speedLimitError = null
            try {
                val limit = geoapifyService.getSpeedLimit(latLng)
                if (limit != null) {
                    speedLimit = limit
                    // Automatically send to ESP32
                    if (!isManualOverrideActive) {
                        bluetoothService.sendSpeedLimit(limit)
                    }
                } else {
                    speedLimitError = "No speed limit data available"
                }
            } catch (e: Exception) {
                speedLimitError = "Error fetching speed limit"
                e.printStackTrace()
            } finally {
                isLoadingSpeedLimit = false
                isCheckingSpeedLimit = false
            }
        }
    }

    // Function to clear existing data
    fun clearExistingData() {
        currentMarker = null
        speedLimit = null
        speedLimitError = null
        isLoadingSpeedLimit = false
    }

    // Updated function to handle hospital location selection
    fun handleHospitalLocationSelection(latLng: LatLng) {
        if (!sendSmsToHospital && !sendSmsToContact) {
            snackbarMessage = "Please configure SMS recipients in Settings → Checkboxes"
            return
        }
        
        if (sendSmsToContact && contactNumber.isBlank()) {
            snackbarMessage = "Please enter a contact number in Settings → Checkboxes"
            return
        }
        
        currentMarker = MarkerState(position = latLng)
        isSelectingLocation = false
        scope.launch {
            isSearchingHospitals = true
            try {
                nearbyHospitals = hospitalService.getNearbyHospitals(latLng)
                    .sortedBy { it.distance }
                    .take(2)
                
                if (nearbyHospitals.isNotEmpty()) {
                    // Get contact info for the two nearest hospitals first
                    nearbyHospitals = nearbyHospitals.map { hospital ->
                        val contactInfo = hospitalService.getHospitalContactInfo(
                            hospital.name,
                            hospital.address
                        )
                        // Extract phone number from contact info if available
                        val phoneNumber = if (contactInfo?.contains("Phone:") == true) {
                            contactInfo.substringAfter("Phone:").substringBefore("\n").trim()
                        } else null
                        
                        hospital.copy(
                            contactInfo = contactInfo,
                            displayPhoneNumber = phoneNumber
                        )
                    }
                    
                    val nearestHospital = nearbyHospitals[0]
                    var smsSuccessfullySent = false
                    
                    if (hasSmsPermission) {
                        // Send SMS to hospital if selected and phone number is available
                        if (sendSmsToHospital && !nearestHospital.displayPhoneNumber.isNullOrBlank()) {
                            hospitalService.sendEmergencySMS(latLng, nearestHospital.name, nearestHospital.displayPhoneNumber!!)
                            smsSuccessfullySent = true
                        }
                        
                        // Send SMS to custom contact if selected
                        if (sendSmsToContact && contactNumber.isNotBlank()) {
                            hospitalService.sendEmergencySMS(latLng, nearestHospital.name, contactNumber)
                            smsSuccessfullySent = true
                        }
                        
                        snackbarMessage = if (smsSuccessfullySent) {
                            "Emergency alert sent successfully"
                        } else {
                            "No SMS sent. Please check settings and hospital contact information."
                        }
                    } else {
                        snackbarMessage = "SMS permission not granted"
                    }
                    
                    showHospitalsList = true
                } else {
                    snackbarMessage = "No hospitals found in this area"
                }
            } catch (e: Exception) {
                snackbarMessage = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isSearchingHospitals = false
            }
            
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(latLng, 15f)
                ),
                durationMs = 1000
            )
        }
    }

    // Updated function to handle emergency location selection
    fun handleEmergencyLocationSelection(latLng: LatLng) {
        if (!hasSmsPermission) {
            snackbarMessage = "SMS permission required for emergency alerts"
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }

        if (!sendSmsToHospital && !sendSmsToContact) {
            snackbarMessage = "Please configure SMS recipients in Settings"
            return
        }

        if (sendSmsToContact && contactNumber.isBlank()) {
            snackbarMessage = "Please configure contact number in Settings"
            return
        }

        scope.launch {
            isSearchingEmergency = true
            try {
                // Get nearby hospitals
                nearbyHospitals = hospitalService.getNearbyHospitals(latLng)
                    .sortedBy { it.distance }
                    .take(2)

                if (nearbyHospitals.isNotEmpty()) {
                    // Show hospitals list first
                    showHospitalsList = true

                    // Get contact info for hospitals
                    nearbyHospitals = nearbyHospitals.map { hospital ->
                        val contactInfo = hospitalService.getHospitalContactInfo(
                            hospital.name,
                            hospital.address
                        )
                        // Extract phone number from contact info if available
                        val phoneNumber = if (contactInfo?.contains("Phone:") == true) {
                            contactInfo.substringAfter("Phone:").substringBefore("\n").trim()
                        } else null
                        
                        hospital.copy(
                            contactInfo = contactInfo,
                            displayPhoneNumber = phoneNumber
                        )
                    }

                    // Small delay to ensure UI updates
                    delay(500)

                    // Send SMS alerts
                    val nearestHospital = nearbyHospitals[0]
                    var smsSent = false

                    if (sendSmsToHospital && !nearestHospital.displayPhoneNumber.isNullOrBlank()) {
                        try {
                            hospitalService.sendEmergencySMS(
                                latLng,
                                nearestHospital.name,
                                nearestHospital.displayPhoneNumber!!
                            )
                            smsSent = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (sendSmsToContact && contactNumber.isNotBlank()) {
                        try {
                            hospitalService.sendEmergencySMS(
                                latLng,
                                nearestHospital.name,
                                contactNumber
                            )
                            smsSent = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    snackbarMessage = if (smsSent) {
                        "Emergency alerts sent successfully"
                    } else {
                        "Failed to send emergency alerts"
                    }

                    // Update camera position to show location
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(latLng, 15f)
                        ),
                        1000
                    )
                } else {
                    snackbarMessage = "No hospitals found nearby"
                }
            } catch (e: Exception) {
                snackbarMessage = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isSearchingEmergency = false
            }
        }
    }

    // Add this function to handle Bluetooth connection
    fun connectBluetooth() {
        if (!hasBluetoothPermission) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
            return
        }

        if (!bluetoothService.isBluetoothSupported()) {
            snackbarMessage = "Bluetooth is not supported on this device"
            return
        }

        if (!bluetoothService.isBluetoothEnabled()) {
            snackbarMessage = "Please enable Bluetooth"
            return
        }

        scope.launch {
            if (bluetoothService.connectToESP32()) {
                snackbarMessage = "Connected to ESP32"
            }
        }
    }

    // Update the LaunchedEffect for Bluetooth message handling
    LaunchedEffect(bluetoothService.receivedMessage.collectAsState().value) {
        val message = bluetoothService.receivedMessage.value?.trim()
        if (!message.isNullOrEmpty()) {
            // Update message tracking
            lastReceivedMessage = message
            messageCount++
            
            // Debug logging for message processing
            Log.d("MessageProcessing", "Processing message: '$message' (count: $messageCount)")
            
            when {
                message == "1" || message.contains("Trigger sent to app", ignoreCase = true) || 
                message.contains("crash", ignoreCase = true) || message.contains("trigger", ignoreCase = true) -> {
                    Log.d("CrashDetection", "Crash detection triggered by message: '$message'")
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastEmergencyTime > EMERGENCY_COOLDOWN) {
                        Log.d("CrashDetection", "Processing crash detection (cooldown passed)")
                        lastEmergencyTime = currentTime
                        crashDetectedDialog = true
                        
                        // Check if emergency contacts are configured
                        val hasEmergencyContacts = contactNumber.isNotBlank() || additionalContactNumber.isNotBlank()
                        if (!hasEmergencyContacts) {
                            snackbarMessage = "CRASH DETECTED but no emergency contacts configured! Please add contacts in Settings."
                            return@LaunchedEffect
                        }

                        if (hasLocationPermission) {
                            try {
                                val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                scope.launch {
                                    // Use dedicated crash detection handler
                                    handleCrashDetection(latLng)
                                }
                            } catch (e: Exception) {
                                snackbarMessage = "Error getting location for crash detection: ${e.message}"
                                e.printStackTrace()
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            snackbarMessage = "Location permission needed for crash detection"
                        }
                    }
                    // Clear the message after processing
                    bluetoothService.clearReceivedMessage()
                }
                message.startsWith("SPEED:") -> {
                    // Handle current vehicle speed from ESP32
                    try {
                        val speedValue = message.substringAfter("SPEED:").toIntOrNull()
                        if (speedValue != null && speedValue >= 0) {
                            currentVehicleSpeed = speedValue
                            // Update accident risk immediately when speed changes
                            if (hasLocationPermission) {
                                try {
                                    val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                    updateAccidentRisk(latLng)
                                } catch (e: Exception) {
                                    // Continue with risk calculation using last known values
                                    updateAccidentRisk(LatLng(12.9716, 77.5946)) // Default Bangalore coordinates
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Invalid speed format, ignore
                    }
                    bluetoothService.clearReceivedMessage()
                }
                message.matches(Regex("\\d+")) -> {
                    // Handle pure numeric speed values (backward compatibility)
                    try {
                        val speedValue = message.toIntOrNull()
                        if (speedValue != null && speedValue >= 0 && speedValue <= 200) { // Reasonable speed range
                            currentVehicleSpeed = speedValue
                            // Update accident risk immediately when speed changes
                            if (hasLocationPermission) {
                                try {
                                    val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                    updateAccidentRisk(latLng)
                                } catch (e: Exception) {
                                    // Continue with risk calculation using default location
                                    updateAccidentRisk(LatLng(12.9716, 77.5946))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Invalid format, ignore
                    }
                    bluetoothService.clearReceivedMessage()
                }
            }
        }
    }

    // Update the emergency SMS handling function
    suspend fun handleEmergencySMS(latLng: LatLng, hospitalName: String, hospitalPhone: String?) {
        if (!sendSmsToHospital && !sendSmsToContact) {
            snackbarMessage = "No SMS recipients selected. Please configure in Settings"
            return
        }

        var smsSuccessfullySent = false
        val sentTo = mutableListOf<String>()

        if (hasSmsPermission) {
            try {
                // Send SMS to hospital if selected
                if (sendSmsToHospital && !hospitalPhone.isNullOrBlank()) {
                    hospitalService.sendEmergencySMS(latLng, hospitalName, hospitalPhone)
                    smsSuccessfullySent = true
                    sentTo.add("hospital")
                }

                // Send SMS to emergency contacts if selected
                if (sendSmsToContact) {
                    var contactsSent = 0
                    
                    // Send to primary contact
                    if (contactNumber.isNotBlank()) {
                        hospitalService.sendEmergencySMS(latLng, hospitalName, contactNumber)
                        contactsSent++
                    }
                    
                    // Send to additional contact
                    if (additionalContactNumber.isNotBlank()) {
                        hospitalService.sendEmergencySMS(latLng, hospitalName, additionalContactNumber)
                        contactsSent++
                    }

                    if (contactsSent > 0) {
                        smsSuccessfullySent = true
                        sentTo.add(if (contactsSent == 2) "both emergency contacts" else "emergency contact")
                    }
                }

                snackbarMessage = when {
                    !smsSuccessfullySent -> "No messages sent. Please check contact settings."
                    sentTo.size == 1 -> "Emergency alert sent to ${sentTo[0]}"
                    else -> "Emergency alert sent to ${sentTo.joinToString(" and ")}"
                }
            } catch (e: Exception) {
                snackbarMessage = "Error sending SMS: ${e.message}"
            }
        } else {
            snackbarMessage = "SMS permission required to send emergency alerts"
        }
    }

    // Update this function to store the emergency number
    fun updateEmergencyNumber(number: String) {
        emergencyNumber = number
        hospitalService.updateEmergencyNumber(number)
    }

    // New function to send manual override SMS
    suspend fun sendManualOverrideSMS(latLng: LatLng) {
        withContext(Dispatchers.IO) {
            try {
                val phoneNumber = "+919482717310"
                val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val mapsLink = "https://www.google.com/maps?q=${latLng.latitude},${latLng.longitude}"


                val message = "MANUAL OVERRIDE ACTIVATED\n" +
                            "Date & Time: $currentTime\n" +
                            "Latitude: ${latLng.latitude}\n" +
                            "Longitude: ${latLng.longitude}\n" +
                            "Map Link: $mapsLink\n" +
                            "Vehicle is now allowed to exceed speed limits."
                             
                val smsManager = SmsManager.getDefault()
                val messageParts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    null,
                    null
                )
                
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Manual override activated. SMS sent to monitoring authority."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Failed to send manual override SMS: ${e.message}"
                }
            }
        }
    }

    // Function to send manual override deactivation SMS
    suspend fun sendManualOverrideDeactivationSMS(latLng: LatLng) {
        withContext(Dispatchers.IO) {
            try {
                val phoneNumber = "+919663016205"
                val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val mapsLink = "https://www.google.com/maps?q=${latLng.latitude},${latLng.longitude}"
                
                val message = "MANUAL OVERRIDE DEACTIVATED\n" +
                            "Date & Time: $currentTime\n" +
                            "Latitude: ${latLng.latitude}\n" +
                            "Longitude: ${latLng.longitude}\n" +
                            "Map Link: $mapsLink\n" +
                            "Vehicle is now back to normal speed limit enforcement."
                             
                val smsManager = SmsManager.getDefault()
                val messageParts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    null,
                    null
                )
                
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Manual override deactivated. Normal speed limits resumed."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Manual override deactivated. Normal speed limits resumed."
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = false
            ),
            onMapClick = { latLng ->
                if (isSelectingFakeLocation) {
                    fakeLocation = latLng
                    currentMarker = MarkerState(position = latLng)
                    isSelectingFakeLocation = false
                    snackbarMessage = "Fake location set"
                } else if (isSelectingLocation) {
                    if (isCheckingSpeedLimit) {
                        currentMarker = MarkerState(position = latLng)
                        fetchSpeedLimit(latLng)
                        isSelectingLocation = false
                        isCheckingSpeedLimit = false
                    } else {
                        handleHospitalLocationSelection(latLng)
                    }
                }
            }
        ) {
            // Current location marker
            currentMarker?.let { markerState ->
                Marker(
                    state = markerState,
                    title = "Selected Location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            // Hospital markers
            nearbyHospitals.forEach { hospital ->
                Marker(
                    state = MarkerState(hospital.location),
                    title = hospital.name,
                    snippet = "${hospital.distance.toInt()}m away",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Accident Risk Display - Top Left Corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 120.dp, start = 8.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                    .background(
                        color = when {
                            accidentRisk < 25 -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Green
                            accidentRisk < 50 -> Color(0xFFFF9800).copy(alpha = 0.9f) // Orange
                            accidentRisk < 75 -> Color(0xFFFF5722).copy(alpha = 0.9f) // Red-Orange
                            else -> Color(0xFFD32F2F).copy(alpha = 0.9f) // Red
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACCIDENT RISK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${accidentRisk.toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Speed: ${currentVehicleSpeed} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Top row for status messages and buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status messages
                if (useFakeLocation) {
                    Text(
                        text = "Using Fake Location",
                        modifier = Modifier
                            .background(
                                color = Color.Red.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Emergency contacts status
                val hasEmergencyContacts = contactNumber.isNotBlank() || additionalContactNumber.isNotBlank()
                if (!hasEmergencyContacts) {
                    Text(
                        text = "No Emergency Contacts Configured",
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFF9800).copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Top row buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Speed Limit Button
                    Button(
                        onClick = {
                            if (hasLocationPermission) {
                                scope.launch {
                                    try {
                                        isLoadingSpeedLimit = true
                                        val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                        currentMarker = MarkerState(position = latLng)
                                        fetchSpeedLimit(latLng)
                                        startPeriodicSpeedLimitUpdates()
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newCameraPosition(
                                                CameraPosition.fromLatLngZoom(latLng, 15f)
                                            ),
                                            durationMs = 1000
                                        )
                                    } catch (e: Exception) {
                                        snackbarMessage = "Error getting location"
                                    }
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Speed Limit", fontSize = 14.sp)
                    }

                    // Fake Location Button
                    Button(
                        onClick = { showFakeLocationDialog = true },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Fake Location", fontSize = 14.sp)
                    }
                }
            }

            // Settings button in top-end corner
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Bottom buttons container
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Show Manual Override Active message if active
                if (isManualOverrideActive) {
                    Text(
                        text = "Manual Override Active",
                        color = Color.Red,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Add button to end override
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    // Get current location for deactivation SMS
                                    val currentLatLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                    
                                    // Send deactivation SMS
                                    sendManualOverrideDeactivationSMS(currentLatLng)
                                    
                                    Log.d("BluetoothSend", "Ending manual override: sending '3' to ESP32")
                                    
                                    // Cancel manual override job and send deactivation signal
                                    manualOverrideJob?.cancel()
                                    repeat(5) {
                                        bluetoothService.sendCustomMessage("3")
                                        delay(2000)
                                    }
                                    
                                    // Update Firestore log
                                    lastOverrideLogId?.let { docId ->
                                        val db = FirebaseFirestore.getInstance()
                                        db.collection("OverrideLogs").document(docId)
                                            .update(
                                                mapOf(
                                                    "endTimestamp" to com.google.firebase.Timestamp.now(),
                                                    "status" to "deactivated",
                                                    "endLatitude" to currentLatLng.latitude,
                                                    "endLongitude" to currentLatLng.longitude
                                                )
                                            )
                                    }
                                    
                                    isManualOverrideActive = false
                                    // Resume normal speed limit enforcement
                                    startPeriodicSpeedLimitUpdates()
                                } catch (e: Exception) {
                                    snackbarMessage = "Error ending manual override: ${e.message}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("End Manual Override", color = Color.White)
                    }
                }
                // Manual Override Button
                Button(
                    onClick = { showManualOverrideDialog = true },
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Manual Override", color = Color.White)
                }

                // ESP32 Connection Button with updated states
                Button(
                    onClick = {
                        if (!hasBluetoothPermission) {
                            bluetoothPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN
                                )
                            )
                        } else {
                            scope.launch {
                                if (bluetoothService.connectToESP32()) {
                                    snackbarMessage = "Connected to ESP32"
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (bluetoothService.connectionState.collectAsState().value) {
                            is BluetoothService.ConnectionState.Connected -> Color(0xFF4CAF50) // Green
                            is BluetoothService.ConnectionState.Error -> Color(0xFFE53935) // Red
                            BluetoothService.ConnectionState.Disconnected -> Color(0xFFE53935) // Red
                        }
                    )
                ) {
                    Text(
                        when (bluetoothService.connectionState.collectAsState().value) {
                            is BluetoothService.ConnectionState.Connected -> "Connected to ESP32"
                            is BluetoothService.ConnectionState.Error -> "Connection Failed"
                            BluetoothService.ConnectionState.Disconnected -> "Not Connected to ESP32"
                        },
                        color = Color.White
                    )
                }

                // Track Current Location button
                Button(
                    onClick = {
                        if (hasLocationPermission) {
                            isLoading = true
                            scope.launch {
                                try {
                                    val location = locationHelper.getCurrentLocation()
                                    val latLng = LatLng(location.latitude, location.longitude)
                                    currentMarker = MarkerState(position = latLng)
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.fromLatLngZoom(latLng, 15f)
                                        ),
                                        durationMs = 1000
                                    )
                                } catch (e: Exception) {
                                    snackbarMessage = "Error getting location"
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Track Current Location")
                    }
                }
            }
        }

        // Bluetooth Message Debug Info - Top Right Corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "BT Messages: $messageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Last: ${lastReceivedMessage?.take(20) ?: "None"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Speed limit display with improved visibility
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            when {
                isLoadingSpeedLimit -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                    )
                }
                speedLimitError != null -> {
                    Text(
                        text = speedLimitError!!,
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                speedLimit != null -> {
                    Text(
                        text = "$speedLimit km/h",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // Add the number input dialog
        if (showNumberInputDialog) {
            AlertDialog(
                onDismissRequest = { showNumberInputDialog = false },
                title = { Text("Enter Emergency Contact Number") },
                text = {
                    Column {
                        Text(
                            "Enter the phone number of a trusted contact (family/guardian) " +
                            "who should receive emergency alerts with your location.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = inputPhoneNumber,
                            onValueChange = { 
                                inputPhoneNumber = it
                                isPhoneNumberValid = it.matches(Regex("^\\+?[1-9]\\d{9,14}$"))
                            },
                            label = { Text("Contact's Phone Number (with country code)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = !isPhoneNumberValid && inputPhoneNumber.isNotEmpty(),
                            supportingText = {
                                if (!isPhoneNumberValid && inputPhoneNumber.isNotEmpty()) {
                                    Text("Please enter a valid phone number with country code")
                                }
                            }
                        )
                        Text(
                            "Example: +91XXXXXXXXXX",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isPhoneNumberValid && inputPhoneNumber.isNotEmpty()) {
                                updateEmergencyNumber(inputPhoneNumber)
                                showNumberInputDialog = false
                                snackbarMessage = "Emergency number updated successfully"
                            }
                        },
                        enabled = isPhoneNumberValid && inputPhoneNumber.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { showNumberInputDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Update the emergency dialog to its previous state
        if (showEmergencyDialog) {
            AlertDialog(
                onDismissRequest = { showEmergencyDialog = false },
                title = { Text("Emergency - Find Nearest Hospital") },
                text = { 
                    Column {
                        if (!hospitalService.hasEmergencyNumber()) {
                            Text(
                                "Important: Emergency Contact Number Required\n\n" +
                                "Due to restrictions on sending SMS to hospital numbers directly, " +
                                "this app requires you to configure a personal emergency contact number " +
                                "(like family member or guardian).\n\n" +
                                "The app will:\n" +
                                "• Find the nearest hospital\n" +
                                "• Send your location to your emergency contact\n" +
                                "• Show you the hospital's contact information\n\n" +
                                "Without a configured number, you can still view nearby hospitals but no SMS alerts will be sent.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        TextButton(
                            onClick = { showNumberInputDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(if (hospitalService.hasEmergencyNumber()) 
                                "Update Emergency Contact" 
                                else "Add Emergency Contact")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEmergencyDialog = false
                            if (hasLocationPermission) {
                                scope.launch {
                                    try {
                                        val latLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                        handleEmergencyLocationSelection(latLng)
                                    } catch (e: Exception) {
                                        snackbarMessage = "Error getting location"
                                    }
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    ) {
                        Text(if (useFakeLocation) "Use Fake Location" else "Use Current Location")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showEmergencyDialog = false
                            clearExistingData()
                            isSelectingLocation = true
                            isCheckingSpeedLimit = false
                        }
                    ) {
                        Text("Select on Map")
                    }
                }
            )
        }

        // Selection instruction when in selection mode
        if (isSelectingLocation) {
            Text(
                text = if (isCheckingSpeedLimit) {
                    "Tap on the map to check speed limit"
                } else {
                    "Tap on the map to find nearby hospitals"
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp) // Increased padding to show below buttons
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Add instruction text for fake location selection
        if (isSelectingFakeLocation) {
            Text(
                text = "Tap on the map to set fake location",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Updated hospitals list to include contact info
        if (showHospitalsList) {
            AlertDialog(
                onDismissRequest = { showHospitalsList = false },
                title = { Text("Nearby Hospitals") },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        items(nearbyHospitals) { hospital ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = hospital.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hospital.address,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Distance: %.2f km".format(hospital.distance / 1000),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
    Text(
                                        text = hospital.contactInfo ?: "Loading contact information...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showHospitalsList = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Loading indicator
        if (isSearchingHospitals) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            )
        }

        // Loading indicator for emergency search
        if (isSearchingEmergency) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            )
        }

        // Snackbar
        snackbarMessage?.let { message ->
            LaunchedEffect(message) {
                snackbarHostState.showSnackbar(message)
                snackbarMessage = null
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        // Add Fake Location Dialog
        if (showFakeLocationDialog) {
            AlertDialog(
                onDismissRequest = { showFakeLocationDialog = false },
                title = { Text("Use Fake Location") },
                text = { Text("How would you like to set the fake location?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showFakeLocationDialog = false
                            isSelectingFakeLocation = true
                            useFakeLocation = true
                        }
                    ) {
                        Text("Select on Map")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showFakeLocationDialog = false
                            useFakeLocation = false
                            fakeLocation = null
                        }
                    ) {
                        Text("Turn Off Fake Location")
                    }
                }
            )
        }

        // Add the Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Emergency & Crash Detection Settings") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header text for crash detection
                        Text(
                            "Crash Detection Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            "These contacts will receive immediate SMS alerts when a crash is detected by ESP32:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Primary emergency contact
                        OutlinedTextField(
                            value = contactNumber,
                            onValueChange = { contactNumber = it },
                            label = { Text("Primary Emergency Contact") },
                            placeholder = { Text("e.g., +1234567890") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        // Additional emergency contact
                        OutlinedTextField(
                            value = additionalContactNumber,
                            onValueChange = { additionalContactNumber = it },
                            label = { Text("Secondary Emergency Contact") },
                            placeholder = { Text("e.g., +1234567890") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Header for manual emergency options
                        Text(
                            "Manual Emergency Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Hospital SMS checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = sendSmsToHospital,
                                onCheckedChange = { sendSmsToHospital = it }
                            )
                            Text("Send SMS to Hospital (manual emergency)")
                        }

                        // Emergency contacts SMS checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = sendSmsToContact,
                                onCheckedChange = { sendSmsToContact = it }
                            )
                            Text("Send SMS to Emergency Contacts (manual emergency)")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            if (!hasSmsPermission) {
                                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            }
                            showSettingsDialog = false 
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Manual Override Confirmation Dialog
        if (showManualOverrideDialog) {
            AlertDialog(
                onDismissRequest = { showManualOverrideDialog = false },
                title = { Text("Manual Override") },
                text = { Text("Are you sure you want to activate manual override? This will be logged and reviewed later. An SMS alert will be sent with your location details.") },
                confirmButton = {
                    Button(onClick = {
                        // Check for SMS permission first
                        if (!hasSmsPermission) {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                            showManualOverrideDialog = false
                            snackbarMessage = "SMS permission required for manual override"
                            return@Button
                        }

                                                 scope.launch {
                             try {
                                 // Get current location for SMS
                                 val currentLatLng = getCurrentLocationOrFake(locationHelper, useFakeLocation, fakeLocation)
                                 
                                 // Send SMS to the specified number
                                 sendManualOverrideSMS(currentLatLng)
                                 
                                 // Log to Firestore
                                 val db = FirebaseFirestore.getInstance()
                                 val log = hashMapOf(
                                     "timestamp" to com.google.firebase.Timestamp.now(),
                                     "status" to "active",
                                     "latitude" to currentLatLng.latitude,
                                     "longitude" to currentLatLng.longitude
                                 )
                                 db.collection("OverrideLogs")
                                     .add(log)
                                     .addOnSuccessListener { docRef ->
                                         scope.launch {
                                             Log.d("BluetoothSend", "Sending '2' for manual override activation")
                                             bluetoothService.sendCustomMessage("2")
                                         }
                                         // Stop sending speed limits to ESP32
                                         speedLimitUpdateJob?.cancel()
                                         manualOverrideJob?.cancel()
                                         // Send manual override signal continuously
                                         manualOverrideJob = scope.launch {
                                             while (true) {
                                                 bluetoothService.sendCustomMessage("2")
                                                 delay(2000)
                                             }
                                         }
                                         isManualOverrideActive = true
                                         showManualOverrideDialog = false
                                         showOverrideReasonDialog = true
                                         lastOverrideLogId = docRef.id
                                     }
                                     .addOnFailureListener {
                                         snackbarMessage = "Failed to log manual override"
                                         showManualOverrideDialog = false
                                     }
                             } catch (e: Exception) {
                                 snackbarMessage = "Error getting location for manual override"
                                 showManualOverrideDialog = false
                             }
                         }
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(onClick = { showManualOverrideDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        // Manual Override Reason Dialog
        if (showOverrideReasonDialog) {
            AlertDialog(
                onDismissRequest = { showOverrideReasonDialog = false },
                title = { Text("Manual Override Reason") },
                text = {
                    Column {
                        Text("Please explain why you are using manual override.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = overrideReason,
                            onValueChange = { overrideReason = it },
                            label = { Text("Reason") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val db = FirebaseFirestore.getInstance()
                        lastOverrideLogId?.let { docId ->
                            db.collection("OverrideLogs").document(docId)
                                .update("reason", overrideReason)
                                .addOnSuccessListener {
                                    showOverrideReasonDialog = false
                                    overrideReason = ""
                                    showOverrideConfirmationDialog = true
                                }
                                .addOnFailureListener {
                                    showOverrideReasonDialog = false
                                    overrideReason = ""
                                    showOverrideConfirmationDialog = true
                                }
                        }
                    }, enabled = overrideReason.isNotBlank()) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    Button(onClick = { showOverrideReasonDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        // Manual Override Confirmation Dialog
        if (showOverrideConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showOverrideConfirmationDialog = false },
                title = { Text("Override Logged") },
                text = { Text("Your override has been logged. This will be reviewed by the authorities. If misused, a fine may be issued.") },
                confirmButton = {
                    Button(onClick = { showOverrideConfirmationDialog = false }) {
                        Text("Return to Dashboard")
                    }
                }
            )
        }
        // Add the crash detected dialog UI
        if (crashDetectedDialog) {
            AlertDialog(
                onDismissRequest = { crashDetectedDialog = false },
                title = { 
                    Text(
                        "CRASH DETECTED!",
                        color = Color.Red,
                        style = MaterialTheme.typography.headlineSmall
                    ) 
                },
                text = { 
                    Column {
                        Text(
                            "A vehicle crash has been detected by the ESP32 sensor.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "Emergency alerts have been sent to your configured emergency contacts with your current location.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "If this was a false alarm, please contact your emergency contacts immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { crashDetectedDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("I'm Safe", color = Color.White)
                    }
                }
            )
        }
    }
}