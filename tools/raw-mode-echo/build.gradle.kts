@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("com.jakewharton.mosaic.targets")
}

kotlin {
	sourceSets {
		commonMain {
			dependencies {
				implementation(projects.mosaicTtyTerminal)
				implementation(libs.clikt)
				implementation(libs.finalizationHook)
			}
		}
	}

	jvm {
		binaries {
			executable {
				mainClass = "example.Main"
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
