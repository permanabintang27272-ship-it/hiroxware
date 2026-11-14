package com.hiroxware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View

class TiltEffect(
    context: Context,
    private val views: List<View>,
    private val maxPx: Float
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var smoothX = 0f
    private var smoothY = 0f

    fun start() {
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tx = (event.values[0] / 9.8f).coerceIn(-1f, 1f) * maxPx
        val ty = (event.values[1] / 9.8f).coerceIn(-1f, 1f) * maxPx
        smoothX += (tx - smoothX) * 0.15f
        smoothY += (ty - smoothY) * 0.15f

        // Setiap kartu bergerak dengan kekuatan berbeda, supaya kelihatan melayang
        views.forEachIndexed { i, v ->
            val depth = 0.6f + (i % 4) * 0.2f
            v.translationX = smoothX * depth
            v.translationY = smoothY * depth
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
