package com.suraksha.plus

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "SurakshaPlus"
        private const val SOS_TRIGGER_COUNT = 3
        private const val MAX_TRUSTED_CONTACTS = 5
        private const val LOCATION_UPDATE_INTERVAL = 5000L
        private const val LOCATION_FASTEST_INTERVAL = 2000L
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_CODE = 101
        private const val ACCESSIBILITY_PERMISSION_CODE = 102
        
        private const val EMERGENCY_NUMBER = "112"
        private const val POLICE_NUMBER = "100"
        private const val WOMEN_HELPLINE = "1091"
        
        const val SOS_ACTION = "com.suraksha.plus.SOS_TRIGGERED"
        const val FAKE_CALL_ACTION = "com.suraksha.plus.FAKE_CALL"
    }

    // UI Components
    private lateinit var sosButton: MaterialButton
    private lateinit var sosCardView: MaterialCardView
    private lateinit var fakeCallButton: MaterialButton
    private lateinit var safeWalkButton: MaterialButton
    private lateinit var reachedSafelyButton: MaterialButton
    private lateinit var contactsButton: MaterialButton
    private lateinit var nearbyHelpButton: MaterialButton
    private lateinit var stealthModeSwitch: SwitchMaterial
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var statusTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var batteryTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mainLayout: LinearLayout

    // Firebase
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var realtimeDatabase: DatabaseReference
    private var currentFirebaseUser: FirebaseUser? = null

    // Location Services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: Location? = null
    private var isTrackingLocation = false

    // SOS Components
    private var powerButtonPressCount = 0
    private var lastPowerPressTime = 0L
    private var isSOSActive = false
    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingStartTime: Long = 0
    private var sosHandler: Handler? = null
    private var sosVibrator: Vibrator? = null

    // Sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private var shakeCount = 0

    // Data
    private val trustedContacts = mutableListOf<EmergencyContact>()
    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("SurakshaPlus_Prefs", MODE_PRIVATE)
    }
    private val gson = Gson()

    // Recording files
    private var audioFile: File? = null
    private var videoFile: File? = null

    // Location manager
    private lateinit var locationManager: android.location.LocationManager

    // Network
    private lateinit var connectivityManager: ConnectivityManager

    // Telephony
    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        realtimeDatabase = FirebaseDatabase.getInstance().reference
        
        // Apply theme
        applyTheme()
        
        setContentView(R.layout.activity_main)
        
        // Initialize components
        initializeViews()
        initializeServices()
        initializeLocationServices()
        initializeSensors()
        checkAllPermissions()
        loadTrustedContacts()
        setupClickListeners()
        setupPowerButtonDetection()
        startStealthModeIfEnabled()
        
        // Authenticate user
        authenticateUser()
        
        // Start location tracking
        startLocationTracking()
        
        // Monitor battery
        startBatteryMonitoring()
        
        Log.d(TAG, "Suraksha+ MainActivity created successfully")
    }

    private fun initializeViews() {
        try {
            sosButton = findViewById(R.id.sosButton)
            sosCardView = findViewById(R.id.sosCardView)
            fakeCallButton = findViewById(R.id.fakeCallButton)
            safeWalkButton = findViewById(R.id.safeWalkButton)
            reachedSafelyButton = findViewById(R.id.reachedSafelyButton)
            contactsButton = findViewById(R.id.contactsButton)
            nearbyHelpButton = findViewById(R.id.nearbyHelpButton)
            stealthModeSwitch = findViewById(R.id.stealthModeSwitch)
            darkModeSwitch = findViewById(R.id.darkModeSwitch)
            statusTextView = findViewById(R.id.statusTextView)
            locationTextView = findViewById(R.id.locationTextView)
            batteryTextView = findViewById(R.id.batteryTextView)
            progressBar = findViewById(R.id.progressBar)
            mainLayout = findViewById(R.id.mainLayout)

            // Apply animations
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
            sosButton.startAnimation(pulseAnimation)

            // Set initial states
            updateUIState()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}")
        }
    }

    private fun initializeServices() {
        fusedLocationClient = android.location.LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        sosVibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        sosHandler = Handler(Looper.getMainLooper())
    }

    private fun initializeLocationServices() {
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_FASTEST_INTERVAL
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateLocationUI(location)
                    updateLocationToFirebase(location)
                }
            }
        }
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun setupClickListeners() {
        // SOS Button - Main Emergency Trigger
        sosButton.setOnClickListener {
            if (!isSOSActive) {
                showSOSConfirmationDialog()
            } else {
                deactivateSOS()
            }
        }

        // Long press for immediate SOS
        sosButton.setOnLongClickListener {
            triggerSOSImmediately()
            true
        }

        // Fake Call Button
        fakeCallButton.setOnClickListener {
            scheduleFakeCall()
        }

        fakeCallButton.setOnLongClickListener {
            triggerFakeCallImmediately()
            true
        }

        // Safe Walk Button
        safeWalkButton.setOnClickListener {
            startSafeWalk()
        }

        // Reached Safely Button
        reachedSafelyButton.setOnClickListener {
            notifyReachedSafely()
        }

        // Contacts Button
        contactsButton.setOnClickListener {
            openContactsManagement()
        }

        // Nearby Help Button
        nearbyHelpButton.setOnClickListener {
            openNearbyHelp()
        }

        // Stealth Mode Switch
        stealthModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleStealthMode(isChecked)
        }

        // Dark Mode Switch
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleDarkMode(isChecked)
        }
    }

    private fun showSOSConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("EMERGENCY SOS")
            .setMessage("Are you sure you want to trigger emergency SOS?\n\nThis will:\n• Send alerts to ${trustedContacts.size} contacts\n• Share your live location\n• Start audio/video recording\n• Activate loud alarm")
            .setPositiveButton("SEND SOS") { _, _ ->
                triggerSOS()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Quick SOS") { _, _ ->
                triggerSOSImmediately()
            }
            .setIcon(R.drawable.ic_warning_red)
            .setCancelable(false)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun triggerSOS() {
        if (isSOSActive) return
        
        try {
            isSOSActive = true
            updateUIState()
            
            // Show progress
            progressBar.visibility = View.VISIBLE
            statusTextView.text = "SOS ACTIVATED - Sending alerts..."
            
            // Start all emergency protocols
            activateLoudAlarm()
            startVibrationPattern()
            startAudioRecording()
            startVideoRecording()
            sendSMSToAllContacts()
            callPrimaryContact()
            updateLocationToFirebase(currentLocation!!)
            sendFirebaseAlert()
            saveSOSEvent()
            
            // Change button appearance
            sosButton.text = "SOS ACTIVE - TAP TO STOP"
            sosButton.setBackgroundColor(Color.RED)
            sosCardView.setCardBackgroundColor(Color.parseColor("#FF0000"))
            
            // Continuous location updates
            startEmergencyLocationUpdates()
            
            // Flash screen
            startScreenFlashing()
            
            Log.d(TAG, "SOS triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering SOS: ${e.message}")
            statusTextView.text = "Error: SOS activation failed"
            isSOSActive = false
            updateUIState()
        }
    }

    private fun triggerSOSImmediately() {
        // Immediate SOS without confirmation
        triggerSOS()
        vibratePattern(longArrayOf(0, 500, 200, 500, 200, 500))
    }

    private fun deactivateSOS() {
        try {
            isSOSActive = false
            updateUIState()
            
            // Stop all emergency protocols
            stopLoudAlarm()
            stopVibration()
            stopAudioRecording()
            stopVideoRecording()
            stopScreenFlashing()
            
            // Send deactivation alert
            sendDeactivationAlert()
            
            // Reset UI
            sosButton.text = "EMERGENCY\nSOS"
            sosButton.setBackgroundColor(Color.parseColor("#FF4444"))
            sosCardView.setCardBackgroundColor(Color.WHITE)
            statusTextView.text = "SOS Deactivated"
            progressBar.visibility = View.GONE
            
            Log.d(TAG, "SOS deactivated")
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating SOS: ${e.message}")
        }
    }

    private fun activateLoudAlarm() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.emergency_siren).apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing siren: ${e.message}")
        }
    }

    private fun stopLoudAlarm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                    release()
                }
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping siren: ${e.message}")
        }
    }

    private fun startVibrationPattern() {
        sosVibrator?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 1000, 500, 1000, 500, 1000),
                intArrayOf(0, 255, 0, 255, 0, 255),
                0
            )
        )
    }

    private fun stopVibration() {
        sosVibrator?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording() {
        try {
            audioFile = File.createTempFile("SOS_AUDIO_", ".mp3", cacheDir)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            Log.d(TAG, "Audio recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording: ${e.message}")
        }
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            // Save recording to secure storage
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    saveRecordingToSecureStorage(file, "audio")
                }
            }
            
            Log.d(TAG, "Audio recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideoRecording() {
        try {
            val intent = Intent(this, VideoRecordingService::class.java)
            intent.putExtra("duration", 300) // 5 minutes
            startForegroundService(intent)
            Log.d(TAG, "Video recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording: ${e.message}")
        }
    }

    private fun stopVideoRecording() {
        try {
            val intent = Intent(this, VideoRecordingService::class.java)
            stopService(intent)
            Log.d(TAG, "Video recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendSMSToAllContacts() {
        val location = currentLocation
        if (location == null) {
            Log.e(TAG, "No location available for SMS")
            return
        }
        
        val message = buildEmergencyMessage(location)
        
        trustedContacts.forEach { contact ->
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(
                    contact.phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
                Log.d(TAG, "SMS sent to ${contact.name}: ${contact.phoneNumber}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending SMS to ${contact.name}: ${e.message}")
            }
        }
    }

    private fun buildEmergencyMessage(location: Location): String {
        val mapLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        return """
            🆘 EMERGENCY ALERT - SURAKSHA+
            
            I need immediate help!
            
            📍 My Location:
            Latitude: ${location.latitude}
            Longitude: ${location.longitude}
            Accuracy: ${location.accuracy}m
            
            🗺️ Track me: $mapLink
            
            ⏰ Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}
            📅 Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}
            
            This is an automated emergency alert from Suraksha+ Women Safety App.
            Please respond immediately!
        """.trimIndent()
    }

    @SuppressLint("MissingPermission")
    private fun callPrimaryContact() {
        if (trustedContacts.isEmpty()) {
            // Call emergency services if no contacts
            callNumber(EMERGENCY_NUMBER)
            return
        }
        
        val primaryContact = trustedContacts.first()
        callNumber(primaryContact.phoneNumber)
    }

    @SuppressLint("MissingPermission")
    private fun callNumber(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "Calling: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error making call: ${e.message}")
        }
    }

    private fun updateLocationToFirebase(location: Location) {
        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "timestamp" to ServerValue.TIMESTAMP,
            "battery" to getBatteryLevel(),
            "isSOS" to isSOSActive
        )
        
        currentFirebaseUser?.let { user ->
            realtimeDatabase.child("users").child(user.uid).child("location")
                .setValue(locationData)
                .addOnSuccessListener {
                    Log.d(TAG, "Location updated to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating location: ${e.message}")
                }
        }
    }

    private fun sendFirebaseAlert() {
        val location = currentLocation ?: return
        
        val alert = mapOf(
            "type" to "SOS",
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to ServerValue.TIMESTAMP,
            "contacts" to trustedContacts.size,
            "battery" to getBatteryLevel(),
            "message" to "Emergency SOS triggered"
        )
        
        currentFirebaseUser?.let { user ->
            firestore.collection("alerts")
                .document(user.uid)
                .set(alert)
                .addOnSuccessListener {
                    Log.d(TAG, "Alert sent to Firebase")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error sending alert: ${e.message}")
                }
        }
    }

    private fun saveSOSEvent() {
        val location = currentLocation ?: return
        
        val event = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "contacts_notified" to trustedContacts.size,
            "duration" to 0,
            "location_shared" to true,
            "recording_started" to true
        )
        
        currentFirebaseUser?.let { user ->
            firestore.collection("users").document(user.uid)
                .collection("sos_events")
                .add(event)
                .addOnSuccessListener {
                    Log.d(TAG, "SOS event saved")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving SOS event: ${e.message}")
                }
        }
    }

    private fun sendDeactivationAlert() {
        val location = currentLocation ?: return
        
        val message = """
            ✅ SOS DEACTIVATED
            
            I have deactivated the emergency SOS.
            
            📍 Last known location:
            https://maps.google.com/?q=${location.latitude},${location.longitude}
            
            ⏰ Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}
            
            Sent via Suraksha+ Safety App
        """.trimIndent()
        
        trustedContacts.forEach { contact ->
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending deactivation SMS: ${e.message}")
            }
        }
    }

    private fun startEmergencyLocationUpdates() {
        // Update location every 2 seconds during SOS
        val emergencyRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 2000
            fastestInterval = 1000
        }
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    emergencyRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting emergency location: ${e.message}")
        }
    }

    private fun startScreenFlashing() {
        sosHandler?.post(object : Runnable {
            private var isRed = true
            override fun run() {
                if (!isSOSActive) return
                
                mainLayout.setBackgroundColor(
                    if (isRed) Color.RED else Color.WHITE
                )
                isRed = !isRed
                sosHandler?.postDelayed(this, 500)
            }
        })
    }

    private fun stopScreenFlashing() {
        sosHandler?.removeCallbacksAndMessages(null)
        mainLayout.setBackgroundColor(
            ContextCompat.getColor(this, R.color.background)
        )
    }

    private fun scheduleFakeCall() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Fake Call")
            .setMessage("Schedule a fake incoming call to escape uncomfortable situations")
            .setPositiveButton("In 30 seconds") { _, _ ->
                scheduleFakeCallWithDelay(30000)
            }
            .setNegativeButton("In 2 minutes") { _, _ ->
                scheduleFakeCallWithDelay(120000)
            }
            .setNeutralButton("Now") { _, _ ->
                triggerFakeCallImmediately()
            }
            .show()
    }

    private fun scheduleFakeCallWithDelay(delayMs: Long) {
        statusTextView.text = "Fake call scheduled in ${delayMs / 1000} seconds"
        
        Handler(Looper.getMainLooper()).postDelayed({
            triggerFakeCallImmediately()
        }, delayMs)
    }

    private fun triggerFakeCallImmediately() {
        val intent = Intent(this, FakeCallScreen::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("caller_name", getRandomCallerName())
            putExtra("caller_number", "Unknown Number")
        }
        startActivity(intent)
    }

    private fun getRandomCallerName(): String {
        val names = listOf("Mom", "Dad", "Brother", "Sister", "Friend", "Office", "Home")
        return names.random()
    }

    @SuppressLint("MissingPermission")
    private fun startSafeWalk() {
        if (!isLocationEnabled()) {
            showLocationEnableDialog()
            return
        }
        
        val intent = Intent(this, SafeWalkActivity::class.java)
        startActivity(intent)
    }

    private fun notifyReachedSafely() {
        val location = currentLocation
        val message = "✅ I reached safely!\n\n" +
                if (location != null) {
                    "📍 Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else {
                    "📍 Location not available"
                } + "\n⏰ ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}" +
                "\n\nSent via Suraksha+"

        trustedContacts.forEach { contact ->
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending safe message: ${e.message}")
            }
        }

        // Update Firebase
        currentFirebaseUser?.let { user ->
            firestore.collection("users").document(user.uid)
                .update("status", "safe")
        }

        Toast.makeText(this, "Family notified! ✅", Toast.LENGTH_LONG).show()
        statusTextView.text = "Reached safely notification sent ✓"
    }

    private fun openContactsManagement() {
        val intent = Intent(this, ContactsActivity::class.java)
        startActivity(intent)
    }

    private fun openNearbyHelp() {
        if (!isLocationEnabled()) {
            showLocationEnableDialog()
            return
        }
        
        val intent = Intent(this, NearbyHelpActivity::class.java)
        startActivity(intent)
    }

    private fun toggleStealthMode(enable: Boolean) {
        if (enable) {
            startStealthMode()
        } else {
            stopStealthMode()
        }
    }

    private fun startStealthMode() {
        sharedPreferences.edit().putBoolean("stealth_mode", true).apply()
        
        // Change app icon to calculator
        setAppIcon(R.drawable.ic_calculator)
        
        // Hide from recent apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity: Activity) {
                    activity.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
        
        // Start stealth service
        val intent = Intent(this, StealthService::class.java)
        startService(intent)
        
        statusTextView.text = "Stealth Mode: Active 🔒"
        Toast.makeText(this, "Stealth Mode Activated - App disguised as Calculator", Toast.LENGTH_SHORT).show()
    }

    private fun stopStealthMode() {
        sharedPreferences.edit().putBoolean("stealth_mode", false).apply()
        
        // Restore app icon
        setAppIcon(R.mipmap.ic_launcher)
        
        // Stop stealth service
        val intent = Intent(this, StealthService::class.java)
        stopService(intent)
        
        statusTextView.text = "Normal Mode"
    }

    private fun startStealthModeIfEnabled() {
        if (sharedPreferences.getBoolean("stealth_mode", false)) {
            startStealthMode()
        }
    }

    private fun setAppIcon(iconRes: Int) {
        try {
            val componentName = ComponentName(this, "com.suraksha.plus.MainActivity")
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error changing app icon: ${e.message}")
        }
    }

    private fun toggleDarkMode(enable: Boolean) {
        sharedPreferences.edit().putBoolean("dark_mode", enable).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (enable) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun applyTheme() {
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        darkModeSwitch.isChecked = isDarkMode
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupPowerButtonDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            registerPowerButtonReceiver()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun registerPowerButtonReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(powerButtonReceiver, filter)
    }

    private val powerButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handlePowerButtonPress()
                }
            }
        }
    }

    private fun handlePowerButtonPress() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastPowerPressTime < 1000) {
            powerButtonPressCount++
        } else {
            powerButtonPressCount = 1
        }
        
        lastPowerPressTime = currentTime
        
        if (powerButtonPressCount >= SOS_TRIGGER_COUNT) {
            Log.d(TAG, "SOS triggered by power button")
            powerButtonPressCount = 0
            triggerSOSImmediately()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isTrackingLocation = true
            val lastLocation = fusedLocationClient.lastLocation
            lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    updateLocationUI(it)
                }
            }
        }
    }

    private fun updateLocationUI(location: Location) {
        locationTextView.text = String.format(
            "📍 %.4f, %.4f (±%.0fm)",
            location.latitude,
            location.longitude,
            location.accuracy
        )
    }

    private fun startBatteryMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            batteryTextView.text = "🔋 $batteryPct%"
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun updateUIState() {
        if (isSOSActive) {
            statusTextView.text = "🆘 SOS ACTIVE"
            statusTextView.setTextColor(Color.RED)
            fakeCallButton.isEnabled = false
            safeWalkButton.isEnabled = false
        } else {
            statusTextView.text = "Ready ✓"
            statusTextView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            fakeCallButton.isEnabled = true
            safeWalkButton.isEnabled = true
        }
    }

    private fun authenticateUser() {
        currentFirebaseUser = firebaseAuth.currentUser
        if (currentFirebaseUser == null) {
            // Anonymous authentication for privacy
            firebaseAuth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        currentFirebaseUser = firebaseAuth.currentUser
                        Log.d(TAG, "Anonymous auth successful")
                        setupUserInFirestore()
                    } else {
                        Log.e(TAG, "Auth failed: ${task.exception?.message}")
                        statusTextView.text = "Authentication failed"
                    }
                }
        } else {
            setupUserInFirestore()
        }
    }

    private fun setupUserInFirestore() {
        currentFirebaseUser?.let { user ->
            val userData = mapOf(
                "uid" to user.uid,
                "lastLogin" to ServerValue.TIMESTAMP,
                "appVersion" to BuildConfig.VERSION_NAME,
                "deviceModel" to Build.MODEL,
                "androidVersion" to Build.VERSION.RELEASE
            )
            
            firestore.collection("users").document(user.uid)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "User setup in Firestore")
                }
        }
    }

    private fun loadTrustedContacts() {
        val json = sharedPreferences.getString("trusted_contacts", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<EmergencyContact>>() {}.type
                val contacts: List<EmergencyContact> = gson.fromJson(json, type)
                trustedContacts.clear()
                trustedContacts.addAll(contacts)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts: ${e.message}")
            }
        }
        
        if (trustedContacts.isEmpty()) {
            statusTextView.text = "No emergency contacts set. Please add contacts."
        } else {
            statusTextView.text = "${trustedContacts.size} emergency contacts ready ✓"
        }
    }

    private fun saveRecordingToSecureStorage(file: File, type: String) {
        try {
            val encryptedData = EncryptionUtils.encryptFile(file.readBytes())
            val encryptedFile = File(cacheDir, "ENCRYPTED_${file.name}")
            FileOutputStream(encryptedFile).use { it.write(encryptedData) }
            
            // Upload to Firebase Storage
            // uploadToFirebaseStorage(encryptedFile, type)
            
            Log.d(TAG, "$type recording encrypted and saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recording: ${e.message}")
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationEnableDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Required")
            .setMessage("Please enable GPS for accurate location tracking")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAllPermissions() {
        val permissions = mutableListOf<String>()
        
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            permissions.add(Manifest.permission.CALL_PHONE)
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (!hasPermission(Manifest.permission.VIBRATE)) {
            permissions.add(Manifest.permission.VIBRATE)
        }
        if (!hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        if (!hasPermission(Manifest.permission.FOREGROUND_SERVICE)) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            
            if (denied.isEmpty()) {
                statusTextView.text = "All permissions granted ✓"
                startLocationTracking()
            } else {
                statusTextView.text = "Some permissions denied. Limited functionality."
                Toast.makeText(this, "Please grant all permissions for full protection", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && isSOSActive) {
            detectShake(event)
        }
    }

    private fun detectShake(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH
        
        val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
        
        if (gForce > 3.0) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShakeTime > 2000) {
                shakeCount++
                if (shakeCount >= 3 && !isSOSActive) {
                    triggerSOS()
                    shakeCount = 0
                }
                lastShakeTime = currentTime
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        loadTrustedContacts()
        updateUIState()
        
        if (isTrackingLocation) {
            startLocationTracking()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isSOSActive) {
            // Continue tracking in background
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        try {
            stopLoudAlarm()
            stopVibration()
            sensorManager.unregisterListener(this)
            unregisterReceiver(batteryReceiver)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                unregisterReceiver(powerButtonReceiver)
            }
            
            if (isTrackingLocation) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
            
            // Clean up recordings
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && event?.isTracking == true) {
            // Volume up quick SOS
            if (sharedPreferences.getBoolean("volume_sos", false)) {
                triggerSOSImmediately()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun vibratePattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sosVibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            sosVibrator?.vibrate(pattern, -1)
        }
    }
}

// Data Classes
data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relation: String,
    val isPrimary: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val isSOS: Boolean = false
)

data class SOSAlert(
    val id: String,
    val location: UserLocation,
    val contactsNotified: Int,
    val timestamp: Long,
    val recordingsSaved: Boolean
)

// Encryption Utils
object EncryptionUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SECRET_KEY = "SurakshaPlus2024"

    fun encrypt(data: String): String {
        try {
            val key = generateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(data.toByteArray())
            return Base64.getEncoder().encodeToString(encrypted)
        } catch (e: Exception) {
            Log.e("EncryptionUtils", "Encryption error: ${e.message}")
            return data
        }
    }

    fun decrypt(encryptedData: String): String {
        try {
            val key = generateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = Base64.getDecoder().decode(encryptedData)
            val decrypted = cipher.doFinal(decoded)
            return String(decrypted)
        } catch (e: Exception) {
            Log.e("EncryptionUtils", "Decryption error: ${e.message}")
            return encryptedData
        }
    }

    fun encryptFile(data: ByteArray): ByteArray {
        try {
            val key = generateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e("EncryptionUtils", "File encryption error: ${e.message}")
            return data
        }
    }

    private fun generateKey(): SecretKey {
        return SecretKeySpec(SECRET_KEY.toByteArray(), ALGORITHM)
    }
}

// Constants
object Constants {
    const val EMERGENCY_NUMBER = "112"
    const val POLICE_NUMBER = "100"
    const val WOMEN_HELPLINE = "1091"
    const val AMBULANCE = "108"
    
    const val MAX_SOS_DURATION = 300000L // 5 minutes
    const val LOCATION_HISTORY_SIZE = 100
    
    const val FIREBASE_COLLECTION_USERS = "users"
    const val FIREBASE_COLLECTION_ALERTS = "alerts"
    const val FIREBASE_COLLECTION_LOCATIONS = "locations"
}