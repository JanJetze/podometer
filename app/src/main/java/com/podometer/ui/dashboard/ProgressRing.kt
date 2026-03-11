// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.podometer.ui.theme.LocalGoalRingColors
import com.podometer.ui.theme.PodometerTheme

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Computes the overall ring fill fraction in [0f, 1f] based on [steps] relative to [stretchGoal].
 *
 * @param steps       Current step count.
 * @param minimumGoal Minimum daily step goal (unused in fraction but kept for consistent API).
 * @param targetGoal  Target daily step goal (unused in fraction but kept for consistent API).
 * @param stretchGoal Maximum goal defining the 100% ring fill.
 * @return Normalised progress in [0f, 1f].
 */
fun computeRingProgress(
    steps: Int,
    minimumGoal: Int,
    targetGoal: Int,
    stretchGoal: Int,
): Float {
    if (stretchGoal <= 0) return 0f
    return (steps.toFloat() / stretchGoal.toFloat()).coerceIn(0f, 1f)
}

/**
 * Holds the sweep angle (in degrees) for each of the three ring tiers.
 *
 * @property minimumSweep  Degrees swept for the minimum-goal tier arc.
 * @property targetSweep   Degrees swept for the target-goal tier arc.
 * @property stretchSweep  Degrees swept for the stretch-goal tier arc.
 */
data class TierSweepAngles(
    val minimumSweep: Float,
    val targetSweep: Float,
    val stretchSweep: Float,
)

/**
 * Computes how many degrees of the 360° ring each tier should fill given [steps].
 *
 * The full ring represents [stretchGoal] steps. Each tier occupies a proportional segment:
 * - minimum tier: 0 → minimumGoal
 * - target tier:  minimumGoal → targetGoal
 * - stretch tier: targetGoal → stretchGoal
 *
 * If [steps] hasn't reached a tier boundary, that tier's portion is partially or fully empty.
 *
 * @param steps       Current step count.
 * @param minimumGoal Threshold for the minimum tier.
 * @param targetGoal  Threshold for the target tier.
 * @param stretchGoal Maximum goal (defines full ring = 360°).
 * @return [TierSweepAngles] containing the degrees for each filled tier.
 */
fun computeTierSweepAngles(
    steps: Int,
    minimumGoal: Int,
    targetGoal: Int,
    stretchGoal: Int,
): TierSweepAngles {
    if (stretchGoal <= 0) return TierSweepAngles(0f, 0f, 0f)
    val totalSteps = steps.coerceIn(0, stretchGoal).toFloat()
    val stretch = stretchGoal.toFloat()

    // Steps filled in each tier
    val filledMin = totalSteps.coerceAtMost(minimumGoal.toFloat())
    val filledTarget = (totalSteps - minimumGoal).coerceIn(0f, (targetGoal - minimumGoal).toFloat())
    val filledStretch = (totalSteps - targetGoal).coerceIn(0f, (stretchGoal - targetGoal).toFloat())

    return TierSweepAngles(
        minimumSweep = (filledMin / stretch) * 360f,
        targetSweep = (filledTarget / stretch) * 360f,
        stretchSweep = (filledStretch / stretch) * 360f,
    )
}

/**
 * Builds a plain-English accessibility description for the progress ring.
 *
 * Pure-Kotlin function kept separate from the Composable so it can be unit-tested on the JVM.
 *
 * @param steps       Current step count.
 * @param stretchGoal Maximum goal; defines 100% fill.
 * @return Human-readable string suitable for use as a `contentDescription`.
 */
