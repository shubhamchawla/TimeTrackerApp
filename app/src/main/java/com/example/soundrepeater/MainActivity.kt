package com.example.soundrepeater

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var intervalSpinner: Spinner
    private lateinit var toggleButton: Button
    private lateinit var soundSpinner: Spinner
    private lateinit var timePicker: TimePicker
    private lateinit var setCustomSoundButton: Button

    private var isServiceRunning = false
    private val soundMap = mapOf(
        "Sound 1" to R.raw.sound1,
        "Sound 2" to R.raw.sound2,
        "Sound 3" to R.raw.sound3
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        intervalSpinner = findViewById(R.id.intervalSpinner)
        toggleButton = findViewById(R.id.toggleButton)
        soundSpinner = findViewById(R.id.soundSpinner)
        timePicker = findViewById(R.id.timePicker)
        setCustomSoundButton = findViewById(R.id.setCustomSoundButton)

        // Populate interval spinner
        val intervals = arrayOf("5 min", "10 min", "15 min", "20 min", "30 min", "60 min")
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        intervalSpinner.adapter = intervalAdapter

        // Populate sound spinner
        val sounds = soundMap.keys.toTypedArray()
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sounds)
        soundSpinner.adapter = soundAdapter

        toggleButton.setOnClickListener {
            if (isServiceRunning) {
                stopSoundService()
            } else {
                startSoundService()
            }
        }

        setCustomSoundButton.setOnClickListener {
            setCustomSound()
        }
    }

    private fun startSoundService() {
        val intent = Intent(this, SoundService::class.java)
        val selectedInterval = intervalSpinner.selectedItem.toString().split(" ")[0].toInt()
        val selectedSound = soundSpinner.selectedItem.toString()
        val soundResId = soundMap[selectedSound] ?: R.raw.sound1
        intent.putExtra("interval", selectedInterval)
        intent.putExtra("soundResId", soundResId)
        startService(intent)
        toggleButton.text = "Stop"
        isServiceRunning = true
    }

    private fun stopSoundService() {
        val intent = Intent(this, SoundService::class.java)
        stopService(intent)
        toggleButton.text = "Start"
        isServiceRunning = false
    }

    private fun setCustomSound() {
        val intent = Intent(this, SoundService::class.java)
        val hour = timePicker.hour
        val minute = timePicker.minute
        val selectedSound = soundSpinner.selectedItem.toString()
        val soundResId = soundMap[selectedSound] ?: R.raw.sound1
        intent.putExtra("hour", hour)
        intent.putExtra("minute", minute)
        intent.putExtra("soundResId", soundResId)
        intent.action = "ACTION_SET_CUSTOM_SOUND"
        startService(intent)
    }
}
