// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

/**
 * Converts a stride length value from kilometres (DataStore storage format) to centimetres
 * (UI display format).
 *
 * @param km Stride length in kilometres (e.g. 0.00075 for 75 cm).
 * @return Stride length rounded to the nearest centimetre.
 */
fun strideLengthKmToCm(km: Float): Int = (km * 100_000).toInt()

/**
 * Converts a stride length value from centimetres (UI display format) to kilometres
 * (DataStore storage format).
 *
 * @param cm Stride length in centimetres (e.g. 75 for 75 cm).
 * @return Stride length in kilometres (e.g. 0.00075).
 */
fun strideLengthCmToKm(cm: Int): Float = cm / 100_000f

/**
 * Parses and validates a step goal input string entered by the user.
 *
 * A valid step goal is a positive integer in the range [1, 100_000].
 *
 * @param input The raw text input from the user.
 * @return The parsed step goal as an [Int], or `null` if the input is invalid.
 */
fun validateStepGoal(input: String): Int? {
    val trimmed = input.trim()
    val parsed = trimmed.toIntOrNull() ?: return null
    return if (parsed in 1..100_000) parsed else null
}
