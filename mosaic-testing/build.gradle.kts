plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("com.jakewharton.mosaic.targets")
	id("com.jakewharton.mosaic.publish")
	alias(libs.plugins.poko)
	id("com.jakewharton.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("mosaic.testing")
}

kotlin {
	explicitApi()

	sourceSets {
		commonMain {
			dependencies {
				api(projects.mosaicRuntime)
				api(libs.kotlinx.coroutines.core)
				implementation(libs.androidx.collection)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.kotlinx.coroutines.test)
				implementation(libs.assertk)
			}
		}
	}

	compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
