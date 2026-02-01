package com.example.arduinousbpoc

import android.content.Context
import android.content.SharedPreferences

class MotorConfigPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("motor_config", Context.MODE_PRIVATE)

    fun getLeftMotor(): Int = prefs.getInt("left_motor", 3)

    fun getRightMotor(): Int = prefs.getInt("right_motor", 4)

    fun save(left: Int, right: Int) {
        prefs.edit()
            .putInt("left_motor", left)
            .putInt("right_motor", right)
            .apply()
    }
}
