package dev.lingmulongtai.cadence.motion

data class ReplaySummary(val eventCount: Int, val durationNs: Long, val sensorCounts: Map<String, Int>)

object SensorReplay {
    /** Stable sort: different Android sensor queues may arrive out of timestamp order. No sleeping. */
    fun replay(events: List<RecordedEvent>, consume: (RecordedEvent) -> Unit): ReplaySummary {
        val ordered = events.sortedBy(RecordedEvent::timestampNs)
        ordered.forEach(consume)
        val durationNs = if (ordered.isEmpty()) 0L else ordered.last().timestampNs - ordered.first().timestampNs
        return ReplaySummary(
            ordered.size,
            durationNs,
            ordered.groupingBy { if (it is SensorSample) it.type.name else "LOCATION" }.eachCount(),
        )
    }
}
