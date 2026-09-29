package com.yuvraj.resumescreener.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ported from `backend/nodes/parse_resume_node.py::Resume`.
 *
 * Every field is a string in the source and stays a string here. `phone` in
 * particular must not become a number: real phone numbers carry leading zeros,
 * country codes and spaces, and an integer type would reject the whole resume.
 */
@Serializable
data class Resume(
    val name: String = "",
    val email: String = "",
    val githublink: String = "",
    val linkedinlink: String = "",
    val phone: String = "",
    val skills: String = "",
    val education: String = "",
    val experience: String = "",
    val projects: String = "",
)

/**
 * Ported from `backend/nodes/parse_jd_node.py::jobDisc`, renamed to
 * JobDescription because that is what it is. Fields and their string-typed
 * shape are unchanged from the source schema.
 */
@Serializable
data class JobDescription(
    @SerialName("job_title") val jobTitle: String = "",
    @SerialName("skills_reqd") val skillsRequired: String = "",
    @SerialName("experience_reqd") val experienceRequired: String = "",
    @SerialName("edu_reqd") val educationRequired: String = "",
    val responsibilities: String = "",
)

/** Per-criterion sub-scores. Added by decision d7; the source had no breakdown. */
@Serializable
data class ScoreBreakdown(
    val skills: Int = 0,
    val experience: Int = 0,
    val education: Int = 0,
) {
    /**
     * Weighted contribution of each criterion, in score points.
     *
     * The 60/30/10 weights live only in the source prompt, so the UI derives
     * this rather than trusting a model-supplied total.
     */
    fun weightedTotal(): Double =
        skills * SKILL_WEIGHT + experience * EXPERIENCE_WEIGHT + education * EDUCATION_WEIGHT

    companion object {
        const val SKILL_WEIGHT = 0.60
        const val EXPERIENCE_WEIGHT = 0.30
        const val EDUCATION_WEIGHT = 0.10
    }
}

/**
 * Ported from `backend/nodes/match_score_node.py::MatchResult`.
 *
 * `score` and `summary` are unchanged. `breakdown` is new per decision d7.
 */
@Serializable
data class MatchResult(
    val score: Double = 0.0,
    val summary: String = "",
    val breakdown: ScoreBreakdown = ScoreBreakdown(),
)

/**
 * One completed screening: a candidate scored against one job description.
 * Mirrors the document shape the Python app persists, so the two are
 * structurally interchangeable.
 */
@Serializable
data class ScreeningOutcome(
    val candidate: Resume,
    val job: JobDescription,
    val match: MatchResult,
)
