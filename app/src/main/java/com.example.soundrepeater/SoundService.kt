package com.example.soundrepeater

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Calendar

class SoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var interval: Int = 5 // Default interval in minutes
    private var soundResId: Int = R.raw.sound1

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SET_CUSTOM_SOUND") {
            val hour = intent.getIntExtra("hour", -1)
            val minute = intent.getIntExtra("minute", -1)
            val soundResId = intent.getIntExtra("soundResId", R.raw.sound1)
            if (hour != -1 && minute != -1) {
                scheduleRecurringSound(hour, minute, soundResId)
            }
        } else {
            interval = intent?.getIntExtra("interval", 5) ?: 5
            soundResId = intent?.getIntExtra("soundResId", R.raw.sound1) ?: R.raw.sound1
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, "sound_service_channel")
                .setContentTitle("Sound Repeater")
                .setContentText("Service is running...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
            startForeground(1, notification)
            scheduleIntervalSound()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        cancelAlarms()
    }

    private fun playSound() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(this, soundResId)
        mediaPlayer?.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "sound_service_channel",
                "Sound Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun scheduleIntervalSound() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SoundReceiver::class.java)
        intent.putExtra("soundResId", soundResId)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval * 60 * 1000,
            (interval * 60 * 1000).toLong(),
            pendingIntent
        )
    }

    private fun scheduleRecurringSound(hour: Int, minute: Int, soundResId: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SoundReceiver::class.java)
        intent.putExtra("soundResId", soundResId)
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SoundReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.cancel(pendingIntent)
        val customSoundIntent = Intent(this, SoundReceiver::class.java)
        val customSoundPendingIntent = PendingIntent.getBroadcast(this, 1, customSoundIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.cancel(customSoundPendingIntent)
    }
}
