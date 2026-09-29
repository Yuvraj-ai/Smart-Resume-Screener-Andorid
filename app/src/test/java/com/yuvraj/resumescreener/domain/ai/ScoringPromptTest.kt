package com.yuvraj.resumescreener.domain.ai

import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.Resume
import com.yuvraj.resumescreener.domain.model.ScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the scoring rubric against drift.
 *
 * The prompt was extracted verbatim from `match_score_node.py` during the
 * migration. These assertions exist because the rubric is product behavior:
 * if someone paraphrases a weight, retunes a band, or drops the no-inference
 * instruction, screening results silently change meaning and nothing else in
 * the build would notice.
 */
class ScoringPromptTest {

    private val prompt = Prompts.score(Resume(name = "Jane Doe"), JobDescription(jobTitle = "Dev"))

    @Test
    fun `rubric weights are intact`() {
        assertTrue("skill weight missing", prompt.contains("**1. Skill Match (60%)**"))
        assertTrue("experience weight missing", prompt.contains("**2. Experience Match (30%)**"))
        assertTrue("education weight missing", prompt.contains("**3. Education Match (10%)**"))
    }

    @Test
    fun `score bands are intact`() {
        assertTrue(prompt.contains("1–3 = Poor fit"))
        assertTrue(prompt.contains("4–6 = Partial fit"))
        assertTrue(prompt.contains("7–8 = Good fit"))
        assertTrue(prompt.contains("9–10 = Excellent fit"))
    }

    @Test
    fun `no-inference instruction is intact`() {
        assertTrue(
            "the model must still be told not to invent data",
            prompt.contains("Do NOT infer, assume, or guess missing data."),
        )
    }

    @Test
    fun `score range instruction is intact`() {
        assertTrue(prompt.contains("score between 1 and 10"))
    }

    @Test
    fun `breakdown is requested for the weighted bars`() {
        assertTrue("d7 breakdown missing from the output contract", prompt.contains("\"breakdown\""))
        assertTrue(prompt.contains("\"skills\""))
        assertTrue(prompt.contains("\"experience\""))
        assertTrue(prompt.contains("\"education\""))
    }

    @Test
    fun `both inputs are substituted and no token is left behind`() {
        // Assert on substituted values, not on JSON spacing, so turning on
        // prettyPrint later cannot fail this for a cosmetic reason.
        assertTrue("resume json missing", prompt.contains("Jane Doe"))
        assertTrue("jd json missing", prompt.contains("Dev"))
        assertTrue("resume json should be an object", prompt.contains("\"name\""))
        assertTrue("jd json should use the source field name", prompt.contains("\"job_title\""))
        assertFalse("unsubstituted token", prompt.contains("__RESUME_JSON__"))
        assertFalse("unsubstituted token", prompt.contains("__JD_JSON__"))
    }

    @Test
    fun `weighted total follows the rubric weights`() {
        val breakdown = ScoreBreakdown(skills = 10, experience = 10, education = 10)
        assertEquals(10.0, breakdown.weightedTotal(), 0.001)

        // 8 skills, 8 experience, 8 education -> 8.0 exactly, since the weights sum to 1.
        assertEquals(8.0, ScoreBreakdown(8, 8, 8).weightedTotal(), 0.001)

        // A perfect skills match must move the total far more than education does.
        val skillsOnly = ScoreBreakdown(skills = 10, experience = 5, education = 5).weightedTotal()
        val educationOnly = ScoreBreakdown(skills = 5, experience = 5, education = 10).weightedTotal()
        assertTrue("skills should outweigh education", skillsOnly > educationOnly)

        // Moving one criterion from 5 to 10 is worth (10-5) * its weight:
        // skills 5*0.6 = 3.0, education 5*0.1 = 0.5, so the gap is 2.5.
        assertEquals(2.5, skillsOnly - educationOnly, 0.001)
    }
}
