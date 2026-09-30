package com.yuvraj.resumescreener.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreeningDao {

    @Insert
    suspend fun insert(entity: ScreeningEntity): Long

    @Delete
    suspend fun delete(entity: ScreeningEntity)

    /** Used by tests to start from a known-empty database. */
    @Query("DELETE FROM screening_results")
    suspend fun deleteAll()

    @Query("SELECT * FROM screening_results WHERE id = :id")
    suspend fun byId(id: Long): ScreeningEntity?

    @Query("SELECT * FROM screening_results ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScreeningEntity>>

    /**
     * Search matches candidate name or job title. Rows come back newest first;
     * alternate sort orders are applied in the repository rather than with a
     * conditional ORDER BY, which is far easier to reason about and to test.
     */
    @Query(
        """
        SELECT * FROM screening_results
        WHERE candidateName LIKE '%' || :query || '%'
           OR jobTitle LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<ScreeningEntity>>

    @Query(
        """
        SELECT COUNT(*) AS total,
               AVG(score) AS avgScore,
               MAX(score) AS maxScore,
               SUM(CASE WHEN createdAt >= :weekStart THEN 1 ELSE 0 END) AS thisWeek
        FROM screening_results
        """
    )
    fun observeStats(weekStart: Long): Flow<StatsRow>

    @Query(
        """
        SELECT CAST(ROUND(score) AS INTEGER) AS band, COUNT(*) AS count
        FROM screening_results
        WHERE score >= 1 AND score <= 10
        GROUP BY band
        """
    )
    suspend fun scoreBands(): List<BandCount>

    @Query("SELECT * FROM screening_results ORDER BY score DESC, createdAt DESC LIMIT :limit")
    fun observeTop(limit: Int): Flow<List<ScreeningEntity>>
}
