package com.suraksha.plus

import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FakeCallScreen : AppCompatActivity() {
    
    private lateinit var callerName: String
    private lateinit var callerNumber: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_call)
        
        callerName = intent.getStringExtra("caller_name") ?: "Mom"
        callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
        
        setupUI()
        startRinging()
    }
    
    private fun setupUI() {
        findViewById<TextView>(R.id.fakeCallerName).text = callerName
        findViewById<TextView>(R.id.fakeCallerNumber).text = callerNumber
        
        findViewById<ImageButton>(R.id.answerButton).setOnClickListener {
            answerCall()
        }
        
        findViewById<ImageButton>(R.id.declineButton).setOnClickListener {
            declineCall()
        }
    }
    
    private fun startRinging() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(longArrayOf(0, 1000, 500, 1000), 1)
        
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(this, notification)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun answerCall() {
        // Simulate call in progress
        findViewById<View>(R.id.callControls).visibility = View.VISIBLE
        findViewById<View>(R.id.incomingControls).visibility = View.GONE
    }
    
    private fun declineCall() {
        finish()
    }
}