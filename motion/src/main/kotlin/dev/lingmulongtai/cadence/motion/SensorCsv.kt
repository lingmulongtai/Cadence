package dev.lingmulongtai.cadence.motion

import java.io.Reader

/** Version 1: fixed numeric columns, no coordinates, locale-independent decimal points. */
object SensorCsv {
    const val HEADER = "timestampNs,sensorType,v0,v1,v2,v3,v4,speedMps,bearingDeg"

    fun writeHeader(output: Appendable) {
        output.append(HEADER).append('\n')
    }

    fun writeEvent(output: Appendable, event: RecordedEvent) {
        output.append(event.timestampNs.toString()).append(',')
        when (event) {
            is SensorSample -> {
                output.append(event.type.name)
                repeat(5) { index ->
                    output.append(',')
                    if (index < event.size) output.append(event[index].toString())
                }
                output.append(",,")
            }
            is LocationSample -> {
                output.append("LOCATION,,,,,,")
                event.speedMps?.let { output.append(it.toString()) }
                output.append(',')
                event.bearingDeg?.let { output.append(it.toString()) }
            }
        }
        output.append('\n')
    }

    /** The caller owns [input]. Reject corrupt rows instead of silently passing incomplete evidence. */
    fun read(input: Reader): List<RecordedEvent> {
        return buildList { forEach(input) { add(it) } }
    }

    /** Validate large exports without retaining a whole recording in memory. The caller owns [input]. */
    fun forEach(input: Reader, consume: (RecordedEvent) -> Unit): Long {
        val lines = input.buffered().lineSequence().iterator()
        require(lines.hasNext() && lines.next().removePrefix("\uFEFF") == HEADER) { "Unsupported CSV header" }
        var count = 0L
        while (lines.hasNext()) {
            val line = lines.next()
            val event = try {
                parseRow(line)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("CSV line ${count + 2}: ${error.message}", error)
            }
            consume(event)
            count++
        }
        return count
    }

    private fun parseRow(line: String): RecordedEvent {
        val cells = line.split(',')
        require(cells.size == 9) { "Expected 9 columns, got ${cells.size}" }
        val timestampNs = cells[0].toLong()
        if (cells[1] == "LOCATION") {
            require(cells.subList(2, 7).all(String::isEmpty)) { "Location row contains sensor values" }
            return LocationSample(
                timestampNs,
                cells[7].takeIf(String::isNotEmpty)?.toFloat(),
                cells[8].takeIf(String::isNotEmpty)?.toFloat(),
            )
        }
        require(cells[7].isEmpty() && cells[8].isEmpty()) { "Sensor row contains location values" }
        val values = cells.subList(2, 7)
        val count = values.indexOfFirst(String::isEmpty).let { if (it < 0) 5 else it }
        require(values.drop(count).all(String::isEmpty)) { "Non-contiguous sensor values" }
        return SensorSample(timestampNs, SensorType.valueOf(cells[1]), values.take(count).map(String::toFloat).toFloatArray())
    }
}
