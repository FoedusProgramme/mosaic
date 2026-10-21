plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("com.jakewharton.mosaic.targets")
	id("com.jakewharton.mosaic.publish")
	id("com.jakewharton.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("mosaic.tty.terminal")
}

kotlin {
	explicitApi()

	sourceSets {
		configureEach {
			languageSettings {
				optIn("kotlin.contracts.ExperimentalContracts")
				optIn("kotlinx.cinterop.ExperimentalForeignApi")
			}
			if (name.endsWith("Test")) {
				languageSettings {
					optIn("kotlin.ExperimentalStdlibApi")
				}
			}
		}

		commonMain {
			dependencies {
				api(libs.kotlinx.coroutines.core)
				api(projects.mosaicTerminal)
				api(projects.mosaicTty)
				implementation(libs.finalizationHook)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.kotlinx.io.core)
				implementation(libs.assertk)
			}
		}
	}
}
