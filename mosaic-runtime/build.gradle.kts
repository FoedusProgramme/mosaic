plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("org.unitedsoftware.mosaic.targets")
	id("org.unitedsoftware.mosaic.publish")
	alias(libs.plugins.poko)
	id("org.unitedsoftware.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("org.unitedsoftware.mosaic.runtime")
}

kotlin {
	explicitApi()

	sourceSets {
		configureEach {
			languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
		}

		commonMain {
			dependencies {
				api(libs.androidx.compose.runtime)
				api(libs.androidx.lifecycle.compose)
				api(libs.kotlinx.coroutines.core)
				api(projects.mosaicTerminal)
				implementation(projects.mosaicTtyTerminal)
				implementation(libs.androidx.collection)
				implementation(libs.codepoints)
			}
		}
		commonTest {
			dependencies {
				implementation(projects.mosaicTesting)
				implementation(libs.kotlin.test)
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.assertk)
			}
		}
	}

	compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
