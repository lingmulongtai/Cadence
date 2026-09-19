package dev.lingmulongtai.cadence.motion

/** Platform-independent events. Timestamps use Android's monotonic elapsed-realtime clock. */
sealed interface RecordedEvent {
    val timestampNs: Long
}

enum class SensorType(val minValues: Int, val maxValues: Int = minValues) {
    ACCELEROMETER(3),
    GRAVITY(3),
    LINEAR_ACCELERATION(3),
    GYROSCOPE(3),
    GAME_ROTATION_VECTOR(3, 5),
}

/** Acceleration is m/s², angular velocity rad/s, rotation vectors are unmodified quaternions. */
class SensorSample(
    override val timestampNs: Long,
    val type: SensorType,
    values: FloatArray,
) : RecordedEvent {
    // SensorEvent.values is reused by Android; a recording must own its sample.
    private val components = values.copyOf()
    val size: Int get() = components.size
    val values: FloatArray get() = components.copyOf()
    operator fun get(index: Int): Float = components[index]

    init {
        require(timestampNs >= 0) { "Negative timestamp" }
        require(size in type.minValues..type.maxValues) { "Invalid value count for $type" }
        require(components.all(Float::isFinite)) { "Non-finite sensor value" }
    }
}

/** Deliberately excludes latitude, longitude and wall-clock location history. */
data class LocationSample(
    override val timestampNs: Long,
    val speedMps: Float?,
    val bearingDeg: Float?,
) : RecordedEvent {
    init {
        require(timestampNs >= 0) { "Negative timestamp" }
        require(speedMps != null || bearingDeg != null) { "Empty location sample" }
        require(speedMps == null || speedMps.isFinite() && speedMps >= 0f) { "Invalid speed" }
        require(bearingDeg == null || bearingDeg.isFinite() && bearingDeg >= 0f && bearingDeg < 360f) {
            "Invalid bearing"
        }
    }
}
