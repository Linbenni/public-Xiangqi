package com.sojourners.tchess.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.sojourners.tchess.game.SoundCue

/**
 * 走子音效（复用桌面 wav）+ 触感反馈（ANDROID_PLAN.md M2）。
 */
class SoundManager(context: Context, private var enabled: Boolean = true) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<SoundCue, Int>()
    private val loaded = HashSet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        load("sound/click.wav", SoundCue.CLICK)
        load("sound/move.wav", SoundCue.MOVE)
        load("sound/capture.wav", SoundCue.CAPTURE)
        load("sound/check.wav", SoundCue.CHECK)
        load("sound/win.wav", SoundCue.WIN)
    }

    private fun load(assetPath: String, cue: SoundCue) {
        try {
            appContext.assets.openFd(assetPath).use { afd ->
                ids[cue] = soundPool.load(afd, 1)
            }
        } catch (_: Exception) {
            // 缺失音效不致命
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun play(cue: SoundCue) {
        if (!enabled) return
        ids[cue]?.let { id ->
            if (id in loaded) {
                soundPool.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
        vibrate(cue)
    }

    /**
     * 轻触感：选子 10ms、吃子/将军/胜利用一次短振。
     */
    private fun vibrate(cue: SoundCue) {
        val vibrator = obtainVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val durationMs = when (cue) {
            SoundCue.CLICK -> 10L
            SoundCue.MOVE -> 18L
            SoundCue.CAPTURE, SoundCue.CHECK -> 30L
            SoundCue.WIN -> 60L
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun obtainVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    fun release() {
        soundPool.release()
    }
}
