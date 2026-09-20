package dev.lingmulongtai.cadence.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SensorReplayTest {
    @Test fun `replay orders asynchronous streams and preserves ties and actual intervals`() {
        val accel = SensorSample(1_030_000_000, SensorType.ACCELEROMETER, floatArrayOf(0f, 0f, 9.81f))
        val gyro = SensorSample(1_007_000_000, SensorType.GYROSCOPE, floatArrayOf(0f, 0f, 0f))
        val rotation = SensorSample(accel.timestampNs, SensorType.GAME_ROTATION_VECTOR, floatArrayOf(0f, 0f, 0f, 1f))
        val location = LocationSample(1_000_000_000, 6f, 90f)
        val consumed = mutableListOf<RecordedEvent>()
        val summary = SensorReplay.replay(listOf(accel, gyro, rotation, location), consumed::add)
        assertEquals(listOf(1_000_000_000L, 1_007_000_000L, 1_030_000_000L, 1_030_000_000L), consumed.map { it.timestampNs })
        assertSame(accel, consumed[2])
        assertSame(rotation, consumed[3])
        assertEquals(30_000_000, summary.durationNs)
        assertEquals(4, summary.eventCount)
        assertEquals(1, summary.sensorCounts["LOCATION"])
    }

    @Test fun `empty replay has no invented samples or elapsed time`() {
        val summary = SensorReplay.replay(emptyList()) { error("Unexpected event") }
        assertEquals(0, summary.eventCount)
        assertEquals(0L, summary.durationNs)
        assertEquals(emptyMap(), summary.sensorCounts)
    }
}
