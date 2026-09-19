package dev.lingmulongtai.cadence.motion

import java.io.File
import java.util.Locale

/** Phase 1 replays raw samples; a real MotionEngine consumer is added only in phase 2. */
fun main(args: Array<String>) {
    require(args.size in 1..2) { "Usage: replay input.csv [ordered-output.csv]" }
    val input = File(args[0])
    require(!input.name.endsWith(".partial", ignoreCase = true)) {
        "Incomplete .partial recordings are not accepted. Collect and stop a new recording normally."
    }
    val output = args.getOrNull(1)?.let(::File)
    require(output?.canonicalFile != input.canonicalFile) { "Output must not overwrite the source recording" }
    val events = input.bufferedReader().use(SensorCsv::read)
    val summary = if (output == null) {
        SensorReplay.replay(events) {}
    } else {
        output.parentFile?.mkdirs()
        output.bufferedWriter().use { writer ->
            SensorCsv.writeHeader(writer)
            SensorReplay.replay(events) { SensorCsv.writeEvent(writer, it) }
        }
    }
    println("Events: ${summary.eventCount}")
    println("Duration: ${String.format(Locale.ROOT, "%.3f", summary.durationNs / 1e9)} s")
    summary.sensorCounts.forEach { (type, count) -> println("$type: $count") }
    println("Raw replay only. Completion/provenance metadata is not verified by this CSV command.")
    println("This is not vehicle-motion or confidence validation.")
}
