package dev.lingmulongtai.cadence.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lingmulongtai.cadence.motion.LocationSample
import dev.lingmulongtai.cadence.motion.RecordedEvent
import dev.lingmulongtai.cadence.motion.SensorSample
import dev.lingmulongtai.cadence.motion.SensorType
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class SensorSourceInfo(val type: String, val name: String, val minDelayUs: Int, val requestedPeriodUs: Int)

class SensorSubscription internal constructor(
    val sensors: List<SensorSourceInfo>,
    val gpsStatus: String,
    private val release: () -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/** Owned by a service. Activities must never instantiate or subscribe to this source. */
class AndroidSensorSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)

    fun startRecording(
        highPrecision: Boolean,
        includeGps: Boolean,
        emit: (RecordedEvent) -> Unit,
        onError: (Exception) -> Unit,
    ): SensorSubscription {
        val kinds = mapOf(
            Sensor.TYPE_ACCELEROMETER to SensorType.ACCELEROMETER,
            Sensor.TYPE_GRAVITY to SensorType.GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION to SensorType.LINEAR_ACCELERATION,
            Sensor.TYPE_GYROSCOPE to SensorType.GYROSCOPE,
            // A magnetic rotation vector drifts inside metal vehicles. Never subscribe to it.
            Sensor.TYPE_GAME_ROTATION_VECTOR to SensorType.GAME_ROTATION_VECTOR,
        )
        val sensors = kinds.keys.mapNotNull(manager::getDefaultSensor)
        require(sensors.any { it.type == Sensor.TYPE_ACCELEROMETER || it.type == Sensor.TYPE_LINEAR_ACCELERATION }) {
            "No acceleration sensor is available"
        }
        val thread = HandlerThread("cadence-sensors").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val type = kinds[event.sensor.type] ?: return
                try { emit(SensorSample(event.timestamp, type, event.values)) }
                catch (error: Exception) { onError(error) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.hasSpeed() && !location.hasBearing()) return
                try {
                    emit(LocationSample(
                        location.elapsedRealtimeNanos,
                        location.speed.takeIf { location.hasSpeed() },
                        location.bearing.takeIf { location.hasBearing() },
                    ))
                } catch (error: Exception) { onError(error) }
            }
            @Deprecated("Required for Android 8 compatibility")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        var gpsRegistered = false
        val release = {
            manager.unregisterListener(listener)
            try { if (gpsRegistered) locations.removeUpdates(locationListener) }
            finally { thread.quitSafely() }
            Unit
        }
        try {
            val registered = sensors.mapNotNull { sensor ->
                // Honor slower hardware; never request a period shorter than 10,000 us (100 Hz).
                val desiredUs = if (highPrecision) 10_000 else 20_000
                val periodUs = maxOf(desiredUs, sensor.minDelay)
                val request = if (!highPrecision && periodUs == 20_000) SensorManager.SENSOR_DELAY_GAME else periodUs
                if (manager.registerListener(listener, sensor, request, 0, Handler(thread.looper))) {
                    SensorSourceInfo(kinds.getValue(sensor.type).name, sensor.name, sensor.minDelay, periodUs)
                } else null
            }
            check(registered.any { it.type == "ACCELEROMETER" || it.type == "LINEAR_ACCELERATION" }) {
                "The device refused acceleration sensor registration"
            }
            val gpsStatus = when {
                !includeGps -> "off"
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                    throw SecurityException("Precise location permission is required for optional GPS recording")
                !locations.isProviderEnabled(LocationManager.GPS_PROVIDER) -> "provider_disabled"
                else -> {
                    // GPS only: no network provider or latitude/longitude is recorded.
                    locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, locationListener, thread.looper)
                    gpsRegistered = true
                    "requested_no_fix_yet"
                }
            }
            return SensorSubscription(registered, gpsStatus, release)
        } catch (error: Exception) {
            release()
            throw error
        }
    }
}
