// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import com.podometer.ui.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pure helper: given the onboarding completion flag, returns the appropriate
 * navigation route string.
 *
 * Extracted as a top-level function so it can be unit-tested on the JVM without
 * any Android framework dependencies.
 *
 * @param isOnboardingComplete `true` if the user has already completed onboarding.
 * @return [Screen.Dashboard.route] when onboarding is done, [Screen.Onboarding.route] otherwise.
 */
fun resolveStartDestination(isOnboardingComplete: Boolean): String =
    if (isOnboardingComplete) Screen.Dashboard.route else Screen.Onboarding.route

/**
 * ViewModel for the Onboarding screen and for determining the app start destination
 * in [com.podometer.MainActivity].
 *
 * Exposes:
 * - [startDestination]: a [StateFlow] that resolves to the correct route based on
 *   whether the user has already completed onboarding.
 * - [completeOnboarding]: a function that persists the onboarding-complete flag and
 *   is called after the permission flow finishes.
 *
 * @param preferencesManager DataStore-backed preferences repository.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    /**
     * Emits the navigation route that [com.podometer.MainActivity] should use as
     * the start destination.
     *
     * Starts with `null` (loading) and resolves to either [Screen.Onboarding.route]
     * or [Screen.Dashboard.route] once the DataStore preference is read.
     */
    val startDestination: StateFlow<String?> =
        preferencesManager.isOnboardingComplete()
            .map { complete -> resolveStartDestination(complete) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /**
     * Marks the onboarding flow as complete and persists the flag in DataStore.
     *
     * Call this after the permission launcher callback resolves — regardless of
     * whether permissions were granted or denied — so the onboarding screen is
     * not shown again on the next app launch.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesManager.setOnboardingComplete(true)
        }
    }
}
