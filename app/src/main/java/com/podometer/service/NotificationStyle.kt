// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

/**
 * Controls the level of detail shown in the persistent foreground notification.
 *
 * - [MINIMAL]: shows only the formatted step count (e.g. "7,432 steps").
 * - [DETAILED]: shows step count, distance, and current activity
 *   (e.g. "7,432 steps · 5.2 km · Walking").
 */
enum class NotificationStyle {
    MINIMAL,
    DETAILED;

    companion object {
        /**
         * Converts a DataStore preference string to the corresponding enum value.
         *
         * Recognised strings: `"minimal"`, `"detailed"` (case-insensitive).
         * Unrecognised values fall back to [MINIMAL].
         */
        fun fromPreference(value: String): NotificationStyle = when (value.lowercase()) {
            "detailed" -> DETAILED
            else -> MINIMAL
        }
    }
}
