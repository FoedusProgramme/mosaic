package com.jakewharton.mosaic.buildsupport

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Adds every Kotlin target Mosaic supports to a multiplatform project. */
@Suppress("unused") // Invoked reflectively by Gradle.
public class MosaicTargetsPlugin : Plugin<Project> {
	override fun apply(target: Project) {
		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			target.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
				kotlin.jvm()

				kotlin.linuxArm64()
				kotlin.linuxX64()

				kotlin.macosArm64()

				kotlin.mingwX64()

				kotlin.applyDefaultHierarchyTemplate()
			}
		}
	}
}
