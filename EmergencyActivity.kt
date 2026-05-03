package com.suraksha.plus

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class EmergencyActivity : AppCompatActivity() {
    
    private lateinit var locationText: TextView
    private lateinit var contactsText: TextView
    private lateinit var statusText: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        initializeViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Trigger emergency immediately
        triggerEmergencyProtocol()
    }
    
    private fun initializeViews() {
        locationText = findViewById(R.id.emergencyLocationText)
        contactsText = findViewById(R.id.emergencyContactsText)
        statusText = findViewById(R.id.emergencyStatusText)
    }
    
    @SuppressLint("MissingPermission")
    private fun triggerEmergencyProtocol() {
        statusText.text = "🆘 EMERGENCY ACTIVE"
        
        // Get location
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    locationText.text = "📍 ${it.latitude}, ${it.longitude}"
                }
            }
        }
    }
}