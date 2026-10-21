import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.mavenPublish) apply false
	alias(libs.plugins.dokka)
	alias(libs.plugins.cite) apply false
	alias(libs.plugins.poko) apply false
	alias(libs.plugins.binaryCompatibilityValidator) apply false
	alias(libs.plugins.cklib) apply false
	alias(libs.plugins.jextract) apply false
	alias(libs.plugins.testDistribution) apply false
	alias(libs.plugins.spotless) apply false
	alias(libs.plugins.burst) apply false
	id("com.jakewharton.mosaic.build") apply false
	id("com.jakewharton.mosaic.publish") apply false
	id("com.jakewharton.mosaic.targets") apply false
}

dependencies {
	dokka(projects.mosaicAnimation)
	dokka(projects.mosaicRuntime)
	dokka(projects.mosaicTerminal)
	dokka(projects.mosaicTesting)
	dokka(projects.mosaicTty)
	dokka(projects.mosaicTtyTerminal)
}

allprojects {
	group = property("GROUP")!!
	version = property("VERSION_NAME")!!

	repositories {
		mavenCentral()
		google()
	}

	tasks.withType<JavaCompile>().configureEach {
		sourceCompatibility = JavaVersion.VERSION_11.toString()
		targetCompatibility = JavaVersion.VERSION_11.toString()
	}

	tasks.withType<KotlinJvmCompile>().configureEach {
		compilerOptions.jvmTarget = JvmTarget.JVM_11
		compilerOptions.jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
	}

	tasks.withType<KotlinNativeCompile>().configureEach {
		compilerOptions.freeCompilerArgs.add("-Xpartial-linkage-loglevel=ERROR")
	}

	tasks.withType<AbstractTestTask>().configureEach {
		testLogging {
			if (System.getenv("CI") == "true") {
				events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
				showStandardStreams = true
			}
			exceptionFormat = TestExceptionFormat.FULL
		}
	}

	apply(plugin = "com.diffplug.spotless")
	configure<SpotlessExtension> {
		kotlin {
			target("src/**/*.kt")
			ktlint(libs.ktlint.core.get().version)
				.editorConfigOverride(
					mapOf(
						"ktlint_standard_filename" to "disabled",
						// Making something an expression body should be a choice around readability.
						"ktlint_standard_function-expression-body" to "disabled",
						"ktlint_standard_property-naming" to "disabled",
						"ktlint_function_naming_ignore_when_annotated_with" to "Composable",
						"ktlint_compose_modifier-missing-check" to "disabled",
						"ktlint_compose_compositionlocal-allowlist" to "disabled",
						"compose_treat_as_lambda" to "MeasurePolicy",
					),
				)
				.customRuleSets(
					listOf(
						libs.ktlint.composeRules.get().toString(),
					),
				)
		}
	}
}
