package com.example.soundrepeater

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import java.util.Calendar

class SoundReceiver : BroadcastReceiver() {

    private val TAG = "SoundReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null) {
            // Check if we're in quiet hours
            if (isInQuietHours(context)) {
                Log.d(TAG, "Currently in quiet hours - skipping sound playback")
                
                // Still need to reschedule alarms even during quiet hours
                val interval = intent?.getIntExtra("interval", -1) ?: -1
                val intervalType = intent?.getStringExtra("intervalType") ?: "normal"
                val isDailyAlarm = intent?.getBooleanExtra("isDailyAlarm", false) ?: false
                val hour = intent?.getIntExtra("hour", -1) ?: -1
                val minute = intent?.getIntExtra("minute", -1) ?: -1
                val isSystemSound = intent?.getBooleanExtra("isSystemSound", true) ?: true
                val soundType = intent?.getIntExtra("soundType", RingtoneManager.TYPE_NOTIFICATION) ?: RingtoneManager.TYPE_NOTIFICATION
                val soundResId = intent?.getIntExtra("soundResId", 0) ?: 0
                
                if (interval > 0 && intervalType != "no_repeat") {
                    rescheduleNextAlarm(context, interval, intervalType, isSystemSound, soundType, soundResId)
                } else if (isDailyAlarm && hour >= 0 && minute >= 0) {
                    rescheduleDailyAlarm(context, hour, minute, isSystemSound, soundType, soundResId)
                } else if (intent?.action == "ACTION_HOURLY_MINUTE_MARK_CHIME") {
                    rescheduleNextMinuteMarkChime(context, intent)
                }
                return
            }
            
            // Check if this is an hourly minute mark chime
            if (intent?.action == "ACTION_HOURLY_MINUTE_MARK_CHIME") {
                handleHourlyMinuteMarkChime(context, intent)
                return
            }
            
            var isSystemSound = intent?.getBooleanExtra("isSystemSound", true) ?: true
            var soundType = intent?.getIntExtra("soundType", RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.TYPE_NOTIFICATION
            
            // Check for forced alarm sound (resuming after quiet hours)
            if (intent?.getBooleanExtra("forceAlarmSound", false) == true) {
                Log.d(TAG, "Resuming after quiet hours - forcing ALARM sound type")
                soundType = RingtoneManager.TYPE_ALARM
            }

            var soundResId = intent?.getIntExtra("soundResId", 0) ?: 0
            val interval = intent?.getIntExtra("interval", -1) ?: -1
            val intervalType = intent?.getStringExtra("intervalType") ?: "normal"
            val isDailyAlarm = intent?.getBooleanExtra("isDailyAlarm", false) ?: false
            val hour = intent?.getIntExtra("hour", -1) ?: -1
            val minute = intent?.getIntExtra("minute", -1) ?: -1
            
            // Check if we should use minute mark specific sound
            if (intervalType == "minute_mark" || intervalType == "top_of_hour") {
                val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)
                val minuteKey = if (intervalType == "top_of_hour") 0 else currentMinute
                
                // Check if there's a custom sound for this minute mark
                val hasCustomSound = intent?.hasExtra("minute${minuteKey}_isSystemSound") ?: false
                if (hasCustomSound) {
                    isSystemSound = intent?.getBooleanExtra("minute${minuteKey}_isSystemSound", true) ?: true
                    soundResId = intent?.getIntExtra("minute${minuteKey}_resourceId", 0) ?: 0
                    if (isSystemSound) {
                        soundType = intent?.getIntExtra("minute${minuteKey}_soundType", RingtoneManager.TYPE_NOTIFICATION)
                            ?: RingtoneManager.TYPE_NOTIFICATION
                    }
                    Log.d(TAG, "Using custom sound for minute mark :$minuteKey")
                }
            }
            
