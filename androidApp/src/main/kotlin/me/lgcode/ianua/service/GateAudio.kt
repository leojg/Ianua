package me.lgcode.ianua.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Silences the gated app while the gate is up.
 *
 * First, take transient audio focus: well-behaved players pause on transient loss and resume
 * when it is abandoned. If something is still playing shortly after (the Shorts player
 * ignored focus), mute the media stream until the gate closes. A media PAUSE key was tried
 * first and did not reach the Shorts player; it could also hit an unrelated media app.
 */
class GateAudio(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setOnAudioFocusChangeListener { }
        .build()
    private val muteIfStillPlaying = Runnable {
        if (audio.isMusicActive && !muted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            muted = true
        }
    }
    private var muted = false

    fun silence() {
        audio.requestAudioFocus(focusRequest)
        handler.postDelayed(muteIfStillPlaying, MUTE_CHECK_DELAY_MS)
    }

    /** Safe to call repeatedly, and must be called on every path that removes the gate. */
    fun restore() {
        handler.removeCallbacks(muteIfStillPlaying)
        if (muted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            muted = false
        }
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val MUTE_CHECK_DELAY_MS = 400L
    }
}
