package com.suraksha.plus.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.suraksha.plus.R

class SOSService : Service() {
    
    companion object {
        const val CHANNEL_ID = "SOS_Channel"
        const val NOTIFICATION_ID = 1
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Start monitoring
        startSOSMonitoring()
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SOS Emergency Service"
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Active")
            .setContentText("Emergency services are active")
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }
    
    private fun startSOSMonitoring() {
        // Continuous monitoring while SOS is active
        Thread {
            while (true) {
                // Monitor location
                // Send updates
                // Check battery
                Thread.sleep(2000)
            }
        }.start()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}