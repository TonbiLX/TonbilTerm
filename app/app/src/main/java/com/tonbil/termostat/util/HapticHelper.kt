package com.tonbil.termostat.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    /**
     * Hafif tik -- kadran cevirilirken her 0.5 derece adiminda
     * performHapticFeedback Samsung'da guvenilir degil, Vibrator API kullan
     */
    fun tickFeedback(view: View) {
        val vibrator = getVibrator(view.context)
        vibrator?.vibrate(VibrationEffect.createOneShot(8, 40))
    }

    /**
     * Orta klik -- kadran birakildiginda onay
     */
    fun confirmFeedback(view: View) {
        val vibrator = getVibrator(view.context)
        vibrator?.vibrate(VibrationEffect.createOneShot(25, 150))
    }

    /**
     * Guclu cift vurusu -- role acildi (relay ON)
     */
    fun relayOnFeedback(view: View) {
        val vibrator = getVibrator(view.context)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
        )
    }

    /**
     * Tek saglam vurusu -- role kapandi (relay OFF)
     */
    fun relayOffFeedback(view: View) {
        val vibrator = getVibrator(view.context)
        vibrator?.vibrate(VibrationEffect.createOneShot(80, 200))
    }

    /**
     * Sinir hissi -- min/max limitine ulasildiginda
     */
    fun boundaryFeedback(view: View) {
        val vibrator = getVibrator(view.context)
        vibrator?.vibrate(VibrationEffect.createOneShot(40, 255))
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
