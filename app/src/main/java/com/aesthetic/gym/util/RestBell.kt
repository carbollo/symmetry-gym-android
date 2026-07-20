package com.aesthetic.gym.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Boxing-bell used when a rest period ends.
 * The sound is synthesised (struck-metal partials with exponential decay) so the app
 * doesn't need to ship an audio asset.
 */
class RestBell(private val context: Context) {

    private var track: AudioTrack? = null

    private fun buildPcm(): ShortArray {
        val strikes = 3
        val strikeGapSec = 0.24
        val strikeLenSec = 0.60
        val totalSec = strikeGapSec * (strikes - 1) + strikeLenSec
        val total = (SAMPLE_RATE * totalSec).toInt()
        val mix = DoubleArray(total)

        // Inharmonic partials typical of a struck bell.
        val ratios = doubleArrayOf(1.0, 2.76, 5.40, 8.93)
        val gains = doubleArrayOf(1.0, 0.55, 0.35, 0.20)
        val decays = doubleArrayOf(5.5, 7.5, 10.0, 13.0)

        for (s in 0 until strikes) {
            val offset = (SAMPLE_RATE * strikeGapSec * s).toInt()
            val len = (SAMPLE_RATE * strikeLenSec).toInt()
            for (i in 0 until len) {
                val idx = offset + i
                if (idx >= total) break
                val t = i.toDouble() / SAMPLE_RATE
                var sample = 0.0
                for (p in ratios.indices) {
                    sample += gains[p] * exp(-decays[p] * t) *
                        sin(2.0 * PI * F0 * ratios[p] * t)
                }
                // Short attack so it sounds struck, not faded in.
                val attack = if (t < 0.004) t / 0.004 else 1.0
                mix[idx] += sample * attack
            }
        }

        val peak = mix.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0
        val scale = if (peak > 0) 0.82 * Short.MAX_VALUE / peak else 0.0
        return ShortArray(total) { (mix[it] * scale).toInt().toShort() }
    }

    private fun obtainTrack(): AudioTrack {
        track?.let { return it }
        val pcm = buildPcm()
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        created.write(pcm, 0, pcm.size)
        track = created
        return created
    }

    /** Rings the bell and vibrates. Safe to call from any thread. */
    fun ring() {
        try {
            val t = obtainTrack()
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {
            // Audio is a nice-to-have: never crash the workout because of it.
        }
        vibrate()
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            val pattern = longArrayOf(0, 180, 120, 180, 120, 300)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val F0 = 740.0
    }
}
