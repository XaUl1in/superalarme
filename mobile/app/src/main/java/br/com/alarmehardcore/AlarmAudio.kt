package br.com.alarmehardcore

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

object AlarmAudio {
    val names = listOf("Clássico", "Aurora", "Órbita")
    val sounds = listOf("builtin:alarm", "builtin:aurora", "builtin:orbit")

    fun open(context: Context, source: String, preview: Boolean = false): MediaPlayer {
        val player = create(context, source, preview)
        try { player.prepare(); return player } catch (error: Exception) { player.release(); throw error }
    }

    // The service calls this on its audio thread, then prepares asynchronously.
    fun create(context: Context, source: String, preview: Boolean = false): MediaPlayer {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(if (preview) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val raw = when (source) {
                "builtin:alarm" -> R.raw.alarm
                "builtin:aurora" -> R.raw.aurora
                "builtin:orbit" -> R.raw.orbit
                "builtin:siren" -> R.raw.siren
                "builtin:zen" -> R.raw.zen
                else -> null
            }
            if (raw != null) context.resources.openRawResourceFd(raw).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            } else player.setDataSource(context, Uri.parse(source))
            player.isLooping = true
            return player
        } catch (error: Exception) { player.release(); throw error }
    }
}
