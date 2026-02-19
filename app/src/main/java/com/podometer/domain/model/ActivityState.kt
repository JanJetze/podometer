// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

/**
 * Represents the physical activity state of the user.
 *
 * Used throughout the domain layer. DB entities store activity as plain
 * strings ("WALKING", "CYCLING", "STILL"); use [fromString] to convert.
 */
enum class ActivityState {
    WALKING,
    CYCLING,
    STILL;

    companion object {
        /**
         * Converts a DB string representation to [ActivityState].
         * Case-insensitive. Returns [STILL] for unknown or empty values.
         */
        fun fromString(value: String): ActivityState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STILL
    }
}
