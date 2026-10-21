import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("com.jakewharton.mosaic.targets")
}

kotlin {
	sourceSets {
		configureEach {
			languageSettings.optIn("kotlin.time.ExperimentalTime")
		}

		commonMain {
			dependencies {
				implementation(projects.mosaicRuntime)
				implementation(libs.kotlinx.datetime)
			}
		}
	}

	jvm {
		binaries {
			executable {
				mainClass = "example.RrtopKt"
			}
		}
	}

	targets.withType<KotlinNativeTarget>().configureEach {
		binaries.executable {
			entryPoint = "example.main"
			if (buildType == NativeBuildType.DEBUG) {
				linkTaskProvider.configure {
					enabled = false
				}
			}
		}
	}
}
