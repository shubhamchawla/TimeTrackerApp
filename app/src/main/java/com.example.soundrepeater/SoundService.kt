package com.example.soundrepeater

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class SoundService : Service() {

    private val TAG = "SoundService"
    private var interval: Int = 5 // Default interval in minutes
    private var intervalType: String = "normal" // normal, minute_mark, top_of_hour
    private var soundType: Int = RingtoneManager.TYPE_NOTIFICATION
    private var isSystemSound: Boolean = true
    private var soundResId: Int = 0

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == "ACTION_SET_CUSTOM_SOUND") {
                val hour = intent.getIntExtra("hour", -1)
                val minute = intent.getIntExtra("minute", -1)
                isSystemSound = intent.getBooleanExtra("isSystemSound", true)
                if (isSystemSound) {
                    soundType = intent.getIntExtra("soundType", RingtoneManager.TYPE_NOTIFICATION)
                } else {
                    soundResId = intent.getIntExtra("soundResId", 0)
                }
                if (hour != -1 && minute != -1) {
                    createNotificationChannel()
                    val timeString = String.format("%02d:%02d", hour, minute)
                    val notification = createNotification(
                        "Sound Repeater Active",
                        "Daily alarm set for $timeString"
                    )
                    startForeground(1, notification)
                    scheduleRecurringSound(hour, minute)
                }
            } else {
                interval = intent?.getIntExtra("interval", 5) ?: 5
                intervalType = intent?.getStringExtra("intervalType") ?: "normal"
                isSystemSound = intent?.getBooleanExtra("isSystemSound", true) ?: true
                if (isSystemSound) {
                    soundType = intent?.getIntExtra("soundType", RingtoneManager.TYPE_NOTIFICATION) ?: RingtoneManager.TYPE_NOTIFICATION
                } else {
                    soundResId = intent?.getIntExtra("soundResId", 0) ?: 0
                }
                createNotificationChannel()
                
                // Check if minute mark chimes are configured
                val prefs = getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
                val hasMinuteMarkChimes = listOf(0, 10, 20, 30, 40, 50).any { minute ->
                    val name = prefs.getString("${minute}_name", null)
                    val resourceId = prefs.getInt("${minute}_resourceId", 0)
                    name != null && resourceId != -1
                }
                
                val intervalText = when (intervalType) {
                    "no_repeat" -> {
                        if (hasMinuteMarkChimes) {
                            "once (minute mark chimes active)"
                        } else {
                            "once (no repeat)"
                        }
                    }
                    "minute_mark" -> "at every ${interval}th minute"
                    "top_of_hour" -> "at top of every hour"
                    else -> "every $interval minutes"
                }
                val notification = createNotification(
                    "Sound Repeater Active",
                    "Playing sound $intervalText"
                )
                startForeground(1, notification)
                scheduleIntervalSound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cancelAlarms()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "sound_service_channel",
                "Sound Repeater Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Sound Repeater is running"
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, text: String): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, "sound_service_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun scheduleIntervalSound() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            val intent = Intent(this, SoundReceiver::class.java)
            intent.putExtra("interval", interval)
            intent.putExtra("intervalType", intervalType)
            intent.putExtra("isSystemSound", isSystemSound)
            if (isSystemSound) {
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", soundResId)
            }
            
            // Always pass minute mark chime settings so hourly chimes work independently
            copyMinuteMarkChimeSettings(intent)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)

            val calendar = Calendar.getInstance()
            val triggerTime: Long

            when (intervalType) {
                "no_repeat" -> {
                    // Don't schedule any alarm for no_repeat - only minute mark chimes will work
                    Log.d(TAG, "No repeat: Not scheduling any alarm (minute mark chimes will work independently)")
                    // Schedule hourly minute mark chimes independently if configured
                    scheduleHourlyMinuteMarkChimes()
                    return
                }
                "minute_mark" -> {
                    // Schedule at specific minute marks (e.g., :10, :20, :30, :40, :50)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val currentMinute = calendar.get(Calendar.MINUTE)
                    val nextMinute = ((currentMinute / interval) + 1) * interval
                    if (nextMinute >= 60) {
                        calendar.add(Calendar.HOUR_OF_DAY, 1)
                        calendar.set(Calendar.MINUTE, nextMinute - 60)
                    } else {
                        calendar.set(Calendar.MINUTE, nextMinute)
                    }
                    triggerTime = calendar.timeInMillis
                    Log.d(TAG, "Minute mark interval: Scheduling at ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
                }
                "top_of_hour" -> {
                    // Schedule at top of every hour (:00)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.add(Calendar.HOUR_OF_DAY, 1)
                    triggerTime = calendar.timeInMillis
                    Log.d(TAG, "Top of hour: Scheduling at ${calendar.get(Calendar.HOUR_OF_DAY)}:00")
                }
                else -> {
                    // Normal interval scheduling
                    if (interval == 1) {
                        // For 1-minute intervals, schedule at round minutes
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        calendar.add(Calendar.MINUTE, 1)
                        triggerTime = calendar.timeInMillis
                        Log.d(TAG, "1-minute interval: Scheduling at round time ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
                    } else {
                        // For other intervals, schedule relative to current time
                        triggerTime = System.currentTimeMillis() + interval * 60 * 1000L
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canScheduleExact = canScheduleExactAlarms(alarmManager)
                
                try {
                    if (canScheduleExact) {
                        if (interval == 1 || intervalType == "no_repeat") {
                            // Use RTC_WAKEUP for round time scheduling or one-time alarms
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                pendingIntent
                            )
                        } else {
                            // Use ELAPSED_REALTIME_WAKEUP for relative scheduling
                            val elapsedTriggerTime = SystemClock.elapsedRealtime() + (triggerTime - System.currentTimeMillis())
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                elapsedTriggerTime,
                                pendingIntent
                            )
                        }
                        Log.d(TAG, "Alarm scheduled using setExactAndAllowWhileIdle for $interval minutes")
                    } else {
                        // Fallback to inexact alarms if exact permission is not granted
                        scheduleInexactAlarm(alarmManager, interval, triggerTime, pendingIntent)
                        Log.d(TAG, "Alarm scheduled using inexact method (exact permission not granted) for $interval minutes")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException: Cannot schedule exact alarm, falling back to inexact", e)
                    scheduleInexactAlarm(alarmManager, interval, triggerTime, pendingIntent)
                }
            } else {
                if (interval == 1 || intervalType == "no_repeat") {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    val elapsedTriggerTime = SystemClock.elapsedRealtime() + (triggerTime - System.currentTimeMillis())
                    alarmManager.setRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        elapsedTriggerTime,
                        (interval * 60 * 1000).toLong(),
                        pendingIntent
                    )
                }
                Log.d(TAG, "Alarm scheduled using setRepeating for $interval minutes")
            }

            // Broadcast next ring time to update UI
            if (intervalType != "no_repeat") {
                broadcastNextRingTime(triggerTime)
            }
            
            // Schedule hourly minute mark chimes independently if configured
            scheduleHourlyMinuteMarkChimes()
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling interval sound", e)
            e.printStackTrace()
        }
    }
    
    private fun scheduleHourlyMinuteMarkChimes() {
        try {
            // Check if any minute mark chimes are configured
            val prefs = getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
            val hasAnyChime = listOf(0, 10, 20, 30, 40, 50).any { minute ->
                val name = prefs.getString("${minute}_name", null)
                val resourceId = prefs.getInt("${minute}_resourceId", 0)
                Log.d(TAG, "Checking minute :$minute - name: $name, resourceId: $resourceId")
                name != null && resourceId != -1
            }
            
            if (!hasAnyChime) {
                Log.d(TAG, "No minute mark chimes configured, skipping minute mark chime scheduling")
                return
            }
            
            Log.d(TAG, "✓ Minute mark chimes ARE configured, proceeding with scheduling")
            
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }
            
            // Schedule an alarm for the next 10-minute mark (0, 10, 20, 30, 40, 50)
            val intent = Intent(this, SoundReceiver::class.java)
            intent.action = "ACTION_HOURLY_MINUTE_MARK_CHIME"
            intent.putExtra("interval", 10)
            intent.putExtra("intervalType", "minute_mark_chime")
            intent.putExtra("isSystemSound", true)
            intent.putExtra("soundType", RingtoneManager.TYPE_NOTIFICATION)
            
            // Copy minute mark chime settings
            copyMinuteMarkChimeSettings(intent)
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Use request code 100 to differentiate from main interval alarm
            val pendingIntent = PendingIntent.getBroadcast(this, 100, intent, flags)
            
            // Calculate next 10-minute mark (0, 10, 20, 30, 40, 50)
            val calendar = Calendar.getInstance()
            val currentMinute = calendar.get(Calendar.MINUTE)
            
            // Find next 10-minute mark
            val nextMinuteMark = when {
                currentMinute < 10 -> 10
                currentMinute < 20 -> 20
                currentMinute < 30 -> 30
                currentMinute < 40 -> 40
                currentMinute < 50 -> 50
                else -> 0 // Next hour at :00
            }
            
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            if (nextMinuteMark == 0) {
                // Next mark is at top of next hour
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                calendar.set(Calendar.MINUTE, 0)
            } else {
                calendar.set(Calendar.MINUTE, nextMinuteMark)
            }
            
            val triggerTime = calendar.timeInMillis
            
            Log.d(TAG, "Scheduling initial minute mark chime at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
            
            // Use setAlarmClock for highest priority - bypasses Doze and all restrictions
            // This ensures minute mark chimes fire at the exact time
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val showIntent = Intent(this, MainActivity::class.java)
                    val showPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.getActivity(this, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    } else {
                        PendingIntent.getActivity(this, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    }
                    
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "✓ Initial minute mark chime scheduled (HIGH PRIORITY - setAlarmClock) at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to use setAlarmClock, falling back", e)
                    // Fallback to exact alarm
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "✓ Initial minute mark chime scheduled at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling minute mark chimes", e)
            e.printStackTrace()
        }
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires SCHEDULE_EXACT_ALARM permission
            alarmManager.canScheduleExactAlarms()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11: Check if app has USE_EXACT_ALARM (automatically granted)
            // or check system settings for Android 12+
            try {
                // Try to check if we can schedule exact alarms
                true // Assume true for older versions, but we'll catch SecurityException
            } catch (e: Exception) {
                false
            }
        } else {
            true // No permission needed for Android 5 and below
        }
    }

    private fun scheduleInexactAlarm(alarmManager: AlarmManager, interval: Int, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            if (interval == 1) {
                // For 1-minute intervals, use set() with RTC_WAKEUP (inexact but close)
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // For other intervals, use setWindow for better accuracy
                val elapsedTriggerTime = SystemClock.elapsedRealtime() + (triggerTime - System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setWindow(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        elapsedTriggerTime,
                        interval * 60 * 1000L, // Allow some window
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        elapsedTriggerTime,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling inexact alarm", e)
            throw e
        }
    }

    private fun broadcastNextRingTime(triggerTime: Long) {
        try {
            val intent = Intent("com.example.soundrepeater.NEXT_RING_TIME")
            intent.putExtra("nextRingTime", triggerTime)
            intent.putExtra("interval", interval)
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting next ring time", e)
        }
    }

    private fun scheduleRecurringSound(hour: Int, minute: Int) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            val intent = Intent(this, SoundReceiver::class.java)
            intent.putExtra("isDailyAlarm", true)
            intent.putExtra("hour", hour)
            intent.putExtra("minute", minute)
            intent.putExtra("isSystemSound", isSystemSound)
            if (isSystemSound) {
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", soundResId)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, flags)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val triggerTime = calendar.timeInMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canScheduleExact = canScheduleExactAlarms(alarmManager)
                try {
                    if (canScheduleExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Daily alarm scheduled for $hour:$minute")
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d(TAG, "Daily alarm scheduled using inexact method (exact permission not granted) for $hour:$minute")
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException: Cannot schedule exact alarm, falling back to inexact", e)
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Daily alarm scheduled using fallback method for $hour:$minute")
                }
            } else {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
                Log.d(TAG, "Daily repeating alarm scheduled for $hour:$minute")
            }

            // Broadcast next ring time to update UI
            broadcastNextRingTime(triggerTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling recurring sound", e)
            e.printStackTrace()
        }
    }

    private fun copyMinuteMarkChimeSettings(intent: Intent) {
        // Load minute mark chime settings from SharedPreferences and copy to intent
        val prefs = getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
        
        Log.d(TAG, "=== Copying Minute Mark Chime Settings ===")
        listOf(0, 10, 20, 30, 40, 50).forEach { minute ->
            val isSystemSound = prefs.getBoolean("${minute}_isSystemSound", true)
            val resourceId = prefs.getInt("${minute}_resourceId", 0)
            val name = prefs.getString("${minute}_name", null)
            val customUri = prefs.getString("${minute}_customUri", null)
            
            if (name != null) {
                intent.putExtra("minute${minute}_isSystemSound", isSystemSound)
                intent.putExtra("minute${minute}_resourceId", resourceId)
                intent.putExtra("minute${minute}_customUri", customUri)
                if (isSystemSound) {
                    val soundType = when (resourceId) {
                        0 -> RingtoneManager.TYPE_NOTIFICATION
                        1 -> RingtoneManager.TYPE_ALARM
                        2 -> RingtoneManager.TYPE_RINGTONE
                        else -> RingtoneManager.TYPE_NOTIFICATION
                    }
                    intent.putExtra("minute${minute}_soundType", soundType)
                }
                Log.d(TAG, "✓ Copied settings for :$minute - name: $name, isSystem: $isSystemSound, resId: $resourceId")
            } else {
                Log.d(TAG, "  Skipped :$minute (no name configured)")
            }
        }
        Log.d(TAG, "=== End Copying Settings ===")
    }

    private fun cancelAlarms() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Cancel main interval alarm
            val intent = Intent(this, SoundReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
            alarmManager?.cancel(pendingIntent)

            // Cancel daily alarm
            val customSoundIntent = Intent(this, SoundReceiver::class.java)
            val customSoundPendingIntent = PendingIntent.getBroadcast(this, 1, customSoundIntent, flags)
            alarmManager?.cancel(customSoundPendingIntent)
            
            // Cancel hourly minute mark chime alarm
            val hourlyChimeIntent = Intent(this, SoundReceiver::class.java)
            hourlyChimeIntent.action = "ACTION_HOURLY_MINUTE_MARK_CHIME"
            val hourlyChimePendingIntent = PendingIntent.getBroadcast(this, 100, hourlyChimeIntent, flags)
            alarmManager?.cancel(hourlyChimePendingIntent)

            Log.d(TAG, "All alarms cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarms", e)
        }
    }
}