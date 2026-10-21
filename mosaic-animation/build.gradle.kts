@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("org.unitedsoftware.mosaic.targets")
	id("org.unitedsoftware.mosaic.publish")
	alias(libs.plugins.poko)
	id("org.unitedsoftware.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("org.unitedsoftware.mosaic.animation")
}

kotlin {
	applyDefaultHierarchyTemplate {
		common {
			group("native") {
				group("posix") {
					group("linux") {}
					group("macos") {}
				}
			}
		}
	}

	explicitApi()

	sourceSets {
		commonMain {
			dependencies {
				api(projects.mosaicRuntime)
				implementation(libs.androidx.collection)
				implementation(libs.androidx.annotation)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.assertk)
			}
		}
	}

	compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
