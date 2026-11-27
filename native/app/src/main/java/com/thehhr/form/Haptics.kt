package com.thehhr.form

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * One feel per event, from lightest to strongest: tick (selection), set done, exercise done, PR, workout done,
 * rest over. View haptics follow the system "touch feedback" switch; vibrator patterns carry VibrationAttributes
 * (API 33+) so the user's per-usage intensity settings, including off, apply.
 */
object Haptics {
    private fun vibrator(ctx: Context) = ctx.getSystemService(VibratorManager::class.java).defaultVibrator

    /** Selection: choosing an exercise, a chip, a tab of the timer. */
    fun tick(view: View) { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK) }

    /** Set checked. */
    fun setDone(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun play(ctx: Context, usage: Int, primitives: List<Triple<Int, Float, Int>>, fallback: LongArray) = runCatching {
        val v = vibrator(ctx)
        if (!v.hasVibrator()) return@runCatching
        val effect = if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(*primitives.map { it.first }.distinct().toIntArray())) {
            VibrationEffect.startComposition().apply { primitives.forEach { (p, scale, delay) -> addPrimitive(p, scale, delay) } }.compose()
        } else VibrationEffect.createWaveform(fallback, -1)
        if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(usage)) else @Suppress("DEPRECATION") v.vibrate(effect)
    }

    private val TOUCH = if (Build.VERSION.SDK_INT >= 33) VibrationAttributes.USAGE_TOUCH else 0
    private val ALARM = if (Build.VERSION.SDK_INT >= 33) VibrationAttributes.USAGE_ALARM else 0
    private const val CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
    private const val RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    private const val SLOW_RISE = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
    private const val THUD = VibrationEffect.Composition.PRIMITIVE_THUD

    /** Every set of an exercise done: a firm double click. */
    fun exerciseDone(ctx: Context) = play(ctx, TOUCH, listOf(Triple(CLICK, 0.7f, 0), Triple(CLICK, 1f, 80)), longArrayOf(0, 30, 80, 40))

    /** New personal record: a rising double-tap. */
    fun pr(ctx: Context) = play(ctx, TOUCH, listOf(Triple(RISE, 0.8f, 0), Triple(CLICK, 1f, 60), Triple(CLICK, 1f, 90)), longArrayOf(0, 40, 60, 40, 60, 120))

    /** Workout finished with something logged: slow swell, then a thud. */
    fun workoutDone(ctx: Context) = play(ctx, TOUCH, listOf(Triple(SLOW_RISE, 0.6f, 0), Triple(THUD, 1f, 40), Triple(CLICK, 0.8f, 120)), longArrayOf(0, 120, 80, 60, 120, 40))

    /** Rest over: the only long buzz (alarm usage, so it still comes through on silent-touch phones). */
    fun restEnd(ctx: Context, sound: Boolean) {
        runCatching {
            val v = vibrator(ctx)
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 300), -1)
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(ALARM)) else @Suppress("DEPRECATION") v.vibrate(effect)
        }
        if (sound) runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
        }
    }
}
