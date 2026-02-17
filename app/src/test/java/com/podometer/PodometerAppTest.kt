// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Basic sanity check that the PodometerApp class is instantiable
 * and exists in the expected package.
 */
class PodometerAppTest {

    @Test
    fun `PodometerApp class exists in correct package`() {
        val appClass = PodometerApp::class.java
        assertNotNull(appClass)
        assert(appClass.name == "com.podometer.PodometerApp") {
            "Expected package com.podometer but was ${appClass.name}"
        }
    }

    @Test
    fun `MainActivity class exists in correct package`() {
        val activityClass = MainActivity::class.java
        assertNotNull(activityClass)
        assert(activityClass.name == "com.podometer.MainActivity") {
            "Expected package com.podometer but was ${activityClass.name}"
        }
    }
}
