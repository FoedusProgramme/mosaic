package com.jakewharton.mosaic.buildsupport

import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Publishing, API compatibility tracking, and documentation for a library module. */
@Suppress("unused") // Invoked reflectively by Gradle.
public class MosaicPublishPlugin : Plugin<Project> {
	@OptIn(ExperimentalBCVApi::class) // klib validation.
	override fun apply(target: Project) {
		target.pluginManager.apply("com.vanniktech.maven.publish")
		target.pluginManager.apply("org.jetbrains.kotlinx.binary-compatibility-validator")
		target.pluginManager.apply("org.jetbrains.dokka")

		// The klib ABI dump lists every target, so it cannot be checked when only building the host.
		val hostOnly = target.providers.gradleProperty("mosaic.hostOnly")
			.map(String::toBoolean)
			.getOrElse(false)

		target.extensions.configure(ApiValidationExtension::class.java) { apiValidation ->
			apiValidation.klib.enabled = !hostOnly

			// We run API checks on a Mac where all possible Kotlin targets are available.
			// Setting this to true will allow us to catch when targets are removed.
			apiValidation.klib.strictValidation = System.getenv("CI") == "true"

			apiValidation.nonPublicMarkers += listOf(
				"com.jakewharton.mosaic.animation.ExperimentalAnimationSpecApi",
				"com.jakewharton.mosaic.animation.ExperimentalTransitionApi",
				"com.jakewharton.mosaic.animation.InternalAnimationApi",
			)
		}
	}
}
