// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.ui.theme.PodometerTheme
import java.text.NumberFormat
import java.util.Locale

// ─── Data model ───────────────────────────────────────────────────────────────

/**
 * Aggregated statistics for display in the [StatsCards] 2×2 grid.
 *
 * @property averageSteps    Daily average step count for the period.
 * @property bestDaySteps    Highest single-day step count in the period.
 * @property bestDayDate     Human-readable date of the best day (e.g., "Mar 3").
 * @property totalDistanceKm Total distance walked in kilometres for the period.
 * @property achievementRate Fraction of days where steps met the target goal, in [0f, 1f].
 */
data class TrendsStats(
    val averageSteps: Int,
    val bestDaySteps: Int,
    val bestDayDate: String,
    val totalDistanceKm: Double,
    val achievementRate: Float,
)

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * 2×2 grid of Material 3 [ElevatedCard]s showing key trends statistics.
 *
 * Cards:
 * 1. **Average steps** — daily average for the current period.
 * 2. **Best day** — highest step count with the date.
 * 3. **Total distance** — sum of distance for the period in km (1 decimal place).
 * 4. **Achievement rate** — percentage of days meeting the target goal.
 *
 * Each card shows an icon, a large formatted value, and a small descriptive label.
 *
 * This is a pure presentational composable — it holds no ViewModel references.
 *
 * @param stats    The [TrendsStats] data to display.
 * @param modifier Optional [Modifier] applied to the root layout.
 */
@Composable
fun StatsCards(
    stats: TrendsStats,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                iconDescription = "Average steps icon",
                value = formatStepCount(stats.averageSteps),
                label = "Avg steps / day",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.EmojiEvents,
                iconDescription = "Best day icon",
                value = formatStepCount(stats.bestDaySteps),
                label = if (stats.bestDayDate.isNotEmpty()) "Best day (${stats.bestDayDate})" else "Best day",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                icon = Icons.Filled.Route,
                iconDescription = "Total distance icon",
                value = formatTrendsDistance(stats.totalDistanceKm),
                label = "Total distance",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Filled.Star,
                iconDescription = "Achievement rate icon",
                value = formatTrendsAchievementRate(stats.achievementRate),
                label = "Goal days",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single stat [ElevatedCard] showing an icon, a large value, and a label.
 *
 * @param icon             Icon to display at the top of the card.
 * @param iconDescription  Accessibility content description for the icon.
 * @param value            Formatted value to display prominently.
 * @param label            Short descriptive label shown below the value.
 * @param modifier         Applied to the [ElevatedCard].
 */
@Composable
private fun StatCard(
    icon: ImageVector,
    iconDescription: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Format helpers (local, delegates to TrendsHelpers) ──────────────────────

private fun formatStepCount(steps: Int): String {
    val format = NumberFormat.getIntegerInstance(Locale.getDefault())
    format.isGroupingUsed = true
    return format.format(steps)
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: stats with realistic values. */
@Preview(showBackground = true, name = "StatsCards — Typical week")
@Composable
private fun PreviewStatsCardsTypical() {
    PodometerTheme(dynamicColor = false) {
        StatsCards(
            stats = TrendsStats(
                averageSteps = 9_350,
                bestDaySteps = 14_200,
                bestDayDate = "Mar 5",
                totalDistanceKm = 65.4,
                achievementRate = 0.71f,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: all goals met. */
@Preview(showBackground = true, name = "StatsCards — All goals met")
@Composable
private fun PreviewStatsCardsAllMet() {
    PodometerTheme(dynamicColor = false) {
        StatsCards(
            stats = TrendsStats(
                averageSteps = 12_100,
                bestDaySteps = 18_500,
                bestDayDate = "Mar 12",
                totalDistanceKm = 84.7,
                achievementRate = 1.0f,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: no activity — all zeros. */
@Preview(showBackground = true, name = "StatsCards — No activity")
@Composable
private fun PreviewStatsCardsNoActivity() {
    PodometerTheme(dynamicColor = false) {
        StatsCards(
            stats = TrendsStats(
                averageSteps = 0,
                bestDaySteps = 0,
                bestDayDate = "",
                totalDistanceKm = 0.0,
                achievementRate = 0.0f,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: monthly stats with large distances. */
@Preview(showBackground = true, name = "StatsCards — Monthly stats")
@Composable
private fun PreviewStatsCardsMonthly() {
    PodometerTheme(dynamicColor = false) {
        StatsCards(
            stats = TrendsStats(
                averageSteps = 10_450,
                bestDaySteps = 21_300,
                bestDayDate = "Mar 22",
                totalDistanceKm = 323.9,
                achievementRate = 0.84f,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
