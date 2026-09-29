package com.yuvraj.resumescreener.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One candidate scored against one job description.
 *
 * The three `*Json` columns hold the full document, mirroring the JSON-in-one-
 * column shape the Python app persists, so nothing is lost if a field is added
 * later. The fields the UI queries and sorts on are additionally promoted to
 * real columns so search and aggregation stay indexed in SQL.
 */
@Entity(
    tableName = "screening_results",
    indices = [Index("candidateName"), Index("jobTitle"), Index("createdAt")],
)
data class ScreeningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceFileName: String,
    val createdAt: Long,
    val score: Double,
    val summary: String,
    val candidateName: String,
    val jobTitle: String,
    /** Nullable so rows written before decision d7 stay valid. */
    val skillScore: Int? = null,
    val experienceScore: Int? = null,
    val educationScore: Int? = null,
    @ColumnInfo(name = "candidateDetailsJson") val candidateDetailsJson: String,
    @ColumnInfo(name = "jobDetailsJson") val jobDetailsJson: String,
    @ColumnInfo(name = "matchAnalysisJson") val matchAnalysisJson: String,
)
