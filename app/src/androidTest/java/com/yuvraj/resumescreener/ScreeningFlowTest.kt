package com.yuvraj.resumescreener

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuvraj.resumescreener.data.local.ScreeningDao
import com.yuvraj.resumescreener.data.repository.ScreeningRepository
import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.MatchResult
import com.yuvraj.resumescreener.domain.model.Resume
import com.yuvraj.resumescreener.domain.model.ScoreBreakdown
import com.yuvraj.resumescreener.domain.model.ScreeningOutcome
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real screens against seeded data.
 *
 * The earlier verification walked each screen on the emulator and read
 * screenshots. That proves a screen renders but cannot fail the build when a
 * later change breaks it. These assert behaviour instead.
 */
@RunWith(AndroidJUnit4::class)
class ScreeningFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var repository: ScreeningRepository
    private lateinit var dao: ScreeningDao

    @Before
    fun setUp() {
        // Reached through the production Hilt graph, so the data seeded here is
        // the same data the screens read.
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as ResumeScreenerApp
        val entry = EntryPointAccessors.fromApplication(app, GraphAccess::class.java)
        repository = entry.repository()
        dao = entry.dao()
        runBlocking { dao.deleteAll() }
    }

    private fun seed(vararg rows: Triple<String, String, Pair<Double, ScoreBreakdown>>) =
        runBlocking {
            rows.forEach { (name, job, scoreAndBreakdown) ->
                val (score, breakdown) = scoreAndBreakdown
                repository.save(
                    fileName = "$name.pdf",
                    outcome = ScreeningOutcome(
                        candidate = Resume(
                            name = name,
                            email = "${name.substringBefore(' ').lowercase()}@example.com",
                            phone = "+1 415 555 0142",
                            skills = "Figma, design systems",
                            education = "BDes Interaction Design",
                        ),
                        job = JobDescription(jobTitle = job, skillsRequired = "Figma"),
                        match = MatchResult(
                            score = score,
                            summary = "A $score fit for $job.",
                            breakdown = breakdown,
                        ),
                    ),
                    createdAt = System.currentTimeMillis(),
                )
            }
        }

    private fun rowCount(): Int = runBlocking { dao.observeAll().first().size }

    /** Navigate by tab label and confirm a headline is showing. */
    private fun openSection(label: String, headline: String) {
        composeRule.onAllNodesWithText(label).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(headline).assertIsDisplayed()
    }

    /**
     * Navigate and confirm a [SectionLabel] is showing. Section labels are
     * uppercased for display, so they are matched by tag rather than text.
     */
    private fun openSectionByEyebrow(label: String, eyebrow: String) {
        composeRule.onAllNodesWithText(label).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("section:$eyebrow").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationReachesEverySection() {
        // With no rows the dashboard shows its empty state, which has no
        // section label, so seed before asserting on the populated layout.
        seed(Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("section:Screening overview").assertIsDisplayed()

        openSection("Screen", "New screening")
        openSection("Candidates", "Screened candidates")
        openSectionByEyebrow("Settings", "Model provider")

        // Back to Dashboard. The list kept its previous scroll offset, so the
        // heading is off-screen; scroll it back before asserting.
        composeRule.onNodeWithTag("dashboard:list")
            .performScrollToNode(hasTestTag("section:Screening overview"))
        composeRule.onNodeWithTag("section:Screening overview").assertIsDisplayed()
    }

    @Test
    fun dashboardShowsEmptyStateBeforeAnyScreening() {
        composeRule.onNodeWithText("No candidates screened yet").assertIsDisplayed()
        composeRule.onNodeWithText("Run your first screening").assertIsDisplayed()
    }

    @Test
    fun dashboardAggregatesMatchSeededData() {
        seed(
            Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)),
            Triple("Marcus Chen", "Staff Product Designer", 8.8 to ScoreBreakdown(9, 9, 7)),
            Triple("Mateo Silva", "Staff Visual Systems Lead", 5.9 to ScoreBreakdown(6, 6, 5)),
        )
        composeRule.waitForIdle()

        // Assert each tile's own value. A bare text matcher is ambiguous here:
        // "3" is both the total screened and the this-week count.
        composeRule.onNodeWithTag("stat:Candidates screened").assertTextEquals("3")
        // (9.1 + 8.8 + 5.9) / 3 = 7.933, shown to one decimal.
        composeRule.onNodeWithTag("stat:Average score").assertTextEquals("7.9")
        composeRule.onNodeWithTag("stat:Best score").assertTextEquals("9.1")
        composeRule.onNodeWithTag("stat:This week").assertTextEquals("3")
        // Scroll the un-composed chart into view before asserting on it.
        composeRule.onNodeWithTag("dashboard:list")
            .performScrollToNode(hasTestTag("section:Score distribution"))
        composeRule.onNodeWithTag("section:Score distribution").assertIsDisplayed()
    }

    @Test
    fun candidatesListShowsSeededEntries() {
        seed(
            Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)),
            Triple("Marcus Chen", "Staff Product Designer", 8.8 to ScoreBreakdown(9, 9, 7)),
            Triple("Mateo Silva", "Staff Visual Systems Lead", 5.9 to ScoreBreakdown(6, 6, 5)),
        )
        openSection("Candidates", "Screened candidates")

        composeRule.onNodeWithText("Aria Vance").assertIsDisplayed()
        composeRule.onNodeWithText("Marcus Chen").assertIsDisplayed()
        composeRule.onNodeWithText("Mateo Silva").assertIsDisplayed()
    }

    @Test
    fun candidateDetailShowsWeightedBreakdown() {
        seed(Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)))
        openSection("Candidates", "Screened candidates")
        composeRule.onNodeWithText("Aria Vance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("section:Overall fit score").assertIsDisplayed()
        // The three weights come from ScoreBreakdown and must be legible.
        composeRule.onNodeWithText("Skills match (60% weight)").assertIsDisplayed()
        composeRule.onNodeWithText("Experience depth (30% weight)").assertIsDisplayed()
        composeRule.onNodeWithText("Education (10% weight)").assertIsDisplayed()
    }

    @Test
    fun cancellingDeleteLeavesTheRecordAlone() {
        seed(
            Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)),
            Triple("Mateo Silva", "Staff Visual Systems Lead", 5.9 to ScoreBreakdown(6, 6, 5)),
        )
        openSection("Candidates", "Screened candidates")
        composeRule.onNodeWithText("Aria Vance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Delete screening").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete this screening?").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete this screening?").assertDoesNotExist()
        assertEquals("cancel must not delete", 2, rowCount())
    }

    @Test
    fun confirmingDeleteRemovesTheRecord() {
        seed(
            Triple("Aria Vance", "Staff Product Designer", 9.1 to ScoreBreakdown(9, 9, 8)),
            Triple("Mateo Silva", "Staff Visual Systems Lead", 5.9 to ScoreBreakdown(6, 6, 5)),
        )
        openSection("Candidates", "Screened candidates")
        composeRule.onNodeWithText("Mateo Silva").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Delete screening").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Screening deleted").assertIsDisplayed()
        assertEquals(1, rowCount())
    }

    @Test
    fun screenFlowGatesOnResumesAndJobDescription() {
        openSection("Screen", "New screening")

        // Nothing attached yet, so the run button explains what is missing.
        composeRule.onNodeWithText("Attach resumes to continue").assertIsDisplayed()

        composeRule.onNodeWithText("Paste the full job description here").performTextInput("A backend role")
        composeRule.waitForIdle()

        // The soft keyboard covers the lower part of the screen, so the run
        // button has to be scrolled back into view before it can be asserted on.
        val runButton = composeRule.onNodeWithText("Attach resumes to continue")
        runButton.performScrollTo()
        runButton.assertIsDisplayed()
        // The placeholder is replaced once typing starts, so assert on the
        // character counter to confirm the text actually landed.
        composeRule.onNodeWithText("14 characters").assertIsDisplayed()
        // A job description alone is still not enough: no resumes are attached.
    }
}
