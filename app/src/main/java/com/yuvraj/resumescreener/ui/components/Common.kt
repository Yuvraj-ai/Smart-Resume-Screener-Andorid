package com.yuvraj.resumescreener.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuvraj.resumescreener.ui.theme.TabularFamily
import com.yuvraj.resumescreener.ui.theme.scoreColor
import kotlin.math.roundToInt

/** Small all-caps mono label used as a section eyebrow throughout the design. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * The score, as the hero element. The numeral is large monospace so digits
 * align across a list, with the unit subordinate beside it.
 */
@Composable
fun ScoreBadge(
    score: Double,
    modifier: Modifier = Modifier,
    diameter: Int = 56,
) {
    val color = scoreColor(score.toFloat())
    val shown = if (score % 1.0 == 0.0) score.roundToInt().toString()
    else String.format("%.1f", score)
    Box(
        modifier = modifier
            .size(diameter.dp)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            // One announcement instead of a bare "8.8" next to "/10".
            .semantics { contentDescription = "Match score $shown out of 10" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = TabularFamily),
            color = color,
        )
    }
}

/** Headline score with the unit subordinate, for the detail screen. */
@Composable
fun ScoreHeader(score: Double, outOf: Int = 10, modifier: Modifier = Modifier) {
    val color = scoreColor(score.toFloat())
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = String.format("%.1f", score),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = TabularFamily),
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "/ $outOf",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Label, large mono numeral, one line of muted context. */
@Composable
fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TabularFamily),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One criterion's sub-score as a thin weighted bar. [weight] is shown because
 * the rubric's 60/30/10 weighting is otherwise invisible.
 */
@Composable
fun WeightedScoreBar(
    label: String,
    score: Int,
    weight: Int,
    modifier: Modifier = Modifier,
    max: Int = 10,
) {
    val color = scoreColor(score.toFloat())
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label ($weight% weight)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$score/$max",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = TabularFamily),
                color = color,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((score.toFloat() / max).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

/**
 * A single bar in the score histogram. Reads as a bar chart without needing a
 * charting dependency, and each bar is separately labelled so TalkBack can
 * reach the value rather than just a picture.
 */
@Composable
fun DistributionBar(
    band: Int,
    count: Int,
    maxCount: Int,
    isPeak: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = scoreColor(band.toFloat())
    Column(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = "Score $band: $count ${if (count == 1) "candidate" else "candidates"}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (count > 0) count.toString() else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
            color = if (isPeak) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(16.dp)
                .height(72.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (count > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((72 * count.toFloat() / maxCount).coerceAtLeast(6f).dp)
                        .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = band.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = TabularFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Deliberate empty state. The design calls for explaining what the screen is
 * for and offering the next action, never just saying "no data".
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
