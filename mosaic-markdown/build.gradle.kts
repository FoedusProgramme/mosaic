plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("org.unitedsoftware.mosaic.targets")
	id("org.unitedsoftware.mosaic.publish")
	alias(libs.plugins.poko)
	id("org.unitedsoftware.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("org.unitedsoftware.mosaic.markdown")
}

kotlin {
	explicitApi()

	sourceSets {
		commonMain {
			dependencies {
				api(projects.mosaicRuntime)
			}
		}
		commonTest {
			dependencies {
				implementation(libs.kotlin.test)
				implementation(libs.assertk)
				implementation(projects.mosaicTesting)
				implementation(libs.kotlinx.coroutines.test)
			}
		}
	}
}
