package dev.lingmulongtai.cadence.motion

import java.io.StringReader
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SensorCsvTest {
    @Test fun `sample owns values independently of the recycled source buffer`() {
        val original = floatArrayOf(1f, 2f, 3f)
        val sample = SensorSample(1, SensorType.ACCELEROMETER, original)
        original[0] = 99f
        sample.values[1] = 88f
        assertContentEquals(floatArrayOf(1f, 2f, 3f), sample.values)
    }

    @Test fun `all sensor types and quaternion component counts round trip exactly`() {
        SensorType.entries.forEach { type ->
            (type.minValues..type.maxValues).forEach { size ->
                val sample = SensorSample(9_876_543_210_123, type, FloatArray(size) { -0.025f * it })
                val text = buildString { SensorCsv.writeHeader(this); SensorCsv.writeEvent(this, sample) }
                val restored = SensorCsv.read(StringReader(text)).single() as SensorSample
                assertEquals(sample.timestampNs, restored.timestampNs)
                assertEquals(type, restored.type)
                assertContentEquals(sample.values, restored.values)
            }
        }
    }

    @Test fun `optional GPS values round trip without storing coordinates`() {
        listOf(LocationSample(12, 6.5f, 359.5f), LocationSample(13, null, 0f), LocationSample(14, 0f, null))
            .forEach { event ->
                val text = buildString { SensorCsv.writeHeader(this); SensorCsv.writeEvent(this, event) }
                assertEquals(event, SensorCsv.read(StringReader(text)).single())
                assertEquals(9, text.lineSequence().drop(1).first().split(',').size)
            }
    }

    @Test fun `decimal representation does not change with device locale`() {
        val savedLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val text = buildString {
                SensorCsv.writeHeader(this)
                SensorCsv.writeEvent(this, SensorSample(1, SensorType.GYROSCOPE, floatArrayOf(1.25f, 0f, -0.5f)))
            }
            assertTrue(text.contains("1.25"))
            assertEquals(1, SensorCsv.read(StringReader(text)).size)
        } finally { Locale.setDefault(savedLocale) }
    }

    @Test fun `Windows line endings and a BOM can be replayed`() {
        val csv = "\uFEFF${SensorCsv.HEADER}\r\n100,LINEAR_ACCELERATION,0.0,1.0,0.0,,,,\r\n"
        assertEquals(100, SensorCsv.read(StringReader(csv)).single().timestampNs)
    }

    @Test fun `malformed and incomplete rows fail with the source line number`() {
        val invalidRows = listOf(
            "100,ACCELEROMETER,1,2", // interrupted write
            "-1,ACCELEROMETER,1,2,3,,,,",
            "100,UNKNOWN,1,2,3,,,,",
            "100,ACCELEROMETER,NaN,2,3,,,,",
            "100,ACCELEROMETER,1,Infinity,3,,,,",
            "100,GYROSCOPE,1,,3,,,,",
            "100,GYROSCOPE,1,2,3,,,6,90", // cannot conflate event clocks
            "100,LOCATION,1,,,,,6,90",
            "100,LOCATION,,,,,,-1,90",
            "100,LOCATION,,,,,,6,360",
            "100,LOCATION,,,,,,,",
        )
        invalidRows.forEach { row ->
            val error = assertFailsWith<IllegalArgumentException>(row) {
                SensorCsv.read(StringReader("${SensorCsv.HEADER}\n$row\n"))
            }
            assertTrue(error.message.orEmpty().startsWith("CSV line 2:"), row)
        }
    }

    @Test fun `unknown schema cannot be silently accepted`() {
        assertFailsWith<IllegalArgumentException> { SensorCsv.read(StringReader("time,type,x,y,z\n")) }
    }
}
