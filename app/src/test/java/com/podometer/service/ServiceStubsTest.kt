// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.Service
import android.content.BroadcastReceiver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that the service stub classes exist in the expected package
 * and extend the correct Android base classes.
 *
 * These stubs are placeholders; full implementation comes in later tasks.
 */
class ServiceStubsTest {

    @Test
    fun `StepTrackingService class exists in service package`() {
        val clazz = StepTrackingService::class.java
        assertNotNull(clazz)
        assertTrue(
            "StepTrackingService must be in com.podometer.service",
            clazz.name == "com.podometer.service.StepTrackingService",
        )
    }

    @Test
    fun `StepTrackingService extends android Service`() {
        val clazz = StepTrackingService::class.java
        assertTrue(
            "StepTrackingService must extend android.app.Service",
            Service::class.java.isAssignableFrom(clazz),
        )
    }

    @Test
    fun `BootReceiver class exists in service package`() {
        val clazz = BootReceiver::class.java
        assertNotNull(clazz)
        assertTrue(
            "BootReceiver must be in com.podometer.service",
            clazz.name == "com.podometer.service.BootReceiver",
        )
    }

    @Test
    fun `BootReceiver extends android BroadcastReceiver`() {
        val clazz = BootReceiver::class.java
        assertTrue(
            "BootReceiver must extend android.content.BroadcastReceiver",
            BroadcastReceiver::class.java.isAssignableFrom(clazz),
        )
    }
}
