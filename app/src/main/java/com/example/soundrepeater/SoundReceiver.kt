package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer

class SoundReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val soundResId = intent?.getIntExtra("soundResId", R.raw.sound1) ?: R.raw.sound1
        val mediaPlayer = MediaPlayer.create(context, soundResId)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener { it.release() }
    }
}
