package com.example.soundrepeater

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var intervalSpinner: Spinner
    private lateinit var soundSpinner: Spinner
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var customTimeButton: MaterialButton
    private lateinit var minuteMarkChimeButton: MaterialButton
    private lateinit var hourlyChimeButton: MaterialButton
    private lateinit var testSoundButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var nextRingText: TextView
    private var currentTestRingtone: Ringtone? = null

    private var isServiceRunning = false
    private var selectedHour = -1
    private var selectedMinute = -1
    private var nextRingTimeReceiver: BroadcastReceiver? = null
    private var currentInterval: Int = 5
    private var availableSounds = mutableListOf<SoundInfo>()
    private val hourlyChimeSounds = mutableMapOf<Int, Int>() // hour -> sound resource ID
    private val minuteMarkChimeSounds = mutableMapOf<Int, SoundInfo>() // minute mark (10,20,30,40,50,0) -> SoundInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Check exact alarm permission for Android 12+
        checkExactAlarmPermission()

        initViews()
        loadMinuteMarkChimeSettings()
        setupSpinners()
        setupButtons()
        registerNextRingTimeReceiver()
        checkServiceState()
    }

    private fun initViews() {
        intervalSpinner = findViewById(R.id.intervalSpinner)
        soundSpinner = findViewById(R.id.soundSpinner)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        customTimeButton = findViewById(R.id.customTimeButton)
        minuteMarkChimeButton = findViewById(R.id.minuteMarkChimeButton)
        hourlyChimeButton = findViewById(R.id.hourlyChimeButton)
        testSoundButton = findViewById(R.id.testSoundButton)
        statusText = findViewById(R.id.statusText)
        nextRingText = findViewById(R.id.nextRingText)
        
        loadAvailableSounds()
    }
    
    data class SoundInfo(val name: String, val resourceId: Int, val isSystemSound: Boolean)
    
    private fun loadAvailableSounds() {
        availableSounds.clear()
        
        // Add "No Sound" option first
        availableSounds.add(SoundInfo("No Sound", -1, true))
        
        // Add system sounds
        availableSounds.add(SoundInfo("Default Notification", 0, true))
        availableSounds.add(SoundInfo("Alarm Sound", 1, true))
        availableSounds.add(SoundInfo("Ringtone", 2, true))
        
        // Load sounds from raw folder
        val rawClass = R.raw::class.java
        val fields = rawClass.fields
        for (field in fields) {
            try {
                val resourceId = field.getInt(null)
                val soundName = field.name.replace("_", " ").split(" ")
                    .joinToString(" ") { it.capitalize() }
                availableSounds.add(SoundInfo(soundName, resourceId, false))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupSpinners() {
        // Interval Spinner - Added new interval options
        val intervals = arrayOf(
            "No repeat", "1 minute", "5 minutes", "10 minutes", "15 minutes", "20 minutes", 
            "30 minutes", "40 minutes", "50 minutes", "1 hour",
            "Every 10th minute", "Every 20th minute", "Every 30th minute",
            "Every 40th minute", "Every 50th minute", "Top of hour"
        )
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        intervalSpinner.adapter = intervalAdapter
        intervalSpinner.setSelection(2) // Default to 5 minutes

        // Sound Spinner - Use available sounds
        val soundNames = availableSounds.map { it.name }.toTypedArray()
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, soundNames)
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        soundSpinner.adapter = soundAdapter
    }

    private fun setupButtons() {
        startButton.setOnClickListener {
            if (!isServiceRunning) {
                startSoundService()
            }
        }

        stopButton.setOnClickListener {
            if (isServiceRunning) {
                stopSoundService()
            }
        }

        customTimeButton.setOnClickListener {
            showTimePicker()
        }

        minuteMarkChimeButton.setOnClickListener {
            showMinuteMarkChimeDialog()
        }

        hourlyChimeButton.setOnClickListener {
            showHourlyChimeDialog()
        }

        testSoundButton.setOnClickListener {
            testSound()
        }
    }

    private fun startSoundService() {
        try {
            // Check exact alarm permission before starting service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms()) {
                    showExactAlarmPermissionDialog()
                    return
                }
            }

            val intervalPosition = intervalSpinner.selectedItemPosition
            val intervalMinutes: Int
            val intervalType: String
            
            when (intervalPosition) {
                0 -> { intervalMinutes = 0; intervalType = "no_repeat" }     // "No repeat"
                1 -> { intervalMinutes = 1; intervalType = "normal" }        // "1 minute"
                2 -> { intervalMinutes = 5; intervalType = "normal" }        // "5 minutes"
                3 -> { intervalMinutes = 10; intervalType = "normal" }       // "10 minutes"
                4 -> { intervalMinutes = 15; intervalType = "normal" }       // "15 minutes"
                5 -> { intervalMinutes = 20; intervalType = "normal" }       // "20 minutes"
                6 -> { intervalMinutes = 30; intervalType = "normal" }       // "30 minutes"
                7 -> { intervalMinutes = 40; intervalType = "normal" }       // "40 minutes"
                8 -> { intervalMinutes = 50; intervalType = "normal" }       // "50 minutes"
                9 -> { intervalMinutes = 60; intervalType = "normal" }       // "1 hour"
                10 -> { intervalMinutes = 10; intervalType = "minute_mark" } // "Every 10th minute"
                11 -> { intervalMinutes = 20; intervalType = "minute_mark" } // "Every 20th minute"
                12 -> { intervalMinutes = 30; intervalType = "minute_mark" } // "Every 30th minute"
                13 -> { intervalMinutes = 40; intervalType = "minute_mark" } // "Every 40th minute"
                14 -> { intervalMinutes = 50; intervalType = "minute_mark" } // "Every 50th minute"
                15 -> { intervalMinutes = 60; intervalType = "top_of_hour" } // "Top of hour"
                else -> { intervalMinutes = 5; intervalType = "normal" }
            }
            currentInterval = intervalMinutes

            // Get the selected sound
            val soundPosition = soundSpinner.selectedItemPosition
            val selectedSound = if (soundPosition < availableSounds.size) {
                availableSounds[soundPosition]
            } else {
                availableSounds[0]
            }

            val intent = Intent(this, SoundService::class.java)
            intent.putExtra("interval", intervalMinutes)
            intent.putExtra("intervalType", intervalType)
            intent.putExtra("isSystemSound", selectedSound.isSystemSound)
            if (selectedSound.isSystemSound) {
                val soundType = when (selectedSound.resourceId) {
                    0 -> RingtoneManager.TYPE_NOTIFICATION
                    1 -> RingtoneManager.TYPE_ALARM
                    2 -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", selectedSound.resourceId)
            }
            
            // Pass minute mark chime settings if using minute_mark or top_of_hour
            if (intervalType == "minute_mark" || intervalType == "top_of_hour") {
                passMinuteMarkChimeSettings(intent)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            isServiceRunning = true
            updateUI()
            val intervalText = when (intervalType) {
                "no_repeat" -> "once (no repeat)"
                "minute_mark" -> "at every ${intervalMinutes}th minute"
                "top_of_hour" -> "at top of every hour"
                else -> "every $intervalMinutes minutes"
            }
            statusText.text = "Service running - Playing $intervalText"
            
            // Calculate and display initial next ring time
            if (intervalType != "no_repeat") {
                updateNextRingTime(intervalMinutes)
            } else {
                nextRingText.text = "Sound will play once"
                nextRingText.visibility = android.view.View.VISIBLE
            }
            
            Toast.makeText(this, "Service started successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun stopSoundService() {
        try {
            val intent = Intent(this, SoundService::class.java)
            stopService(intent)
            isServiceRunning = false
            updateUI()
            statusText.text = "Service stopped"
            nextRingText.visibility = android.view.View.GONE
            nextRingText.text = ""
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping service: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showTimePicker() {
        try {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(12)
                .setMinute(0)
                .setTitleText("Select custom time")
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                scheduleCustomTime()
            }

            picker.show(supportFragmentManager, "timePicker")
        } catch (e: Exception) {
            Toast.makeText(this, "Error showing time picker: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun scheduleCustomTime() {
        try {
            // Get the selected sound
            val soundPosition = soundSpinner.selectedItemPosition
            val selectedSound = if (soundPosition < availableSounds.size) {
                availableSounds[soundPosition]
            } else {
                availableSounds[0]
            }

            val intent = Intent(this, SoundService::class.java)
            intent.action = "ACTION_SET_CUSTOM_SOUND"
            intent.putExtra("hour", selectedHour)
            intent.putExtra("minute", selectedMinute)
            intent.putExtra("isSystemSound", selectedSound.isSystemSound)
            if (selectedSound.isSystemSound) {
                val soundType = when (selectedSound.resourceId) {
                    0 -> RingtoneManager.TYPE_NOTIFICATION
                    1 -> RingtoneManager.TYPE_ALARM
                    2 -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", selectedSound.resourceId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            isServiceRunning = true
            updateUI()
            val timeString = String.format("%02d:%02d", selectedHour, selectedMinute)
            statusText.text = "Custom alarm set for $timeString daily"
            
            // Calculate and display next ring time for custom alarm
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            updateNextRingTimeDisplay(calendar.timeInMillis)
            
            Toast.makeText(this, "Alarm scheduled successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error scheduling alarm: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun testSound() {
        try {
            // Stop any currently playing test sound
            currentTestRingtone?.stop()

            // Get the selected sound
            val soundPosition = soundSpinner.selectedItemPosition
            val selectedSound = if (soundPosition < availableSounds.size) {
                availableSounds[soundPosition]
            } else {
                availableSounds[0]
            }

            if (selectedSound.isSystemSound) {
                // Play system sound
                val soundType = when (selectedSound.resourceId) {
                    0 -> RingtoneManager.TYPE_NOTIFICATION
                    1 -> RingtoneManager.TYPE_ALARM
                    2 -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                val soundUri: Uri = RingtoneManager.getDefaultUri(soundType)
                currentTestRingtone = RingtoneManager.getRingtone(this, soundUri)
            } else {
                // Play custom sound from raw folder
                val soundUri = Uri.parse("android.resource://$packageName/${selectedSound.resourceId}")
                currentTestRingtone = RingtoneManager.getRingtone(this, soundUri)
            }

            if (currentTestRingtone != null) {
                currentTestRingtone?.play()
                Toast.makeText(this, "Playing test sound...", Toast.LENGTH_SHORT).show()

                // Stop the ringtone after 3 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        currentTestRingtone?.stop()
                        currentTestRingtone = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, 3000)
            } else {
                Toast.makeText(this, "Unable to play sound", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing test sound: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
    
    private fun showMinuteMarkChimeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_minute_mark_chime, null)
        
        val spinner10 = dialogView.findViewById<Spinner>(R.id.spinner10thMinute)
        val spinner20 = dialogView.findViewById<Spinner>(R.id.spinner20thMinute)
        val spinner30 = dialogView.findViewById<Spinner>(R.id.spinner30thMinute)
        val spinner40 = dialogView.findViewById<Spinner>(R.id.spinner40thMinute)
        val spinner50 = dialogView.findViewById<Spinner>(R.id.spinner50thMinute)
        val spinnerTop = dialogView.findViewById<Spinner>(R.id.spinnerTopOfHour)
        
        val soundNames = availableSounds.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, soundNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        // Setup all spinners
        listOf(spinner10, spinner20, spinner30, spinner40, spinner50, spinnerTop).forEach { spinner ->
            spinner.adapter = adapter
        }
        
        // Set previously selected sounds if they exist
        minuteMarkChimeSounds[10]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinner10.setSelection(index)
        }
        minuteMarkChimeSounds[20]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinner20.setSelection(index)
        }
        minuteMarkChimeSounds[30]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinner30.setSelection(index)
        }
        minuteMarkChimeSounds[40]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinner40.setSelection(index)
        }
        minuteMarkChimeSounds[50]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinner50.setSelection(index)
        }
        minuteMarkChimeSounds[0]?.let { sound ->
            val index = availableSounds.indexOf(sound)
            if (index >= 0) spinnerTop.setSelection(index)
        }
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Save minute mark chime settings
                minuteMarkChimeSounds[10] = availableSounds[spinner10.selectedItemPosition]
                minuteMarkChimeSounds[20] = availableSounds[spinner20.selectedItemPosition]
                minuteMarkChimeSounds[30] = availableSounds[spinner30.selectedItemPosition]
                minuteMarkChimeSounds[40] = availableSounds[spinner40.selectedItemPosition]
                minuteMarkChimeSounds[50] = availableSounds[spinner50.selectedItemPosition]
                minuteMarkChimeSounds[0] = availableSounds[spinnerTop.selectedItemPosition]
                
                // Save to SharedPreferences
                saveMinuteMarkChimeSettings()
                
                Toast.makeText(this, "Minute mark chime settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveMinuteMarkChimeSettings() {
        val prefs = getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        minuteMarkChimeSounds.forEach { (minute, sound) ->
            editor.putBoolean("${minute}_isSystemSound", sound.isSystemSound)
            editor.putInt("${minute}_resourceId", sound.resourceId)
            editor.putString("${minute}_name", sound.name)
        }
        
        editor.apply()
    }
    
    private fun loadMinuteMarkChimeSettings() {
        val prefs = getSharedPreferences("MinuteMarkChimes", Context.MODE_PRIVATE)
        
        listOf(0, 10, 20, 30, 40, 50).forEach { minute ->
            val isSystemSound = prefs.getBoolean("${minute}_isSystemSound", true)
            val resourceId = prefs.getInt("${minute}_resourceId", 0)
            val name = prefs.getString("${minute}_name", null)
            
            if (name != null) {
                minuteMarkChimeSounds[minute] = SoundInfo(name, resourceId, isSystemSound)
            }
        }
    }
    
    private fun showHourlyChimeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hourly_chime, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.hourlyChimeContainer)
        
        // Create spinners for each hour (0-23)
        for (hour in 0..23) {
            val hourLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            
            val hourLabel = TextView(this).apply {
                text = String.format("%02d:00", hour)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val soundSpinner = Spinner(this).apply {
                val soundNames = availableSounds.map { it.name }.toTypedArray()
                val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, soundNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                this.adapter = adapter
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    2f
                )
                tag = hour
                
                // Set previously selected sound if exists
                val savedSoundResId = hourlyChimeSounds[hour]
                if (savedSoundResId != null) {
                    val index = availableSounds.indexOfFirst { it.resourceId == savedSoundResId }
                    if (index >= 0) setSelection(index)
                }
            }
            
            hourLayout.addView(hourLabel)
            hourLayout.addView(soundSpinner)
            container.addView(hourLayout)
        }
        
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Save hourly chime settings
                for (i in 0 until container.childCount) {
                    val hourLayout = container.getChildAt(i) as LinearLayout
                    val spinner = hourLayout.getChildAt(1) as Spinner
                    val hour = spinner.tag as Int
                    val selectedPos = spinner.selectedItemPosition
                    if (selectedPos < availableSounds.size) {
                        hourlyChimeSounds[hour] = availableSounds[selectedPos].resourceId
                    }
                }
                Toast.makeText(this, "Hourly chime settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUI() {
        // Update button visibility with smooth animations
        if (isServiceRunning) {
            // Service is running - hide start button, show stop button
            animateButtonVisibility(startButton, View.GONE)
            animateButtonVisibility(stopButton, View.VISIBLE)
        } else {
            // Service is stopped - show start button, hide stop button
            animateButtonVisibility(startButton, View.VISIBLE)
            animateButtonVisibility(stopButton, View.GONE)
        }
        
        // Disable/enable controls when service is running
        intervalSpinner.isEnabled = !isServiceRunning
        soundSpinner.isEnabled = !isServiceRunning
        customTimeButton.isEnabled = !isServiceRunning
        minuteMarkChimeButton.isEnabled = !isServiceRunning
        hourlyChimeButton.isEnabled = !isServiceRunning
    }
    
    private fun animateButtonVisibility(button: MaterialButton, visibility: Int) {
        val currentVisibility = button.visibility
        if (currentVisibility == visibility) return
        
        when {
            visibility == View.VISIBLE && currentVisibility != View.VISIBLE -> {
                // Show button with fade-in animation
                button.visibility = View.VISIBLE
                button.alpha = 0f
                button.animate()
                    .alpha(1.0f)
                    .setDuration(250)
                    .setListener(null)
                    .start()
            }
            visibility == View.GONE && currentVisibility == View.VISIBLE -> {
                // Hide button with fade-out animation
                button.animate()
                    .alpha(0.0f)
                    .setDuration(250)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            button.visibility = View.GONE
                            button.alpha = 1f // Reset alpha for next time
                        }
                    })
                    .start()
            }
            else -> {
                // Direct visibility change without animation
                button.visibility = visibility
                button.alpha = if (visibility == View.VISIBLE) 1f else 0f
            }
        }
    }
    
    private fun checkServiceState() {
        isServiceRunning = isServiceCurrentlyRunning(SoundService::class.java)
        updateUI()
    }
    
    private fun isServiceCurrentlyRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (service in runningServices) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun registerNextRingTimeReceiver() {
        nextRingTimeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.soundrepeater.NEXT_RING_TIME") {
                    val nextRingTime = intent.getLongExtra("nextRingTime", -1)
                    if (nextRingTime > 0) {
                        updateNextRingTimeDisplay(nextRingTime)
                    }
                }
            }
        }
        val filter = IntentFilter("com.example.soundrepeater.NEXT_RING_TIME")
        registerReceiver(nextRingTimeReceiver, filter)
    }

    private fun updateNextRingTime(intervalMinutes: Int) {
        val calendar = Calendar.getInstance()
        val triggerTime: Long

        // For 1-minute intervals, calculate next round minute
        if (intervalMinutes == 1) {
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.MINUTE, 1)
            triggerTime = calendar.timeInMillis
        } else {
            // For other intervals, calculate relative to current time
            triggerTime = System.currentTimeMillis() + intervalMinutes * 60 * 1000L
        }

        updateNextRingTimeDisplay(triggerTime)
    }

    private fun updateNextRingTimeDisplay(triggerTime: Long) {
        try {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = triggerTime

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeString = timeFormat.format(calendar.time)
            
            nextRingText.text = "Next ring is at $timeString"
            nextRingText.visibility = android.view.View.VISIBLE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Permission not granted, but we'll handle it when user tries to start service
                // The app will still work with inexact alarms as fallback
            }
        }
    }

    private fun showExactAlarmPermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertDialog.Builder(this)
                .setTitle("Exact Alarm Permission Required")
                .setMessage("This app needs exact alarm permission to schedule sounds accurately. Please grant the permission in system settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Continue Anyway") { _, _ ->
                    // User can continue with inexact alarms
                    startSoundServiceWithFallback()
                }
                .show()
        }
    }

    private fun startSoundServiceWithFallback() {
        // This will use inexact alarms as fallback
        try {
            val intervalPosition = intervalSpinner.selectedItemPosition
            val intervalMinutes: Int
            val intervalType: String
            
            when (intervalPosition) {
                0 -> { intervalMinutes = 0; intervalType = "no_repeat" }
                1 -> { intervalMinutes = 1; intervalType = "normal" }
                2 -> { intervalMinutes = 5; intervalType = "normal" }
                3 -> { intervalMinutes = 10; intervalType = "normal" }
                4 -> { intervalMinutes = 15; intervalType = "normal" }
                5 -> { intervalMinutes = 20; intervalType = "normal" }
                6 -> { intervalMinutes = 30; intervalType = "normal" }
                7 -> { intervalMinutes = 40; intervalType = "normal" }
                8 -> { intervalMinutes = 50; intervalType = "normal" }
                9 -> { intervalMinutes = 60; intervalType = "normal" }
                10 -> { intervalMinutes = 10; intervalType = "minute_mark" }
                11 -> { intervalMinutes = 20; intervalType = "minute_mark" }
                12 -> { intervalMinutes = 30; intervalType = "minute_mark" }
                13 -> { intervalMinutes = 40; intervalType = "minute_mark" }
                14 -> { intervalMinutes = 50; intervalType = "minute_mark" }
                15 -> { intervalMinutes = 60; intervalType = "top_of_hour" }
                else -> { intervalMinutes = 5; intervalType = "normal" }
            }
            currentInterval = intervalMinutes

            val soundPosition = soundSpinner.selectedItemPosition
            val selectedSound = if (soundPosition < availableSounds.size) {
                availableSounds[soundPosition]
            } else {
                availableSounds[0]
            }

            val intent = Intent(this, SoundService::class.java)
            intent.putExtra("interval", intervalMinutes)
            intent.putExtra("intervalType", intervalType)
            intent.putExtra("isSystemSound", selectedSound.isSystemSound)
            if (selectedSound.isSystemSound) {
                val soundType = when (selectedSound.resourceId) {
                    0 -> RingtoneManager.TYPE_NOTIFICATION
                    1 -> RingtoneManager.TYPE_ALARM
                    2 -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                intent.putExtra("soundType", soundType)
            } else {
                intent.putExtra("soundResId", selectedSound.resourceId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            isServiceRunning = true
            updateUI()
            val intervalText = when (intervalType) {
                "no_repeat" -> "once (no repeat)"
                "minute_mark" -> "at every ${intervalMinutes}th minute"
                "top_of_hour" -> "at top of every hour"
                else -> "every $intervalMinutes minutes"
            }
            statusText.text = "Service running - Playing $intervalText (using inexact alarms)"
            if (intervalType != "no_repeat") {
                updateNextRingTime(intervalMinutes)
            } else {
                nextRingText.text = "Sound will play once"
                nextRingText.visibility = android.view.View.VISIBLE
            }
            Toast.makeText(this, "Service started with inexact alarms (may be less accurate)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check service state when activity resumes
        checkServiceState()
        // Re-check permission when returning from settings
        if (isServiceRunning) {
            checkExactAlarmPermission()
        }
    }

    private fun passMinuteMarkChimeSettings(intent: Intent) {
        // Pass minute mark chime settings to the service
        minuteMarkChimeSounds.forEach { (minute, sound) ->
            intent.putExtra("minute${minute}_isSystemSound", sound.isSystemSound)
            intent.putExtra("minute${minute}_resourceId", sound.resourceId)
            if (sound.isSystemSound) {
                val soundType = when (sound.resourceId) {
                    0 -> RingtoneManager.TYPE_NOTIFICATION
                    1 -> RingtoneManager.TYPE_ALARM
                    2 -> RingtoneManager.TYPE_RINGTONE
                    else -> RingtoneManager.TYPE_NOTIFICATION
                }
                intent.putExtra("minute${minute}_soundType", soundType)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any playing test sound when activity is destroyed
        currentTestRingtone?.stop()
        currentTestRingtone = null
        
        // Unregister broadcast receiver
        nextRingTimeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}