package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    private val TAG = "BootReceiver"
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - checking if service should restart")
            
            // Check if minute mark chimes are configured
            val prefs = context.getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
            val hasMinuteMarkChimes = listOf(0, 10, 20, 30, 40, 50).any { minute ->
                val name = prefs.getString("${minute}_name", null)
                val resourceId = prefs.getInt("${minute}_resourceId", 0)
                name != null && resourceId != -1
            }
            
            if (hasMinuteMarkChimes) {
                Log.d(TAG, "Minute mark chimes configured - restarting service")
                
                // Restart the service with no_repeat mode (minute mark chimes will still work)
                val serviceIntent = Intent(context, SoundService::class.java)
                serviceIntent.putExtra("interval", 0)
                serviceIntent.putExtra("intervalType", "no_repeat")
                serviceIntent.putExtra("isSystemSound", true)
                serviceIntent.putExtra("soundType", 0)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d(TAG, "No minute mark chimes configured - not restarting service")
            }
        }
    }
}
