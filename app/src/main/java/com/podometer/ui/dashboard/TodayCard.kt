// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme
import java.util.Locale

/**
 * Formats a distance in kilometres as a localised string with one decimal place.
 *
 * @param distanceKm Distance in kilometres.
 * @return Formatted string, e.g. "3.8 km".
 */
fun formatDistance(distanceKm: Float): String =
    String.format(Locale.US, "%.1f km", distanceKm)

/**
 * Clamps [progress] to the range [0f, 1f].
 *
 * @param progress Raw progress value (may be outside 0..1).
 * @return Value in [0f, 1f].
 */
fun clampProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * Today card displaying the user's step count, circular progress ring, distance estimate,
 * and percentage completion for the current day.
 *
 * This is a purely presentational composable — it holds no internal state and receives
 * all data via parameters. Wire it to [DashboardViewModel.uiState] in the parent screen.
 *
 * Accessibility: the card has a [contentDescription] combining step count and progress
 * percentage so TalkBack can announce the full summary. The percentage is also rendered
 * as visible text inside the ring so progress is never conveyed by colour alone.
 *
 * @param steps          Total steps taken today.
 * @param goal           Daily step goal.
 * @param progressPercent Steps as a percentage of [goal] (0–100, may exceed 100 on over-achievement).
 * @param distanceKm     Approximate walking distance in kilometres.
 * @param modifier       Optional [Modifier] applied to the root [ElevatedCard].
 */
@Composable
fun TodayCard(
    steps: Int,
    goal: Int,
    progressPercent: Float,
    distanceKm: Float,
    modifier: Modifier = Modifier,
) {
    val cardContentDescription = stringResource(
        R.string.cd_today_steps,
        steps,
        goal,
        progressPercent.toInt(),
    )

    ElevatedCard(
        modifier = modifier
            .semantics { contentDescription = cardContentDescription },
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            ProgressRing(
                progress = clampProgress(progressPercent / 100f),
                progressPercent = progressPercent,
                steps = steps,
                modifier = Modifier.size(200.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = formatDistance(distanceKm),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Circular progress ring rendered on a [Canvas].
 *
 * The ring consists of:
 * - A full-circle **track** arc in [MaterialTheme.colorScheme.surfaceVariant].
 * - A **progress** arc in [MaterialTheme.colorScheme.primary] swept from the top (-90°)
 *   clockwise by `progress * 360°`.
 *
 * Content inside the ring:
 * - Hero step count number using [MaterialTheme.typography.displaySmall] (36sp bold),
 *   satisfying the 32sp+ hero text requirement.
 * - Percentage label using [MaterialTheme.typography.bodyMedium].
 *
 * Progress animates smoothly via [animateFloatAsState] with a 600 ms duration when
 * the [progress] value changes.
 *
 * @param progress       Normalised progress in [0f, 1f].
 * @param progressPercent Raw percentage value used to build the percentage label.
 * @param steps          Step count displayed as the hero number inside the ring.
 * @param modifier       Modifier applied to the outer [Box].
 * @param strokeWidth    Width of both arcs in dp (defaults to 16 dp).
 */
@Composable
private fun ProgressRing(
    progress: Float,
    progressPercent: Float,
    steps: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "progressRing",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = strokeWidthPx / 2f
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val topLeft = Offset(inset, inset)
            val strokeStyle = Stroke(width = strokeWidthPx)
            val progressStrokeStyle = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
            )

            // Background track — full circle
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = strokeStyle,
            )

            // Progress arc — swept clockwise from the top
            if (animatedProgress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = progressStrokeStyle,
                )
            }
        }

        // Content centred inside the ring
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero step count — displaySmall (36sp bold) from PodometerTypography;
            // satisfies the 32sp+ hero text requirement from the Material 3 scale.
            // formatStepCount() adds locale-aware thousands separators (e.g. "10,000").
            Text(
                text = formatStepCount(steps),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${progressPercent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: half-way through the daily goal. */
@Preview(showBackground = true, name = "TodayCard — 50% progress")
@Composable
private fun PreviewTodayCardHalfway() {
    PodometerTheme(dynamicColor = false) {
        TodayCard(
            steps = 5_000,
            goal = 10_000,
            progressPercent = 50f,
            distanceKm = 3.75f,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: goal achieved (100%). */
@Preview(showBackground = true, name = "TodayCard — 100% goal")
@Composable
private fun PreviewTodayCardGoalAchieved() {
    PodometerTheme(dynamicColor = false) {
        TodayCard(
            steps = 10_000,
            goal = 10_000,
            progressPercent = 100f,
            distanceKm = 7.5f,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: just started — zero steps. */
@Preview(showBackground = true, name = "TodayCard — zero steps")
@Composable
private fun PreviewTodayCardZero() {
    PodometerTheme(dynamicColor = false) {
        TodayCard(
            steps = 0,
            goal = 10_000,
            progressPercent = 0f,
            distanceKm = 0f,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: over-achievement (>100%). */
@Preview(showBackground = true, name = "TodayCard — over goal")
@Composable
private fun PreviewTodayCardOverGoal() {
    PodometerTheme(dynamicColor = false) {
        TodayCard(
            steps = 13_500,
            goal = 10_000,
            progressPercent = 135f,
            distanceKm = 10.1f,
            modifier = Modifier.padding(16.dp),
        )
    }
}