fun progressRingContentDescription(steps: Int, stretchGoal: Int): String {
    val percent = if (stretchGoal > 0) {
        ((steps.toFloat() / stretchGoal.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else {
        0
    }
    return "$steps steps, $percent percent of stretch goal"
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Circular progress ring that shows step progress against three goal tiers.
 *
 * The ring is divided into three coloured arcs:
 * - sage green for the 0 → minimumGoal tier
 * - forest green for the minimumGoal → targetGoal tier
 * - vibrant gold for the targetGoal → stretchGoal tier
 *
 * The center displays the current step count using [MaterialTheme.typography.displaySmall]
 * and a "GOAL" label when the target is met.
 *
 * When [isRestDay] is true, the ring colours are muted (50% alpha) to visually signal
 * that goals are relaxed for the day.
 *
 * Accessibility: the ring carries a `contentDescription` summarising the step count
 * and percentage so TalkBack can read the progress without visuals.
 *
 * @param steps        Current step count for today.
 * @param minimumGoal  Lower tier threshold (sage green fills up to here).
 * @param targetGoal   Middle tier threshold (forest green fills from minimum to here).
 * @param stretchGoal  Upper tier threshold (gold fills from target to here; ring 100% full).
 * @param isRestDay    When true, renders the ring in a muted/dimmed style.
 * @param modifier     Optional [Modifier] applied to the root [Box].
 */
@Composable
fun ProgressRing(
    steps: Int,
    minimumGoal: Int,
    targetGoal: Int,
    stretchGoal: Int,
    isRestDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val ringColors = LocalGoalRingColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val alpha = if (isRestDay) 0.4f else 1f

    val tierAngles = computeTierSweepAngles(steps, minimumGoal, targetGoal, stretchGoal)

    // Spring-based sweep animations for a more natural, physical feel.
    val sweepSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 200f)
    val animatedMin by animateFloatAsState(
        targetValue = tierAngles.minimumSweep,
        animationSpec = sweepSpec,
        label = "ringMinimum",
    )
    val animatedTarget by animateFloatAsState(
        targetValue = tierAngles.targetSweep,
        animationSpec = sweepSpec,
        label = "ringTarget",
    )
    val animatedStretch by animateFloatAsState(
        targetValue = tierAngles.stretchSweep,
        animationSpec = sweepSpec,
        label = "ringStretch",
    )

    // Smooth color transitions when crossing tier boundaries.
    val colorSpec = tween<Color>(durationMillis = RING_ANIMATION_DURATION_MS)
    val minimumColor by animateColorAsState(
        targetValue = ringColors.minimum.copy(alpha = alpha),
        animationSpec = colorSpec,
        label = "colorMinimum",
    )
    val targetColor by animateColorAsState(
        targetValue = ringColors.target.copy(alpha = alpha),
        animationSpec = colorSpec,
        label = "colorTarget",
    )
    val stretchColor by animateColorAsState(
        targetValue = ringColors.stretch.copy(alpha = alpha),
        animationSpec = colorSpec,
        label = "colorStretch",
    )

    // Pulse/scale animation when a tier boundary is first crossed.
    val currentTier = resolveGoalTier(steps, minimumGoal, targetGoal, stretchGoal)
    var previousTier by remember { mutableStateOf(currentTier) }
    val pulseScale = remember { Animatable(1f) }

    LaunchedEffect(currentTier) {
        if (hasNewTierBeenReached(previousTier, currentTier)) {
            // Scale up then settle back to normal — a brief, rewarding pulse.
            pulseScale.animateTo(
                targetValue = 1.06f,
                animationSpec = tween(durationMillis = TIER_PULSE_DURATION_MS / 2),
            )
            pulseScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
            )
        }
        previousTier = currentTier
    }

    val accessibilityText = progressRingContentDescription(steps, stretchGoal)

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .scale(pulseScale.value)
            .semantics { contentDescription = accessibilityText },
        contentAlignment = Alignment.Center,
    ) {
        ProgressRingCanvas(
            trackColor = trackColor,
            minimumColor = minimumColor,
            targetColor = targetColor,
            stretchColor = stretchColor,
            animatedMin = animatedMin,
            animatedTarget = animatedTarget,
            animatedStretch = animatedStretch,
            density = density,
            modifier = Modifier.fillMaxSize(),
        )

        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatStepCount(steps),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            val goalLabel = when {
                steps >= stretchGoal -> "STRETCH"
                steps >= targetGoal -> "TARGET"
                steps >= minimumGoal -> "MINIMUM"
                else -> "goal"
            }
            Text(
                text = goalLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Internal canvas that draws the three-tier progress arcs and the background track.
 *
 * @param trackColor     Colour of the full-circle background track.
 * @param minimumColor   Colour for the minimum-goal tier arc.
 * @param targetColor    Colour for the target-goal tier arc.
 * @param stretchColor   Colour for the stretch-goal tier arc.
 * @param animatedMin    Current sweep angle for the minimum tier (animated).
 * @param animatedTarget Current sweep angle for the target tier (animated).
 * @param animatedStretch Current sweep angle for the stretch tier (animated).
 * @param density        [androidx.compose.ui.unit.Density] for dp-to-px conversion.
 * @param modifier       Applied to the [Canvas].
 */
@Composable
private fun ProgressRingCanvas(
    trackColor: Color,
    minimumColor: Color,
    targetColor: Color,
    stretchColor: Color,
    animatedMin: Float,
    animatedTarget: Float,
    animatedStretch: Float,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
) {
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    Canvas(modifier = modifier) {
        val inset = strokeWidthPx / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            width = size.width - strokeWidthPx,
            height = size.height - strokeWidthPx,
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val trackStroke = Stroke(width = strokeWidthPx)
        val progressStroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)

        // Background track — full circle
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = trackStroke,
        )

        // Minimum tier arc — starts at the top (−90°)
        if (animatedMin > 0f) {
            drawArc(
                color = minimumColor,
                startAngle = -90f,
                sweepAngle = animatedMin,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = progressStroke,
            )
        }

        // Target tier arc — starts where minimum ended
        if (animatedTarget > 0f) {
            drawArc(
                color = targetColor,
                startAngle = -90f + animatedMin,
                sweepAngle = animatedTarget,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = progressStroke,
            )
        }

        // Stretch tier arc — starts where target ended
        if (animatedStretch > 0f) {
            drawArc(
                color = stretchColor,
                startAngle = -90f + animatedMin + animatedTarget,
                sweepAngle = animatedStretch,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = progressStroke,
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: zero steps — empty ring, no tier filled. */
@Preview(showBackground = true, name = "ProgressRing — Zero steps")
@Composable
private fun PreviewProgressRingZero() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 0,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: steps in the minimum tier only. */
@Preview(showBackground = true, name = "ProgressRing — Minimum tier partial")
@Composable
private fun PreviewProgressRingMinimumPartial() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 3_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: minimum goal reached, entering target tier. */
@Preview(showBackground = true, name = "ProgressRing — Minimum reached")
@Composable
private fun PreviewProgressRingMinimumReached() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 5_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: target goal reached, entering stretch tier. */
@Preview(showBackground = true, name = "ProgressRing — Target reached")
@Composable
private fun PreviewProgressRingTargetReached() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 8_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: stretch goal fully reached — ring complete. */
@Preview(showBackground = true, name = "ProgressRing — Stretch complete")
@Composable
private fun PreviewProgressRingStretchComplete() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 12_000,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: rest day — ring is muted/dimmed. */
@Preview(showBackground = true, name = "ProgressRing — Rest day")
@Composable
private fun PreviewProgressRingRestDay() {
    PodometerTheme(dynamicColor = false) {
        ProgressRing(
            steps = 3_500,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = true,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}

/** Preview: dark theme — stretch goal partially filled. */
@Preview(showBackground = true, backgroundColor = 0xFF0E1514, name = "ProgressRing — Dark theme partial")
@Composable
private fun PreviewProgressRingDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        ProgressRing(
            steps = 9_500,
            minimumGoal = 5_000,
            targetGoal = 8_000,
            stretchGoal = 12_000,
            isRestDay = false,
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
        )
    }
}