            Log.d(TAG, "========== ALARM TRIGGERED ==========")
            Log.d(TAG, "Is system sound: $isSystemSound")
            Log.d(TAG, "Sound type: $soundType")
            Log.d(TAG, "Sound resource ID: $soundResId")
            Log.d(TAG, "Interval: $interval minutes")
            Log.d(TAG, "Interval type: $intervalType")
            Log.d(TAG, "Is daily alarm: $isDailyAlarm")
            Log.d(TAG, "Current time: ${System.currentTimeMillis()}")

            // Play sound only if not "No Sound" option (resourceId != -1)
            if (soundResId != -1) {
                if (isSystemSound) {
                    playSound(context, soundType)
                } else {
                    playCustomSound(context, soundResId)
                }
            } else {
                Log.d(TAG, "No sound selected - skipping sound playback")
            }

            // Reschedule next alarm (but not for no_repeat type)
            if (interval > 0 && intervalType != "no_repeat") {
                // Interval-based scheduling
                rescheduleNextAlarm(context, interval, intervalType, isSystemSound, soundType, soundResId)
            } else if (isDailyAlarm && hour >= 0 && minute >= 0) {
                // Daily alarm rescheduling (needed for Android M+)
                rescheduleDailyAlarm(context, hour, minute, isSystemSound, soundType, soundResId)
            } else if (intervalType == "no_repeat") {
                // Check if minute mark chimes are configured before stopping service
                val prefs = context.getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
                val hasMinuteMarkChimes = listOf(0, 10, 20, 30, 40, 50).any { minute ->
                    val name = prefs.getString("${minute}_name", null)
                    val resourceId = prefs.getInt("${minute}_resourceId", 0)
                    name != null && resourceId != -1
                }
                
                if (hasMinuteMarkChimes) {
                    Log.d(TAG, "No repeat but minute mark chimes are configured - keeping service running")
                } else {
                    Log.d(TAG, "No repeat and no minute mark chimes - stopping service after one-time alarm")
                    // Stop the service after playing once
                    val serviceIntent = Intent(context, SoundService::class.java)
                    context.stopService(serviceIntent)
                }
            }

