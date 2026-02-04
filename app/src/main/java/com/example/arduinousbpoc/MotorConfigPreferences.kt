package com.example.arduinousbpoc

import android.content.Context
import android.content.SharedPreferences

class MotorConfigPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("motor_config", Context.MODE_PRIVATE)

    fun getLeftMotor(): Int = prefs.getInt("left_motor", 3)

    fun getRightMotor(): Int = prefs.getInt("right_motor", 4)

    fun getLeftReversed(): Boolean = prefs.getBoolean("left_reversed", false)

    fun getRightReversed(): Boolean = prefs.getBoolean("right_reversed", false)

    fun save(left: Int, right: Int, leftReversed: Boolean = false, rightReversed: Boolean = false) {
        prefs.edit()
            .putInt("left_motor", left)
            .putInt("right_motor", right)
            .putBoolean("left_reversed", leftReversed)
            .putBoolean("right_reversed", rightReversed)
            .apply()
    }
}
