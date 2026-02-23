// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying that the [ServiceStarter] class exists in the expected package.
 *
 * The actual intent-launching logic in [startTrackingServiceIfPermitted] requires an Android
 * Context and cannot be tested on the JVM. Permission logic is tested in
 * [com.podometer.util.PermissionCheckerTest].
 */
class ServiceStarterTest {

    @Test
    fun `ServiceStarter object exists in service package`() {
        // Verify the top-level function is accessible via its package
        val name = "com.podometer.service.ServiceStarterKt"
        val clazz = Class.forName(name)
        assertNotNull(clazz)
    }
}