            // Show a toast notification
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "🔔 Sound Repeater Alert!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Context is null in onReceive")
        }
    }

    private fun rescheduleNextAlarm(context: Context, interval: Int, intervalType: String, isSystemSound: Boolean, soundType: Int, soundResId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            // Calculate trigger time first to determine if we need to force alarm sound
            val calendar = Calendar.getInstance()
            val triggerTime: Long

            when (intervalType) {
                "no_repeat" -> {
                    // Should not reach here, but handle gracefully
                    Log.d(TAG, "No repeat type - not rescheduling")
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
                    Log.d(TAG, "Rescheduling minute mark alarm at ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
                }
                "top_of_hour" -> {
                    // Schedule at top of every hour (:00)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.add(Calendar.HOUR_OF_DAY, 1)
                    triggerTime = calendar.timeInMillis
                    Log.d(TAG, "Rescheduling top of hour alarm at ${calendar.get(Calendar.HOUR_OF_DAY)}:00")
                }
                else -> {
                    // Normal interval scheduling
                    if (interval == 1) {
                        // For 1-minute intervals, schedule at next round minute
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        calendar.add(Calendar.MINUTE, 1)
                        triggerTime = calendar.timeInMillis
                        Log.d(TAG, "Rescheduling 1-minute alarm at round time ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
                    } else {
                        // For other intervals, schedule relative to current time
                        triggerTime = System.currentTimeMillis() + interval * 60 * 1000L
                    }
                }
            }

            val intent = Intent(context, SoundReceiver::class.java)
            intent.putExtra("interval", interval)
            intent.putExtra("intervalType", intervalType)
            intent.putExtra("isSystemSound", isSystemSound)
            if (isSystemSound) {
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", soundResId)
            }
            
            // Check if we are resuming after quiet hours
            val currentlyInQuietHours = isTimeInQuietHours(context, System.currentTimeMillis())
            val nextInQuietHours = isTimeInQuietHours(context, triggerTime)
            
            if (currentlyInQuietHours && !nextInQuietHours) {
                Log.d(TAG, "Next alarm will resume after quiet hours - setting forceAlarmSound")
                intent.putExtra("forceAlarmSound", true)
            }

            // Always pass minute mark chime settings so hourly chimes work independently
            copyMinuteMarkChimeSettings(context, intent)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val canScheduleExact = canScheduleExactAlarms(context, alarmManager)
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
                    } else {
                        // Fallback to inexact alarms
                        scheduleInexactAlarm(context, alarmManager, interval, triggerTime, pendingIntent)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException: Cannot schedule exact alarm, falling back to inexact", e)
                    scheduleInexactAlarm(context, alarmManager, interval, triggerTime, pendingIntent)
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
            }

            // Broadcast next ring time to update UI
            if (intervalType != "no_repeat") {
                val broadcastIntent = Intent("com.example.soundrepeater.NEXT_RING_TIME")
                broadcastIntent.putExtra("nextRingTime", triggerTime)
                broadcastIntent.putExtra("interval", interval)
                context.sendBroadcast(broadcastIntent)
            }
            
            Log.d(TAG, "Next alarm rescheduled for $interval minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling next alarm", e)
            e.printStackTrace()
        }
    }

    private fun rescheduleDailyAlarm(context: Context, hour: Int, minute: Int, isSystemSound: Boolean, soundType: Int, soundResId: Int) {
        try {
            // Only reschedule on Android M+ where setRepeating is not reliable
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return // setRepeating works fine on older Android versions
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }

            val intent = Intent(context, SoundReceiver::class.java)
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

            val pendingIntent = PendingIntent.getBroadcast(context, 1, intent, flags)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Schedule for next day
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val triggerTime = calendar.timeInMillis

            val canScheduleExact = canScheduleExactAlarms(context, alarmManager)
            try {
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact alarm
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException: Cannot schedule exact daily alarm, falling back to inexact", e)
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            // Broadcast next ring time to update UI
            val broadcastIntent = Intent("com.example.soundrepeater.NEXT_RING_TIME")
            broadcastIntent.putExtra("nextRingTime", triggerTime)
            context.sendBroadcast(broadcastIntent)

            Log.d(TAG, "Daily alarm rescheduled for tomorrow at $hour:$minute")
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling daily alarm", e)
            e.printStackTrace()
        }
    }

    private fun canScheduleExactAlarms(context: Context, alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires SCHEDULE_EXACT_ALARM permission
            alarmManager.canScheduleExactAlarms()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11: Assume true, but we'll catch SecurityException
            true
        } else {
            true // No permission needed for Android 5 and below
        }
    }

    private fun scheduleInexactAlarm(context: Context, alarmManager: AlarmManager, interval: Int, triggerTime: Long, pendingIntent: PendingIntent) {
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

    private fun playSound(context: Context, soundType: Int) {
        try {
            Log.d(TAG, "Attempting to play sound...")

            // Get the sound URI based on type
            val soundUri: Uri = RingtoneManager.getDefaultUri(soundType)
            Log.d(TAG, "Sound URI: $soundUri")

            val ringtone = RingtoneManager.getRingtone(context, soundUri)

            if (ringtone != null) {
                Log.d(TAG, "Ringtone object created successfully")
                ringtone.play()

                val soundTypeName = when(soundType) {
                    RingtoneManager.TYPE_NOTIFICATION -> "notification"
                    RingtoneManager.TYPE_ALARM -> "alarm"
                    RingtoneManager.TYPE_RINGTONE -> "ringtone"
                    else -> "unknown"
                }
                Log.d(TAG, "✓ Playing $soundTypeName sound - isPlaying: ${ringtone.isPlaying}")

                // Stop the ringtone after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (ringtone.isPlaying) {
                            ringtone.stop()
                            Log.d(TAG, "✓ Stopped $soundTypeName sound")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping ringtone", e)
                    }
                }, 5000)
            } else {
                Log.e(TAG, "✗ Ringtone is null - cannot play sound")

                // Try alternative notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                Log.d(TAG, "Notification manager available: ${nm != null}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPTION while playing sound", e)
            e.printStackTrace()
        }
    }
    
    private fun playCustomSound(context: Context, soundResId: Int) {
        try {
            Log.d(TAG, "Attempting to play custom sound with resource ID: $soundResId")

            val soundUri = Uri.parse("android.resource://${context.packageName}/$soundResId")
            Log.d(TAG, "Custom sound URI: $soundUri")

            val ringtone = RingtoneManager.getRingtone(context, soundUri)

            if (ringtone != null) {
                Log.d(TAG, "Custom ringtone object created successfully")
                ringtone.play()
                Log.d(TAG, "✓ Playing custom sound - isPlaying: ${ringtone.isPlaying}")

                // Stop the ringtone after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (ringtone.isPlaying) {
                            ringtone.stop()
                            Log.d(TAG, "✓ Stopped custom sound")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping custom ringtone", e)
                    }
                }, 5000)
            } else {
                Log.e(TAG, "✗ Custom ringtone is null - cannot play sound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPTION while playing custom sound", e)
            e.printStackTrace()
        }
    }
    
    private fun playCustomUriSound(context: Context, uriString: String) {
        try {
            Log.d(TAG, "Attempting to play custom URI sound: $uriString")

            val soundUri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, soundUri)

            if (ringtone != null) {
                Log.d(TAG, "Custom URI ringtone object created successfully")
                ringtone.play()
                Log.d(TAG, "✓ Playing custom URI sound - isPlaying: ${ringtone.isPlaying}")

                // Stop the ringtone after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (ringtone.isPlaying) {
                            ringtone.stop()
                            Log.d(TAG, "✓ Stopped custom URI sound")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping custom URI ringtone", e)
                    }
                }, 5000)
            } else {
                Log.e(TAG, "✗ Custom URI ringtone is null - cannot play sound")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ EXCEPTION while playing custom URI sound", e)
            e.printStackTrace()
        }
    }
    
    private fun copyMinuteMarkChimeSettings(context: Context, intent: Intent) {
        // Load minute mark chime settings from SharedPreferences and copy to intent
        val prefs = context.getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
        
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
            }
        }
    }
    
    private fun handleHourlyMinuteMarkChime(context: Context, intent: Intent) {
        try {
            val calendar = Calendar.getInstance()
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            Log.d(TAG, "========== MINUTE MARK CHIME TRIGGERED ==========")
            Log.d(TAG, "Current time: ${String.format("%02d:%02d", currentHour, currentMinute)}")
            Log.d(TAG, "Current minute: $currentMinute")
            
            // Determine which minute mark this should be (0, 10, 20, 30, 40, 50)
            // Round to nearest 10-minute mark
            val minuteMark = ((currentMinute + 5) / 10) * 10
            val normalizedMinuteMark = if (minuteMark >= 60) 0 else minuteMark
            
            // Check if we're within acceptable window (within 5 minutes of the mark)
            val minuteDiff = Math.abs(currentMinute - normalizedMinuteMark)
            val isWithinWindow = minuteDiff <= 5 || minuteDiff >= 55 // Handle wrap-around at :00
            
            if (!isWithinWindow) {
                Log.d(TAG, "Current time ($currentMinute) is too far from minute mark ($normalizedMinuteMark), skipping")
                rescheduleNextMinuteMarkChime(context, intent)
                return
            }
            
            Log.d(TAG, "Minute mark detected: :$normalizedMinuteMark (current: :$currentMinute)")
            
            // Check if there's a custom sound for this minute mark
            val hasCustomSound = intent.hasExtra("minute${normalizedMinuteMark}_isSystemSound")
            if (hasCustomSound) {
                val isSystemSound = intent.getBooleanExtra("minute${normalizedMinuteMark}_isSystemSound", true)
                val soundResId = intent.getIntExtra("minute${normalizedMinuteMark}_resourceId", 0)
                
                Log.d(TAG, "Custom sound found - isSystemSound: $isSystemSound, resourceId: $soundResId")
                
                // Check if there's a custom URI
                val customUri = intent.getStringExtra("minute${normalizedMinuteMark}_customUri")
                
                // Play sound only if not "No Sound" option (resourceId != -1)
                if (soundResId != -1) {
                    if (customUri != null) {
                        // Play custom URI sound
                        playCustomUriSound(context, customUri)
                        Log.d(TAG, "✓ Playing custom URI minute mark chime for :$normalizedMinuteMark")
                    } else if (isSystemSound) {
                        val soundType = intent.getIntExtra("minute${normalizedMinuteMark}_soundType", RingtoneManager.TYPE_NOTIFICATION)
                        playSound(context, soundType)
                        Log.d(TAG, "✓ Playing minute mark chime for :$normalizedMinuteMark (system sound)")
                    } else {
                        playCustomSound(context, soundResId)
                        Log.d(TAG, "✓ Playing custom minute mark chime for :$normalizedMinuteMark")
                    }
                } else {
                    Log.d(TAG, "No sound configured for minute :$normalizedMinuteMark (resourceId = -1)")
                }
            } else {
                Log.d(TAG, "No custom sound configured for minute :$normalizedMinuteMark")
            }
            
            // Reschedule for next 10-minute mark
            rescheduleNextMinuteMarkChime(context, intent)
            
            // Show a toast notification
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "🔔 Minute Mark Chime :$normalizedMinuteMark!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling minute mark chime", e)
            e.printStackTrace()
        }
    }
    
    private fun rescheduleNextMinuteMarkChime(context: Context, intent: Intent) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                Log.e(TAG, "AlarmManager is null")
                return
            }
            
            // Create new intent with same settings
            val newIntent = Intent(context, SoundReceiver::class.java)
            newIntent.action = "ACTION_HOURLY_MINUTE_MARK_CHIME"
            newIntent.putExtra("interval", 10)
            newIntent.putExtra("intervalType", "minute_mark_chime")
            newIntent.putExtra("isSystemSound", true)
            newIntent.putExtra("soundType", RingtoneManager.TYPE_NOTIFICATION)
            
            // Copy minute mark chime settings
            copyMinuteMarkChimeSettings(context, newIntent)
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            // Use request code 100 to differentiate from main interval alarm
            val pendingIntent = PendingIntent.getBroadcast(context, 100, newIntent, flags)
            
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
            
            Log.d(TAG, "Scheduling next minute mark chime at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
            
            // Use setAlarmClock for highest priority - bypasses Doze and all restrictions
            // This ensures minute mark chimes fire at the exact time
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val showIntent = Intent(context, MainActivity::class.java)
                    val showPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.getActivity(context, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    } else {
                        PendingIntent.getActivity(context, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                    }
                    
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d(TAG, "✓ Minute mark chime scheduled (HIGH PRIORITY - setAlarmClock) at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
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
                Log.d(TAG, "✓ Minute mark chime scheduled at ${calendar.get(Calendar.HOUR_OF_DAY)}:${String.format("%02d", calendar.get(Calendar.MINUTE))}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling minute mark chime", e)
            e.printStackTrace()
        }
    }
    
    private fun isInQuietHours(context: Context): Boolean {
        return isTimeInQuietHours(context, System.currentTimeMillis())
    }

    private fun isTimeInQuietHours(context: Context, timeInMillis: Long): Boolean {
        val prefs = context.getSharedPreferences("QuietHours", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        
        if (!isEnabled) {
            return false
        }
        
        val startHour = prefs.getInt("startHour", 2)
        val startMinute = prefs.getInt("startMinute", 0)
        val endHour = prefs.getInt("endHour", 9)
        val endMinute = prefs.getInt("endMinute", 0)
        
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        
        // Convert times to minutes since midnight for easier comparison
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute
        
        return if (startTimeInMinutes < endTimeInMinutes) {
            // Normal case: e.g., 9 AM to 5 PM
            currentTimeInMinutes >= startTimeInMinutes && currentTimeInMinutes < endTimeInMinutes
        } else {
            // Overnight case: e.g., 10 PM to 6 AM
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
        }
    }
}