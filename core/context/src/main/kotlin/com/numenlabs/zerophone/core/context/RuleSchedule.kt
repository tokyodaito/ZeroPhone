package com.numenlabs.zerophone.core.context

/**
 * Pure scheduling of rule re-evaluation: the next wall-clock moment when a
 * [RuleCondition.TimeWindow] of any rule may flip. The applier schedules a
 * reconcile alarm there so time-based rules take effect without the launcher
 * being resumed.
 *
 * Day-of-week filters are deliberately ignored: a boundary on a day the rule
 * does not apply is a harmless extra wake (reconcile is idempotent).
 */
object RuleSchedule {

    /**
     * Next epoch-ms moment strictly after [nowMillis] when some rule's time
     * window starts or ends, in the zone described by [utcOffsetMillis]
     * (DST included). [offsetAtMillis] resolves the zone offset at an
     * arbitrary instant, so boundaries scheduled across a DST switch still
     * fire at the true local wall-clock time. Null when no rule has a time
     * window.
     */
    fun nextBoundaryMillis(
        rules: List<Rule>,
        nowMillis: Long,
        utcOffsetMillis: Long,
        offsetAtMillis: (Long) -> Long = { utcOffsetMillis }
    ): Long? {
        val boundaries = rules.flatMap { rule ->
            (rule.condition as? RuleCondition.TimeWindow)
                ?.let { listOf(it.startMinuteOfDay, it.endMinuteOfDay) }
                ?: emptyList()
        }
        if (boundaries.isEmpty()) return null

        val localNow = nowMillis + utcOffsetMillis
        val currentDay = Math.floorDiv(localNow, MILLIS_PER_DAY)
        val currentMinuteOfDay = ((localNow % MILLIS_PER_DAY) / MILLIS_PER_MINUTE).toInt()

        var best: Long? = null
        for (minuteOfDay in boundaries) {
            // Same local day when the boundary is still ahead, otherwise tomorrow.
            val day = if (minuteOfDay > currentMinuteOfDay) currentDay else currentDay + 1
            val rough =
                day * MILLIS_PER_DAY + minuteOfDay * MILLIS_PER_MINUTE - utcOffsetMillis
            // Refine once with the offset at the boundary itself — across a
            // DST switch it differs from the offset at scheduling time.
            val refined = rough + utcOffsetMillis - offsetAtMillis(rough)
            val candidate = if (refined > nowMillis) refined else rough
            if (candidate > nowMillis && (best == null || candidate < best)) best = candidate
        }
        return best
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    private const val MILLIS_PER_MINUTE = 60L * 1000L
}
