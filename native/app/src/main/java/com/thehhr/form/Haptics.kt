package com.thehhr.form

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** Distinct feel per event: a crisp tick for a set, a rising double-tap for a PR, a long buzz when rest ends. */
object Haptics {
    private fun vibrator(ctx: Context) = ctx.getSystemService(VibratorManager::class.java).defaultVibrator

    /** Set checked: uses the system's confirm feedback (respects the user's touch-feedback setting). */
    fun setDone(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun pr(ctx: Context) = runCatching {
        val v = vibrator(ctx)
        val effect = if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
                .compose()
        } else VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 120), -1)
        v.vibrate(effect)
    }

    fun restEnd(ctx: Context, sound: Boolean) {
        runCatching { vibrator(ctx).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 300), -1)) }
        if (sound) runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
        }
    }
}
