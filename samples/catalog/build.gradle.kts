import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("com.jakewharton.mosaic.targets")
}

kotlin {
	sourceSets {
		commonMain {
			dependencies {
				implementation(projects.mosaicRuntime)
				implementation(projects.mosaicAnimation)
			}
		}
	}

	jvm {
		binaries {
			executable {
				mainClass = "example.CatalogKt"
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
