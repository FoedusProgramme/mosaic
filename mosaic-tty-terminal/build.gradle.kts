plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("org.unitedsoftware.mosaic.targets")
	id("org.unitedsoftware.mosaic.publish")
	id("org.unitedsoftware.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("org.unitedsoftware.mosaic.tty.terminal")
}

kotlin {
	explicitApi()

	sourceSets {
		configureEach {
			languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
			// The cinterop opt-in marker only exists for native source sets.
			if (!name.startsWith("common") && !name.startsWith("jvm")) {
				languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
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
