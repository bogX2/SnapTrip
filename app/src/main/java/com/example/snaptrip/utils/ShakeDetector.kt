package com.example.snaptrip.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

// Modifica 1: Aggiunto 'context' al costruttore
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {

    // Modifica 2: Inizializzazione interna del SensorManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeTime: Long = 0
    // Manteniamo i tuoi valori di soglia che funzionavano bene
    private val SHAKE_THRESHOLD_GRAVITY = 1.4F
    private val SHAKE_SLOP_TIME_MS = 1000

    // Modifica 3: Metodo start() chiamato dal ViewModel
    fun start() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("ShakeDetector", "Sensor listener REGISTERED")
        } ?: Log.e("ShakeDetector", "Accelerometer not found!")
    }

    // Modifica 4: Metodo stop() chiamato dal ViewModel
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d("ShakeDetector", "Sensor listener UNREGISTERED")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Normalizziamo sulla gravità terrestre
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // Calcoliamo la forza g totale
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            // LOG DI DEBUG
            if (gForce > 1.3) {
                // Log.d("ShakeDetector", "Force read: $gForce")
            }

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()

                if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                lastShakeTime = now
                Log.d("ShakeDetector", ">>> SHAKE DETECTED! <<<")

                // Trigger dell'azione
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Non serve
    }
}