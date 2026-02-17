// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.di

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying that Hilt DI module classes exist in the expected package
 * and carry the required annotations.
 *
 * Annotations with BINARY retention (retained in class files but not at runtime)
 * such as @HiltAndroidApp, @AndroidEntryPoint, and @InstallIn are verified
 * indirectly through a successful build rather than by reflection.
 *
 * @dagger.Module has RUNTIME retention so it is verifiable via reflection.
 */
class DiModulesTest {

    @Test
    fun `DatabaseModule class exists in di package`() {
        val clazz = DatabaseModule::class.java
        assertNotNull(clazz)
        assertTrue(
            "DatabaseModule must be in com.podometer.di",
            clazz.name == "com.podometer.di.DatabaseModule",
        )
    }

    @Test
    fun `SensorModule class exists in di package`() {
        val clazz = SensorModule::class.java
        assertNotNull(clazz)
        assertTrue(
            "SensorModule must be in com.podometer.di",
            clazz.name == "com.podometer.di.SensorModule",
        )
    }

    @Test
    fun `RepositoryModule class exists in di package`() {
        val clazz = RepositoryModule::class.java
        assertNotNull(clazz)
        assertTrue(
            "RepositoryModule must be in com.podometer.di",
            clazz.name == "com.podometer.di.RepositoryModule",
        )
    }

    @Test
    fun `DatabaseModule is annotated with @Module`() {
        val annotations = DatabaseModule::class.java.annotations.map { it.annotationClass.simpleName }
        assertTrue(
            "DatabaseModule must have @Module annotation, found: $annotations",
            annotations.contains("Module"),
        )
    }

    @Test
    fun `SensorModule is annotated with @Module`() {
        val annotations = SensorModule::class.java.annotations.map { it.annotationClass.simpleName }
        assertTrue(
            "SensorModule must have @Module annotation, found: $annotations",
            annotations.contains("Module"),
        )
    }

    @Test
    fun `RepositoryModule is annotated with @Module`() {
        val annotations = RepositoryModule::class.java.annotations.map { it.annotationClass.simpleName }
        assertTrue(
            "RepositoryModule must have @Module annotation, found: $annotations",
            annotations.contains("Module"),
        )
    }
}
