package dev.lingmulongtai.cadence.data

import dev.lingmulongtai.cadence.motion.SensorCsv
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordingRepositoryTest {
    @TempDir lateinit var root: File
    private val recordings get() = File(root, "recordings").apply { mkdirs() }
    private val repository get() = RecordingRepository(recordings)

    private fun session(id: String = "sensors-1.csv", count: Long = 2, complete: Boolean = true): File {
        val csv = File(recordings, id)
        csv.writeText("${SensorCsv.HEADER}\n1,ACCELEROMETER,0.0,0.0,9.8,,,,\n2,GYROSCOPE,0.0,0.0,0.0,,,,\n")
        File(recordings, "$id.json").writeText(JSONObject().put("schemaVersion", 1).put("complete", complete)
            .put("eventCount", count).put("dataKind", "unreviewed").put("label", "市街地")
            .put("recordedAtEpochMillis", 1234L).put("durationSeconds", 5).toString())
        return csv
    }

    @Test fun `completed recordings survive repository recreation and partial files stay hidden`() {
        session()
        session("incomplete.csv", complete = false)
        session("unfinished.csv.partial")
        File(recordings, "missing-metadata.csv").writeText("broken")
        File(recordings, "invalid.csv.json").writeText("{")
        val listed = RecordingRepository(recordings).list()
        assertEquals(listOf("sensors-1.csv"), listed.map { it.id })
        assertEquals("市街地", listed.single().label)
        assertEquals(5L, listed.single().durationSeconds)
    }

    @Test fun `archive preserves original filenames and exact CSV and metadata bytes`() {
        val csv = session()
        val json = File(recordings, "${csv.name}.json")
        val csvBytes = csv.readBytes()
        val jsonBytes = json.readBytes()
        val archive = repository.archive(csv.name, File(root, "exports"), "市街地 午後.zip")
        assertEquals("市街地 午後.zip", archive.name)
        ZipFile(archive).use { zip ->
            assertEquals(setOf(csv.name, json.name), zip.entries().asSequence().map { it.name }.toSet())
            assertContentEquals(csvBytes, zip.getInputStream(zip.getEntry(csv.name)).readBytes())
            assertContentEquals(jsonBytes, zip.getInputStream(zip.getEntry(json.name)).readBytes())
        }
        assertContentEquals(csvBytes, csv.readBytes())
        assertContentEquals(jsonBytes, json.readBytes())
    }

    @Test fun `count mismatch and malformed CSV are rejected before creating an export`() {
        val csv = session(count = 3)
        assertFailsWith<IllegalArgumentException> { repository.archive(csv.name, File(root, "exports"), "test") }
        session()
        csv.appendText("broken\n")
        assertFailsWith<IllegalArgumentException> { repository.archive(csv.name, File(root, "exports"), "test") }
        assertTrue(!File(root, "exports").exists())
        assertTrue(csv.exists())
    }

    @Test fun `incomplete missing and escaping sessions cannot be exported`() {
        session(complete = false)
        for (id in listOf("sensors-1.csv", "missing.csv", "../sensors-1.csv", "..\\sensors-1.csv", "sensors-1.csv.partial")) {
            assertFailsWith<IllegalArgumentException> { repository.archive(id, File(root, "exports"), "test") }
        }
    }

    @Test fun `export storage failure leaves originals readable and unchanged`() {
        val csv = session()
        val before = csv.readBytes()
        val unavailable = File(root, "unavailable").apply { writeText("file instead of directory") }
        assertFailsWith<IllegalStateException> { repository.archive(csv.name, unavailable, "test") }
        assertContentEquals(before, csv.readBytes())
        assertEquals(1, repository.list().size)
    }

    @Test fun `older completion metadata remains usable without label or duration`() {
        val csv = session()
        File(recordings, "${csv.name}.json").writeText("{\"schemaVersion\":1,\"complete\":true,\"eventCount\":2}")
        val old = repository.list().single()
        assertEquals("", old.label)
        assertEquals(null, old.durationSeconds)
        assertEquals(csv.lastModified(), old.recordedAtEpochMillis)
    }

    @Test fun `filenames keep Japanese while removing separators and limiting length`() {
        assertEquals("市街地.zip", RecordingRepository.zipName(" 市街地.ZIP "))
        assertEquals("_a_b_c_.zip", RecordingRepository.zipName("/a\\b:c?"))
        assertEquals("Cadence-recording.zip", RecordingRepository.zipName("..."))
        assertEquals(68, RecordingRepository.zipName("あ".repeat(120)).length)
    }
}
