package dev.lingmulongtai.cadence.motion

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReplayMainTest {
    @TempDir lateinit var directory: Path

    @Test fun `a valid prefix of an interrupted recording is rejected`() {
        val partial = directory.resolve("sensors-test.csv.partial")
        partial.writeText("${SensorCsv.HEADER}\n100,ACCELEROMETER,1,2,3,,,,\n")
        val error = assertFailsWith<IllegalArgumentException> { main(arrayOf(partial.toString())) }
        assertTrue(error.message.orEmpty().contains("Incomplete"))
    }

    @Test fun `replay cannot overwrite the original even through a relative alias`() {
        val input = directory.resolve("source.csv")
        val original = "${SensorCsv.HEADER}\n100,ACCELEROMETER,1,2,3,,,,\n"
        input.writeText(original)
        val alias = directory.resolve(".").resolve("source.csv")
        assertFailsWith<IllegalArgumentException> { main(arrayOf(input.toString(), alias.toString())) }
        assertEquals(original, input.readText())
    }
}
