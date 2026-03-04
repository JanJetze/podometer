// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme

/**
 * Row with previous/next arrows around a centred date label.
 *
 * @param dateLabel       The formatted date text to display (e.g. "Monday, Mar 3").
 * @param isToday         When true, "Today" is appended to the label.
 * @param isNextEnabled   When false, the forward arrow is disabled (at today).
 * @param onPrevious      Callback invoked when the previous arrow is tapped.
 * @param onNext          Callback invoked when the next arrow is tapped.
 * @param onDateClick     Callback invoked when the date label text is tapped (opens picker).
 * @param modifier        Optional [Modifier] applied to the root [Row].
 */
@Composable
fun DateNavigationRow(
    dateLabel: String,
    isToday: Boolean,
    isNextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.activities_previous_day),
            )
        }

        Text(
            text = if (isToday) "$dateLabel (${stringResource(R.string.activities_today)})" else dateLabel,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDateClick)
                .padding(vertical = 8.dp),
        )

        IconButton(
            onClick = onNext,
            enabled = isNextEnabled,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.activities_next_day),
            )
        }
    }
}

@Preview(showBackground = true, name = "DateNavigationRow — Today")
@Composable
private fun PreviewDateNavigationRowToday() {
    PodometerTheme {
        DateNavigationRow(
            dateLabel = "Monday, Mar 3",
            isToday = true,
            isNextEnabled = false,
            onPrevious = {},
            onNext = {},
            onDateClick = {},
        )
    }
}

@Preview(showBackground = true, name = "DateNavigationRow — Past Day")
@Composable
private fun PreviewDateNavigationRowPast() {
    PodometerTheme {
        DateNavigationRow(
            dateLabel = "Sunday, Mar 2",
            isToday = false,
            isNextEnabled = true,
            onPrevious = {},
            onNext = {},
            onDateClick = {},
        )
    }
}
