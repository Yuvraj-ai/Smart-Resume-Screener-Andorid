package com.yuvraj.resumescreener.data.repository

import com.yuvraj.resumescreener.data.local.BandCount
import com.yuvraj.resumescreener.data.local.DashboardStats
import com.yuvraj.resumescreener.data.local.ScreeningDao
import com.yuvraj.resumescreener.data.local.ScreeningEntity
import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.MatchResult
import com.yuvraj.resumescreener.domain.model.Resume
import com.yuvraj.resumescreener.domain.model.ScreeningOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

enum class CandidateSort { RECENT, SCORE_HIGH }

/** Persists and queries screening results. Replaces `backend/utils/db.py`. */
@Singleton
class ScreeningRepository @Inject constructor(
    private val dao: ScreeningDao,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun save(
        fileName: String,
        outcome: ScreeningOutcome,
        createdAt: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        ScreeningEntity(
            sourceFileName = fileName,
            createdAt = createdAt,
            score = outcome.match.score,
            summary = outcome.match.summary,
            candidateName = outcome.candidate.name.ifBlank { "Unknown candidate" },
            jobTitle = outcome.job.jobTitle.ifBlank { "Unspecified role" },
            skillScore = outcome.match.breakdown.skills,
            experienceScore = outcome.match.breakdown.experience,
            educationScore = outcome.match.breakdown.education,
            candidateDetailsJson = json.encodeToString(Resume.serializer(), outcome.candidate),
            jobDetailsJson = json.encodeToString(JobDescription.serializer(), outcome.job),
            matchAnalysisJson = json.encodeToString(MatchResult.serializer(), outcome.match),
        )
    )

    fun observeAll(sort: CandidateSort = CandidateSort.RECENT): Flow<List<ScreeningEntity>> =
        dao.observeAll().map { rows -> sortRows(rows, sort) }

    fun search(query: String, sort: CandidateSort = CandidateSort.RECENT): Flow<List<ScreeningEntity>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) dao.observeAll() else dao.search(trimmed)
        return source.map { rows -> sortRows(rows, sort) }
    }

    fun observeTop(limit: Int = 3): Flow<List<ScreeningEntity>> = dao.observeTop(limit)

    suspend fun byId(id: Long): ScreeningEntity? = dao.byId(id)

    suspend fun delete(entity: ScreeningEntity) = dao.delete(entity)

    fun observeStats(): Flow<DashboardStats> = combine(
        dao.observeStats(weekStartMillis()),
        flowOfBands(),
    ) { stats, bands ->
        val distribution = IntArray(10)
        bands.forEach { distribution[it.band - 1] = it.count }
        DashboardStats(
            total = stats.total,
            averageScore = stats.avgScore,
            bestScore = stats.maxScore,
            screenedThisWeek = stats.thisWeek,
            distribution = distribution,
        )
    }

    private fun flowOfBands(): Flow<List<BandCount>> =
        kotlinx.coroutines.flow.flow { emit(dao.scoreBands()) }

    private fun sortRows(rows: List<ScreeningEntity>, sort: CandidateSort): List<ScreeningEntity> =
        when (sort) {
            CandidateSort.RECENT -> rows
            CandidateSort.SCORE_HIGH -> rows.sortedWith(
                compareByDescending<ScreeningEntity> { it.score }.thenByDescending { it.createdAt }
            )
        }

    private fun weekStartMillis(): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
}
