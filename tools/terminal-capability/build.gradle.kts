@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
	alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
	sourceSets {
		commonMain {
			dependencies {
				implementation(projects.mosaicTtyTerminal)
				implementation(libs.clikt)
				implementation(libs.finalizationHook)
				implementation(libs.kotlin.reflect)
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
}
