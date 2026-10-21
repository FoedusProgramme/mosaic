plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("org.unitedsoftware.mosaic.targets")
	id("org.unitedsoftware.mosaic.publish")
	alias(libs.plugins.cite)
	alias(libs.plugins.poko)
	id("org.unitedsoftware.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("org.unitedsoftware.mosaic.terminal")
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
