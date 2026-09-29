package com.yuvraj.resumescreener.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four main sections, per decision d4.
 *
 * Stitch's Settings design proposed Candidates / Rubric / Analytics / Settings.
 * That was not adopted: the rubric breakdown belongs inside Candidate Detail,
 * and the analytics belong on the Dashboard, which is where the design's
 * stat tiles and score distribution already live. See design/HANDOFF.md.
 */
enum class Section(val route: String, val label: String, val outlined: ImageVector, val filled: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.Dashboard, Icons.Rounded.Dashboard),
    SCREEN("screen", "Screen", Icons.Outlined.Description, Icons.Rounded.Description),
    CANDIDATES("candidates", "Candidates", Icons.Outlined.People, Icons.Rounded.People),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Pushed from a list rather than a tab. */
object Routes {
    const val CANDIDATE_DETAIL = "candidate/{id}"
    fun candidateDetail(id: Long) = "candidate/$id"
}
