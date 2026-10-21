plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.compose)
	id("com.jakewharton.mosaic.targets")
	id("com.jakewharton.mosaic.publish")
	alias(libs.plugins.poko)
	id("com.jakewharton.mosaic.build")
}

mosaicBuild {
	patchJavaModuleWithKotlinClasses("mosaic.animation")
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
