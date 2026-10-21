package com.jakewharton.mosaic.buildsupport

import java.util.Locale.US
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds every Kotlin target Mosaic supports to a multiplatform project.
 *
 * When the `mosaic.hostOnly` Gradle property is true, only the JVM and the native target of the
 * current machine are added.
 */
@Suppress("unused") // Invoked reflectively by Gradle.
public class MosaicTargetsPlugin : Plugin<Project> {
	override fun apply(target: Project) {
		val hostOnly = target.providers.gradleProperty("mosaic.hostOnly")
			.map(String::toBoolean)
			.getOrElse(false)
		val host = hostNativeTargetName()

		target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
			target.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
				kotlin.jvm()

				for ((name, create) in nativeTargets) {
					if (!hostOnly || name == host) {
						kotlin.create()
					}
				}

				kotlin.applyDefaultHierarchyTemplate()
			}
		}
	}
}

private val nativeTargets = mapOf<String, KotlinMultiplatformExtension.() -> Unit>(
	"linuxArm64" to { linuxArm64() },
	"linuxX64" to { linuxX64() },
	"macosArm64" to { macosArm64() },
	"mingwX64" to { mingwX64() },
)

private fun hostNativeTargetName(): String? {
	val os = System.getProperty("os.name").lowercase(US)
	val arm = System.getProperty("os.arch").lowercase(US) in setOf("aarch64", "arm64")
	return when {
		"mac" in os -> if (arm) "macosArm64" else null
		"linux" in os -> if (arm) "linuxArm64" else "linuxX64"
		"windows" in os -> if (arm) null else "mingwX64"
		else -> null
	}
}
