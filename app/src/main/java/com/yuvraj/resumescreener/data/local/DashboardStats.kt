package com.yuvraj.resumescreener.data.local

/** Aggregates for the dashboard. Computed in SQL, not by loading every row. */
data class DashboardStats(
    val total: Int,
    val averageScore: Double?,
    val bestScore: Double?,
    val screenedThisWeek: Int,
    /** Counts per score band, index 0 = score 1, index 9 = score 10. */
    val distribution: IntArray,
) {
    override fun equals(other: Any?): Boolean =
        other is DashboardStats && total == other.total &&
            averageScore == other.averageScore && bestScore == other.bestScore &&
            screenedThisWeek == other.screenedThisWeek &&
            distribution.contentEquals(other.distribution)

    override fun hashCode(): Int {
        var result = total
        result = 31 * result + (averageScore?.hashCode() ?: 0)
        result = 31 * result + (bestScore?.hashCode() ?: 0)
        result = 31 * result + screenedThisWeek
        result = 31 * result + distribution.contentHashCode()
        return result
    }
}

/** Raw aggregate row, mapped into [DashboardStats] by the repository. */
data class StatsRow(
    val total: Int,
    val avgScore: Double?,
    val maxScore: Double?,
    val thisWeek: Int,
)

/** One point on the score histogram. */
data class BandCount(val band: Int, val count: Int)
