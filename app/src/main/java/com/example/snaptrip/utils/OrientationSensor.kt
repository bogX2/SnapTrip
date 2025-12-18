package com.example.snaptrip.utils

    import android.content.Context
    import android.hardware.Sensor
    import android.hardware.SensorEvent
    import android.hardware.SensorEventListener
    import android.hardware.SensorManager
    import kotlinx.coroutines.channels.awaitClose
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.callbackFlow
    import kotlin.math.atan2
    import kotlin.math.cos
    import kotlin.math.sin

    class OrientationSensor(context: Context) {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val sensorData: Flow<Float> = callbackFlow {
            val accelerometerReading = FloatArray(3)
            val magnetometerReading = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    }

                    val rotationMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)

                    // Slide 17: Calcolo Matrice di Rotazione
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        // Azimuth è l'angolo rispetto al Nord (in radianti) -> convertiamo in gradi
                        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        // Normalizziamo a 0-360
                        trySend((azimuth + 360) % 360)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    // Funzione matematica per calcolare l'angolo tra due punti GPS
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)

        return ((Math.toDegrees(theta) + 360) % 360).toFloat()
    }
