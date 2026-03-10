// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

/**
 * Represents a validated set of three-tier step goals.
 *
 * @param minimum The minimum (lower-tier) step goal.
 * @param target The target (middle-tier) step goal.
 * @param stretch The stretch (upper-tier) step goal.
 */
data class GoalTiers(val minimum: Int, val target: Int, val stretch: Int)

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

/**
 * Parses and validates the three goal tier input strings.
 *
 * All three values must be positive integers in the range [1, 100_000] and
 * must satisfy the strict ordering: minimum < target < stretch.
 *
 * @param minimumInput Raw text for the minimum (lower-tier) goal.
 * @param targetInput Raw text for the target (middle-tier) goal.
 * @param stretchInput Raw text for the stretch (upper-tier) goal.
 * @return A [GoalTiers] if all inputs are valid and ordered, or `null` otherwise.
 */
fun validateGoalTiers(minimumInput: String, targetInput: String, stretchInput: String): GoalTiers? {
    val minimum = validateStepGoal(minimumInput) ?: return null
    val target = validateStepGoal(targetInput) ?: return null
    val stretch = validateStepGoal(stretchInput) ?: return null
    return if (minimum < target && target < stretch) GoalTiers(minimum, target, stretch) else null
}
