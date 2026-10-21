plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("com.jakewharton.mosaic.targets")
	id("com.jakewharton.mosaic.publish")
	alias(libs.plugins.cite)
	alias(libs.plugins.poko)
	id("com.jakewharton.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("mosaic.terminal")
}

kotlin {
	explicitApi()

	sourceSets {
		commonMain {
			dependencies {
				api(libs.kotlinx.coroutines.core)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.assertk)
			}
		}
	}
}
